package com.meetingai.provider;

import com.meetingai.model.MeetingSummary;

public interface LlmService {
    MeetingSummary summarize(String transcript) throws Exception;
    String generate(String prompt) throws Exception;
}
