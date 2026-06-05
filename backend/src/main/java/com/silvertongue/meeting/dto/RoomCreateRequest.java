package com.silvertongue.meeting.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RoomCreateRequest {

    @NotBlank(message = "roomName must not be blank")
    private String roomName;

    @Min(2)
    private int maxUsers = 10;
}
