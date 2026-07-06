package com.meetingai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.mode", havingValue = "local")
public class OllamaEmbeddingService implements EmbeddingProvider {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String model;

    public OllamaEmbeddingService(@Value("${ollama.base-url}") String baseUrl,
                                  @Value("${ollama.embed-model}") String model) {
        this.model = model;
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)).build();
        this.webClient = WebClient.builder().baseUrl(baseUrl).exchangeStrategies(strategies).build();
    }

    @Override
    public List<Double> embed(String text) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("prompt", text);

        String response = webClient.post().uri("/api/embeddings")
                .header("Content-Type", "application/json")
                .bodyValue(body).retrieve().bodyToMono(String.class).block();

        JsonNode embeddingNode = objectMapper.readTree(response).get("embedding");
        List<Double> embedding = new ArrayList<>();
        for (JsonNode val : embeddingNode) embedding.add(val.asDouble());
        return embedding;
    }
}
