package com.meetingai.model;

import lombok.Data;

@Data
public class ActionItem {
    private String task;
    private String owner;
    private String deadline;
    private String priority;
}
