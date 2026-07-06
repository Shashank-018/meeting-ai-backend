package com.meetingai.provider;

import java.util.List;
import java.util.Map;

public interface VectorStore {
    void upsert(Long meetingId, List<Double> embedding, String summary, String transcript) throws Exception;
    List<Map<String, Object>> query(List<Double> queryEmbedding, int topN) throws Exception;
    void delete(Long meetingId);
}
