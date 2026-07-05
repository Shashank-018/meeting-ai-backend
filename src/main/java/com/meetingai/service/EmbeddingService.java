package com.meetingai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EmbeddingService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${ollama.embed-model}")
    private String embedModel;

    public EmbeddingService() {
        this.objectMapper = new ObjectMapper();

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();

        this.webClient = WebClient.builder()
                .baseUrl("http://localhost:11434")
                .exchangeStrategies(strategies)
                .build();
    }

    // Convert any text to a list of floats (the embedding vector)
    public List<Double> embed(String text) throws Exception {

        Map<String, Object> body = new HashMap<>();
        body.put("model", embedModel);
        body.put("prompt", text);

        String response = webClient.post()
                .uri("/api/embeddings")
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode root = objectMapper.readTree(response);
        JsonNode embeddingNode = root.get("embedding");

        // Convert JsonNode array to List<Double>
        List<Double> embedding = new java.util.ArrayList<>();
        for (JsonNode val : embeddingNode) {
            embedding.add(val.asDouble());
        }
        return embedding;
    }
}
