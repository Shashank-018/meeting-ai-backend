package com.meetingai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingai.model.MeetingRecord;
import com.meetingai.model.MeetingSummary;
import com.meetingai.model.TranscriptRequest;
import com.meetingai.service.ChromaService;
import com.meetingai.service.EmbeddingService;
import com.meetingai.service.MeetingHistoryService;
import com.meetingai.service.OllamaService;
import com.meetingai.service.RagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class SummarizeController {

    private final OllamaService         ollamaService;
    private final MeetingHistoryService  historyService;
    private final EmbeddingService       embeddingService;
    private final ChromaService          chromaService;
    private final RagService             ragService;
    private final ObjectMapper           objectMapper;

    public SummarizeController(OllamaService ollamaService,
                               MeetingHistoryService historyService,
                               EmbeddingService embeddingService,
                               ChromaService chromaService,
                               RagService ragService) {
        this.ollamaService    = ollamaService;
        this.historyService   = historyService;
        this.embeddingService = embeddingService;
        this.chromaService    = chromaService;
        this.ragService       = ragService;
        this.objectMapper     = new ObjectMapper();
    }

    @GetMapping("/")
    public ResponseEntity<Map<String, String>> root() {
        return ResponseEntity.ok(Map.of("status", "Meeting AI backend running — Phase 2 RAG enabled"));
    }

    // ── Summarize + auto-save + auto-embed ───────────────────────────────
    @PostMapping("/summarize")
    public ResponseEntity<?> summarize(@RequestBody TranscriptRequest request) {
        if (request.getTranscript() == null || request.getTranscript().trim().length() < 50) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Transcript too short."));
        }
        try {
            // 1. Summarize with llama3
            MeetingSummary summary = ollamaService.summarize(request.getTranscript());

            // 2. Save to SQLite
            MeetingRecord saved = historyService.save(request.getTranscript(), summary);

            // 3. Embed and store in ChromaDB (RAG pipeline)
            List<Double> embedding = embeddingService.embed(
                    summary.getSummary() + " " + request.getTranscript()
            );
            chromaService.upsert(saved.getId(), embedding,
                    summary.getSummary(), request.getTranscript());

            System.out.println("Meeting " + saved.getId() + " saved to SQLite + ChromaDB");

            return ResponseEntity.ok(Map.of(
                    "id",               saved.getId(),
                    "summary",          summary.getSummary(),
                    "discussion_points",summary.getDiscussion_points(),
                    "action_items",     summary.getAction_items(),
                    "risks",            summary.getRisks(),
                    "decisions",        summary.getDecisions(),
                    "technical_tasks",  summary.getTechnical_tasks()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("detail", e.getMessage()));
        }
    }

    // ── History endpoints ────────────────────────────────────────────────
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
                    "id",          record.getId(),
                    "createdAt",   record.getCreatedAt().toString(),
                    "transcript",  record.getTranscript(),
                    "summary",     summary
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("detail", e.getMessage()));
        }
    }

    @DeleteMapping("/history/{id}")
    public ResponseEntity<?> deleteMeeting(@PathVariable Long id) {
        try {
            chromaService.delete(id);        // remove from ChromaDB too
            historyService.delete(id);       // remove from SQLite
            return ResponseEntity.ok(Map.of("deleted", id));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("detail", e.getMessage()));
        }
    }

    // ── FEATURE 2: Semantic search ───────────────────────────────────────
    @GetMapping("/search")
    public ResponseEntity<?> search(@RequestParam String q,
                                    @RequestParam(defaultValue = "3") int top) {
        if (q == null || q.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Query cannot be empty."));
        }
        try {
            List<Map<String, Object>> results = ragService.semanticSearch(q, top);
            return ResponseEntity.ok(Map.of("query", q, "results", results));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("detail", e.getMessage()));
        }
    }

    // ── FEATURE 3: RAG chat ──────────────────────────────────────────────
    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, String> body) {
        String question = body.get("question");
        if (question == null || question.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Question cannot be empty."));
        }
        try {
            String answer = ragService.chat(question);
            return ResponseEntity.ok(Map.of("question", question, "answer", answer));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("detail", e.getMessage()));
        }
    }
}