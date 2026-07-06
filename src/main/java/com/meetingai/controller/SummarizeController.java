package com.meetingai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingai.model.MeetingRecord;
import com.meetingai.model.MeetingSummary;
import com.meetingai.model.TranscriptRequest;
import com.meetingai.provider.EmbeddingProvider;
import com.meetingai.provider.LlmService;
import com.meetingai.provider.VectorStore;
import com.meetingai.service.MeetingHistoryService;
import com.meetingai.service.RagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class SummarizeController {

    private final LlmService          llmService;
    private final MeetingHistoryService historyService;
    private final EmbeddingProvider    embeddingProvider;
    private final VectorStore          vectorStore;
    private final RagService           ragService;
    private final ObjectMapper         objectMapper = new ObjectMapper();

    public SummarizeController(LlmService llmService,
                                MeetingHistoryService historyService,
                                EmbeddingProvider embeddingProvider,
                                VectorStore vectorStore,
                                RagService ragService) {
        this.llmService       = llmService;
        this.historyService   = historyService;
        this.embeddingProvider = embeddingProvider;
        this.vectorStore      = vectorStore;
        this.ragService       = ragService;
    }

    @GetMapping("/")
    public ResponseEntity<Map<String, String>> root() {
        return ResponseEntity.ok(Map.of("status", "Meeting AI backend running"));
    }

    @PostMapping("/summarize")
    public ResponseEntity<?> summarize(@RequestBody TranscriptRequest request) {
        if (request.getTranscript() == null || request.getTranscript().trim().length() < 50) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Transcript too short."));
        }
        try {
            MeetingSummary summary = llmService.summarize(request.getTranscript());
            MeetingRecord saved = historyService.save(request.getTranscript(), summary);
            List<Double> embedding = embeddingProvider.embed(
                    summary.getSummary() + " " + request.getTranscript());
            vectorStore.upsert(saved.getId(), embedding, summary.getSummary(), request.getTranscript());

            return ResponseEntity.ok(Map.of(
                    "id", saved.getId(),
                    "summary", summary.getSummary(),
                    "discussion_points", summary.getDiscussion_points(),
                    "action_items", summary.getAction_items(),
                    "risks", summary.getRisks(),
                    "decisions", summary.getDecisions(),
                    "technical_tasks", summary.getTechnical_tasks()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("detail", e.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<MeetingRecord>> getHistory() {
        return ResponseEntity.ok(historyService.getAll());
    }

    @GetMapping("/history/{id}")
    public ResponseEntity<?> getMeeting(@PathVariable Long id) {
        try {
            MeetingRecord record = historyService.getById(id);
            MeetingSummary summary = objectMapper.readValue(record.getFullJson(), MeetingSummary.class);
            return ResponseEntity.ok(Map.of(
                    "id", record.getId(),
                    "createdAt", record.getCreatedAt().toString(),
                    "transcript", record.getTranscript(),
                    "summary", summary
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("detail", e.getMessage()));
        }
    }

    @DeleteMapping("/history/{id}")
    public ResponseEntity<?> deleteMeeting(@PathVariable Long id) {
        try {
            vectorStore.delete(id);
            historyService.delete(id);
            return ResponseEntity.ok(Map.of("deleted", id));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("detail", e.getMessage()));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String q, @RequestParam(defaultValue = "3") int top) {
        if (q == null || q.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Query cannot be empty."));
        }
        try {
            return ResponseEntity.ok(Map.of("query", q, "results", ragService.semanticSearch(q, top)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("detail", e.getMessage()));
        }
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, String> body) {
        String question = body.get("question");
        if (question == null || question.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Question cannot be empty."));
        }
        try {
            return ResponseEntity.ok(Map.of("question", question, "answer", ragService.chat(question)));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("detail", e.getMessage()));
        }
    }
}
