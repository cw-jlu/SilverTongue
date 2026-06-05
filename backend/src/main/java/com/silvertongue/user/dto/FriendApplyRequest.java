package com.silvertongue.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FriendApplyRequest {

    @NotNull(message = "friendId must not be null")
    private Long friendId;

    private String message;
}
