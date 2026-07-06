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
@ConditionalOnProperty(name = "app.mode", havingValue = "local")
public class ChromaVectorStore implements VectorStore {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebClient webClient;
    private String collectionId;

    private final String baseUrl;
    private final String collectionName;

    public ChromaVectorStore(@Value("${chroma.base-url}") String baseUrl,
                             @Value("${chroma.collection-name}") String collectionName) {
        this.baseUrl = baseUrl;
        this.collectionName = collectionName;
    }

    @PostConstruct
    public void init() {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)).build();
        this.webClient = WebClient.builder().baseUrl(baseUrl).exchangeStrategies(strategies).build();
        try {
            collectionId = getOrCreateCollection();
            System.out.println("ChromaDB ready: " + collectionName + " (id=" + collectionId + ")");
        } catch (Exception e) {
            System.err.println("ChromaDB init failed: " + e.getMessage());
        }
    }

    private String getOrCreateCollection() throws Exception {
        try {
            String res = webClient.get().uri("/api/v1/collections/" + collectionName)
                    .retrieve().bodyToMono(String.class).block();
            return objectMapper.readTree(res).get("id").asText();
        } catch (Exception e) {
            Map<String, Object> body = new HashMap<>();
            body.put("name", collectionName);
            body.put("metadata", Map.of("description", "Meeting AI embeddings"));
            String res = webClient.post().uri("/api/v1/collections")
                    .header("Content-Type", "application/json")
                    .bodyValue(body).retrieve().bodyToMono(String.class).block();
            return objectMapper.readTree(res).get("id").asText();
        }
    }

    @Override
    public void upsert(Long meetingId, List<Double> embedding, String summary, String transcript) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("ids", List.of(meetingId.toString()));
        body.put("embeddings", List.of(embedding));
        body.put("metadatas", List.of(Map.of(
                "meetingId", meetingId.toString(),
                "summary", summary.length() > 500 ? summary.substring(0, 500) : summary
        )));
        body.put("documents", List.of(
                transcript.length() > 2000 ? transcript.substring(0, 2000) : transcript
        ));
        webClient.post().uri("/api/v1/collections/" + collectionId + "/upsert")
                .header("Content-Type", "application/json")
                .bodyValue(body).retrieve().bodyToMono(String.class).block();
    }

    @Override
    public List<Map<String, Object>> query(List<Double> queryEmbedding, int topN) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("query_embeddings", List.of(queryEmbedding));
        body.put("n_results", topN);
        body.put("include", List.of("metadatas", "documents", "distances"));

        String res = webClient.post().uri("/api/v1/collections/" + collectionId + "/query")
                .header("Content-Type", "application/json")
                .bodyValue(body).retrieve().bodyToMono(String.class).block();

        JsonNode root = objectMapper.readTree(res);
        List<Map<String, Object>> results = new ArrayList<>();
        JsonNode metadatas = root.path("metadatas").get(0);
        JsonNode documents = root.path("documents").get(0);
        JsonNode distances = root.path("distances").get(0);

        for (int i = 0; i < metadatas.size(); i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("meetingId", metadatas.get(i).path("meetingId").asText());
            item.put("summary", metadatas.get(i).path("summary").asText());
            item.put("document", documents.get(i).asText());
            item.put("distance", distances.get(i).asDouble());
            results.add(item);
        }
        return results;
    }

    @Override
    public void delete(Long meetingId) {
        try {
            Map<String, Object> body = Map.of("ids", List.of(meetingId.toString()));
            webClient.post().uri("/api/v1/collections/" + collectionId + "/delete")
                    .header("Content-Type", "application/json")
                    .bodyValue(body).retrieve().bodyToMono(String.class).block();
        } catch (Exception e) {
            System.err.println("ChromaDB delete failed: " + e.getMessage());
        }
    }
}
