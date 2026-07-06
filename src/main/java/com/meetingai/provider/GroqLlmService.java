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

import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "app.mode", havingValue = "cloud")
public class GroqLlmService implements LlmService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PromptService promptService;
    private final String model;

    public GroqLlmService(PromptService promptService,
                          @Value("${groq.api-key}") String apiKey,
                          @Value("${groq.model}") String model) {
        this.promptService = promptService;
        this.model = model;
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)).build();
        this.webClient = WebClient.builder()
                .baseUrl("https://api.groq.com/openai/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .exchangeStrategies(strategies).build();
    }

    @Override
    public MeetingSummary summarize(String transcript) throws Exception {
        String content = callGroq(
                promptService.getSystemPrompt(),
                promptService.buildUserPrompt(transcript),
                true);
        content = content.trim();
        if (content.startsWith("```")) {
            content = content.replaceAll("```json", "").replaceAll("```", "").trim();
        }
        return objectMapper.readValue(content, MeetingSummary.class);
    }

    @Override
    public String generate(String prompt) throws Exception {
        return callGroq("You are a helpful assistant.", prompt, false);
    }

    private String callGroq(String system, String user, boolean jsonMode) throws Exception {
        var payload = new java.util.HashMap<String, Object>();
        payload.put("model", model);
        payload.put("temperature", 0.2);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)
        ));
        if (jsonMode) {
            payload.put("response_format", Map.of("type", "json_object"));
        }

        String response = webClient.post().uri("/chat/completions")
                .header("Content-Type", "application/json")
                .bodyValue(payload).retrieve().bodyToMono(String.class).block();

        JsonNode root = objectMapper.readTree(response);
        return root.get("choices").get(0).get("message").get("content").asText();
    }
}
