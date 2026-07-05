package com.meetingai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetingai.model.MeetingRecord;
import com.meetingai.model.MeetingSummary;
import com.meetingai.repository.MeetingRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class MeetingHistoryService {

    private final MeetingRepository repository;
    private final ObjectMapper objectMapper;

    public MeetingHistoryService(MeetingRepository repository) {
        this.repository = repository;
        this.objectMapper = new ObjectMapper();
    }

    // Save a meeting after AI summarizes it
    public MeetingRecord save(String transcript, MeetingSummary summary) {
        try {
            MeetingRecord record = new MeetingRecord();
            record.setTranscript(transcript);
            record.setSummary(summary.getSummary());
            record.setFullJson(objectMapper.writeValueAsString(summary));
            return repository.save(record);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save meeting: " + e.getMessage());
        }
    }

    // Get all past meetings
    public List<MeetingRecord> getAll() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    // Get one meeting by ID
    public MeetingRecord getById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Meeting not found with id: " + id));
    }

    // Delete a meeting
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
