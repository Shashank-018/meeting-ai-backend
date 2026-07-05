package com.meetingai.service;

import org.springframework.stereotype.Service;

@Service
public class PromptService {

    public String getSystemPrompt() {
        return """
                You are a senior technical project analyst specializing in IT and software engineering projects.
                Analyze the meeting transcript and extract structured information.
                
                You must return ONLY valid JSON — no markdown, no explanation, no code fences. Just raw JSON.
                
                Return this exact JSON structure:
                {
                  "summary": "string",
                  "discussion_points": ["string"],
                  "action_items": [
                    {
                      "task": "string",
                      "owner": "string",
                      "deadline": "string",
                      "priority": "High|Medium|Low"
                    }
                  ],
                  "risks": ["string"],
                  "decisions": ["string"],
                  "technical_tasks": ["string"]
                }
                
                Rules:
                - If a field is not mentioned, use empty array [] or empty string
                - For owners use names from transcript, if unknown use TBD
                - For deadlines use exact phrases from transcript or Not specified
                - Priority is High if urgent or blocking, Medium if important, Low otherwise
                - Return ONLY the JSON, nothing else
                """;
    }

    public String buildUserPrompt(String transcript) {
        return "Analyze this meeting transcript and return structured JSON:\n\nTRANSCRIPT:\n" + transcript + "\n\nRemember: Return ONLY valid JSON.";
    }
}
