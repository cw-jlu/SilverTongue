package com.silvertongue.coach.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SessionCreateRequest {

    @NotBlank(message = "type must not be blank")
    private String type;   // shadowing, ai_chat

    @NotBlank(message = "mode must not be blank")
    private String mode;   // full_duplex, half_duplex, guided, free_talk

    private String topic;  // 角色/场景 (可选)
    private String contextFileUrl;
}
