package com.meetingai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingai.model.MeetingSummary;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ExchangeStrategies;

import java.util.HashMap;
import java.util.Map;

@Service
public class OllamaService {

    private final WebClient webClient;
    private final PromptService promptService;
    private final ObjectMapper objectMapper;

    private static final String OLLAMA_URL = "http://localhost:11434";
    private static final String MODEL = "llama3";

    public OllamaService(PromptService promptService) {
        this.promptService = promptService;
        this.objectMapper = new ObjectMapper();

        // Allow large responses
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(config -> config.defaultCodecs()
                        .maxInMemorySize(10 * 1024 * 1024))
                .build();

        this.webClient = WebClient.builder()
                .baseUrl(OLLAMA_URL)
                .exchangeStrategies(strategies)
                .build();
    }

    public MeetingSummary summarize(String transcript) throws Exception {

        // Build the full prompt
        String fullPrompt = promptService.getSystemPrompt()
                + "\n\n"
                + promptService.buildUserPrompt(transcript);

        // Build request body for Ollama
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", MODEL);
        requestBody.put("prompt", fullPrompt);
        requestBody.put("stream", false);
        requestBody.put("format", "json");

        // Call Ollama API
        String response = webClient.post()
                .uri("/api/generate")
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        // Parse the response
        JsonNode root = objectMapper.readTree(response);
        String jsonContent = root.get("response").asText();

        // Clean up if model wraps in markdown
        jsonContent = jsonContent.trim();
        if (jsonContent.startsWith("```")) {
            jsonContent = jsonContent.replaceAll("```json", "").replaceAll("```", "").trim();
        }

        return objectMapper.readValue(jsonContent, MeetingSummary.class);
    }
}
