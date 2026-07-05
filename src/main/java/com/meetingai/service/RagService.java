package com.meetingai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RagService {

    private final EmbeddingService embeddingService;
    private final ChromaService chromaService;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    private static final String MODEL = "llama3";

    public RagService(EmbeddingService embeddingService,
                      ChromaService chromaService) {
        this.embeddingService = embeddingService;
        this.chromaService    = chromaService;
        this.objectMapper     = new ObjectMapper();

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();

        this.webClient = WebClient.builder()
                .baseUrl("http://localhost:11434")
                .exchangeStrategies(strategies)
                .build();
    }

    // FEATURE 2 — semantic search: find similar meetings by meaning
    public List<Map<String, Object>> semanticSearch(String query, int topN) throws Exception {
        List<Double> queryEmbedding = embeddingService.embed(query);
        return chromaService.query(queryEmbedding, topN);
    }

    // FEATURE 3 — RAG chat: answer a question using past meetings as context
    public String chat(String question) throws Exception {

        // Step 1 — Retrieve: find 3 most relevant past meetings
        List<Double> queryEmbedding = embeddingService.embed(question);
        List<Map<String, Object>> relevant = chromaService.query(queryEmbedding, 3);

        // Step 2 — Augment: build a prompt with the retrieved context
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < relevant.size(); i++) {
            Map<String, Object> r = relevant.get(i);
            context.append("--- Meeting ").append(i + 1).append(" ---\n");
            context.append("Summary: ").append(r.get("summary")).append("\n");
            context.append("Transcript excerpt: ").append(r.get("document")).append("\n\n");
        }

        String prompt = """
                You are a helpful meeting assistant. Answer the user's question using ONLY the meeting context provided below.
                If the answer is not in the context, say "I couldn't find that in your past meetings."
                Be concise and specific. Reference which meeting your answer comes from.
                
                MEETING CONTEXT:
                """ + context + """
                
                USER QUESTION: """ + question + """
                
                ANSWER:""";

        // Step 3 — Generate: ask llama3 to answer using the context
        Map<String, Object> body = new HashMap<>();
        body.put("model", MODEL);
        body.put("prompt", prompt);
        body.put("stream", false);

        String response = webClient.post()
                .uri("/api/generate")
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode root = objectMapper.readTree(response);
        return root.get("response").asText().trim();
    }
}
