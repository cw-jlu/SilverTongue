package com.silvertongue.coach.dto;

import lombok.Data;

@Data
public class MessageRequest {
    private String content;
    private String type; // text, audio
    private String audioBase64;
}
