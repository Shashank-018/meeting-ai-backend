package com.meetingai.repository;

import com.meetingai.model.MeetingRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MeetingRepository extends JpaRepository<MeetingRecord, Long> {

    // Returns all meetings, newest first
    List<MeetingRecord> findAllByOrderByCreatedAtDesc();
}
