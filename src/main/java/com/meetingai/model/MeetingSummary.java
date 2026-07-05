package com.meetingai.model;

import lombok.Data;
import java.util.List;

@Data
public class MeetingSummary {
    private String summary;
    private List<String> discussion_points;
    private List<ActionItem> action_items;
    private List<String> risks;
    private List<String> decisions;
    private List<String> technical_tasks;
}
