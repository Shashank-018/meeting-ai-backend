package com.meetingai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Service
@ConditionalOnProperty(name = "app.mode", havingValue = "cloud")
public class QdrantVectorStore implements VectorStore {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebClient webClient;

    private final String url;
    private final String apiKey;
    private final String collection;

    // Jina v3 embeddings are 1024-dim
    private static final int VECTOR_SIZE = 1024;

    public QdrantVectorStore(@Value("${qdrant.url}") String url,
                             @Value("${qdrant.api-key}") String apiKey,
                             @Value("${qdrant.collection}") String collection) {
        this.url = url;
        this.apiKey = apiKey;
        this.collection = collection;
    }

    @PostConstruct
    public void init() {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)).build();
        this.webClient = WebClient.builder()
                .baseUrl(url)
                .defaultHeader("api-key", apiKey)
                .exchangeStrategies(strategies).build();
        try {
            createCollectionIfNeeded();
            System.out.println("Qdrant ready: collection " + collection);
        } catch (Exception e) {
            System.err.println("Qdrant init failed: " + e.getMessage());
        }
    }

    private void createCollectionIfNeeded() {
        try {
            // Check if collection exists
            webClient.get().uri("/collections/" + collection)
                    .retrieve().bodyToMono(String.class).block();
        } catch (Exception notFound) {
            // Create it
            Map<String, Object> body = Map.of(
                    "vectors", Map.of("size", VECTOR_SIZE, "distance", "Cosine")
            );
            webClient.put().uri("/collections/" + collection)
                    .header("Content-Type", "application/json")
                    .bodyValue(body).retrieve().bodyToMono(String.class).block();
        }
    }

    @Override
    public void upsert(Long meetingId, List<Double> embedding, String summary, String transcript) throws Exception {
        Map<String, Object> point = Map.of(
                "id", meetingId,
                "vector", embedding,
                "payload", Map.of(
                        "meetingId", meetingId.toString(),
                        "summary", summary.length() > 500 ? summary.substring(0, 500) : summary,
                        "document", transcript.length() > 2000 ? transcript.substring(0, 2000) : transcript
                )
        );
        Map<String, Object> body = Map.of("points", List.of(point));

        webClient.put().uri("/collections/" + collection + "/points")
                .header("Content-Type", "application/json")
                .bodyValue(body).retrieve().bodyToMono(String.class).block();
    }

    @Override
    public List<Map<String, Object>> query(List<Double> queryEmbedding, int topN) throws Exception {
        Map<String, Object> body = Map.of(
                "vector", queryEmbedding,
                "limit", topN,
                "with_payload", true
        );

        String res = webClient.post().uri("/collections/" + collection + "/points/search")
                .header("Content-Type", "application/json")
                .bodyValue(body).retrieve().bodyToMono(String.class).block();

        JsonNode result = objectMapper.readTree(res).get("result");
        List<Map<String, Object>> results = new ArrayList<>();
        for (JsonNode point : result) {
            Map<String, Object> item = new HashMap<>();
            JsonNode payload = point.get("payload");
            item.put("meetingId", payload.path("meetingId").asText());
            item.put("summary", payload.path("summary").asText());
            item.put("document", payload.path("document").asText());
            // Qdrant score is similarity (0-1, higher better). Convert to distance for frontend consistency.
            double score = point.get("score").asDouble();
            item.put("distance", 1.0 - score);
            results.add(item);
        }
        return results;
    }

    @Override
    public void delete(Long meetingId) {
        try {
            Map<String, Object> body = Map.of("points", List.of(meetingId));
            webClient.post().uri("/collections/" + collection + "/points/delete")
                    .header("Content-Type", "application/json")
                    .bodyValue(body).retrieve().bodyToMono(String.class).block();
        } catch (Exception e) {
            System.err.println("Qdrant delete failed: " + e.getMessage());
        }
    }
}
