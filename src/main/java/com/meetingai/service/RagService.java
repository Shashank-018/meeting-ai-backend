package com.meetingai.service;

import com.meetingai.provider.EmbeddingProvider;
import com.meetingai.provider.LlmService;
import com.meetingai.provider.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RagService {

    private final EmbeddingProvider embeddingProvider;
    private final VectorStore vectorStore;
    private final LlmService llmService;

    public RagService(EmbeddingProvider embeddingProvider,
                      VectorStore vectorStore,
                      LlmService llmService) {
        this.embeddingProvider = embeddingProvider;
        this.vectorStore = vectorStore;
        this.llmService = llmService;
    }

    // FEATURE 2 — semantic search
    public List<Map<String, Object>> semanticSearch(String query, int topN) throws Exception {
        List<Double> queryEmbedding = embeddingProvider.embed(query);
        return vectorStore.query(queryEmbedding, topN);
    }

    // FEATURE 3 — RAG chat
    public String chat(String question) throws Exception {
        List<Double> queryEmbedding = embeddingProvider.embed(question);
        List<Map<String, Object>> relevant = vectorStore.query(queryEmbedding, 3);

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

        return llmService.generate(prompt);
    }
}
