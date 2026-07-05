package com.meetingai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import jakarta.annotation.PostConstruct;

import java.util.*;

@Service
public class ChromaService {

    private final ObjectMapper objectMapper;
    private WebClient webClient;

    @Value("${chroma.base-url}")
    private String chromaBaseUrl;

    @Value("${chroma.collection-name}")
    private String collectionName;

    private String collectionId;

    public ChromaService() {
        this.objectMapper = new ObjectMapper();
    }

    // Runs once on startup — creates the ChromaDB collection if it doesn't exist
    @PostConstruct
    public void init() {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();

        this.webClient = WebClient.builder()
                .baseUrl(chromaBaseUrl)
                .exchangeStrategies(strategies)
                .build();

        try {
            collectionId = getOrCreateCollection();
            System.out.println("ChromaDB collection ready: " + collectionName + " (id=" + collectionId + ")");
        } catch (Exception e) {
            System.err.println("ChromaDB init failed: " + e.getMessage());
        }
    }

    // Get existing collection or create a new one
    private String getOrCreateCollection() throws Exception {
        // Try to get existing
        try {
            String res = webClient.get()
                    .uri("/api/v1/collections/" + collectionName)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            JsonNode node = objectMapper.readTree(res);
            return node.get("id").asText();
        } catch (Exception e) {
            // Create new collection
            Map<String, Object> body = new HashMap<>();
            body.put("name", collectionName);
            body.put("metadata", Map.of("description", "Meeting AI embeddings"));

            String res = webClient.post()
                    .uri("/api/v1/collections")
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode node = objectMapper.readTree(res);
            return node.get("id").asText();
        }
    }

    // Store a meeting embedding in ChromaDB
    public void upsert(Long meetingId, List<Double> embedding,
                       String summary, String transcript) throws Exception {

        Map<String, Object> body = new HashMap<>();
        body.put("ids", List.of(meetingId.toString()));
        body.put("embeddings", List.of(embedding));
        body.put("metadatas", List.of(Map.of(
                "meetingId", meetingId.toString(),
                "summary",   summary.length() > 500 ? summary.substring(0, 500) : summary
        )));
        body.put("documents", List.of(
                transcript.length() > 2000 ? transcript.substring(0, 2000) : transcript
        ));

        webClient.post()
                .uri("/api/v1/collections/" + collectionId + "/upsert")
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    // Find top N most similar meetings by vector similarity
    public List<Map<String, Object>> query(List<Double> queryEmbedding, int topN) throws Exception {

        Map<String, Object> body = new HashMap<>();
        body.put("query_embeddings", List.of(queryEmbedding));
        body.put("n_results", topN);
        body.put("include", List.of("metadatas", "documents", "distances"));

        String res = webClient.post()
                .uri("/api/v1/collections/" + collectionId + "/query")
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode root = objectMapper.readTree(res);
        List<Map<String, Object>> results = new ArrayList<>();

        JsonNode metadatas  = root.path("metadatas").get(0);
        JsonNode documents  = root.path("documents").get(0);
        JsonNode distances  = root.path("distances").get(0);

        for (int i = 0; i < metadatas.size(); i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("meetingId", metadatas.get(i).path("meetingId").asText());
            item.put("summary",   metadatas.get(i).path("summary").asText());
            item.put("document",  documents.get(i).asText());
            item.put("distance",  distances.get(i).asDouble());
            results.add(item);
        }
        return results;
    }

    // Delete a meeting from ChromaDB when deleted from SQLite
    public void delete(Long meetingId) {
        try {
            Map<String, Object> body = Map.of("ids", List.of(meetingId.toString()));
            webClient.post()
                    .uri("/api/v1/collections/" + collectionId + "/delete")
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
        } catch (Exception e) {
            System.err.println("ChromaDB delete failed: " + e.getMessage());
        }
    }
}
