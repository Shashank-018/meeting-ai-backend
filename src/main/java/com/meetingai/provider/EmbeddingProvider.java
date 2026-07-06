package com.meetingai.provider;

import java.util.List;

public interface EmbeddingProvider {
    List<Double> embed(String text) throws Exception;
}
