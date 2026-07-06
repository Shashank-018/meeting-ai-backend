package com.meetingai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingai.model.MeetingSummary;
import com.meetingai.service.PromptService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.mode", havingValue = "local")
public class OllamaLlmService implements LlmService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PromptService promptService;

    @Value("${ollama.chat-model}")
    private String model;

    public OllamaLlmService(PromptService promptService,
                            @Value("${ollama.base-url}") String baseUrl) {
        this.promptService = promptService;
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)).build();
        this.webClient = WebClient.builder().baseUrl(baseUrl).exchangeStrategies(strategies).build();
    }

    @Override
    public MeetingSummary summarize(String transcript) throws Exception {
        String fullPrompt = promptService.getSystemPrompt() + "\n\n" + promptService.buildUserPrompt(transcript);
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("prompt", fullPrompt);
        body.put("stream", false);
        body.put("format", "json");

        String response = webClient.post().uri("/api/generate")
                .header("Content-Type", "application/json")
                .bodyValue(body).retrieve().bodyToMono(String.class).block();

        JsonNode root = objectMapper.readTree(response);
        String jsonContent = root.get("response").asText().trim();
        if (jsonContent.startsWith("```")) {
            jsonContent = jsonContent.replaceAll("```json", "").replaceAll("```", "").trim();
        }
        return objectMapper.readValue(jsonContent, MeetingSummary.class);
    }

    @Override
    public String generate(String prompt) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("prompt", prompt);
        body.put("stream", false);

        String response = webClient.post().uri("/api/generate")
                .header("Content-Type", "application/json")
                .bodyValue(body).retrieve().bodyToMono(String.class).block();

        return objectMapper.readTree(response).get("response").asText().trim();
    }
}
