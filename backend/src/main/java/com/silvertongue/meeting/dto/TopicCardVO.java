package com.silvertongue.meeting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicCardVO {
    private Long id;
    private String type;       // TOPIC / VOCABULARY
    private String content;
    private String translation;
}
