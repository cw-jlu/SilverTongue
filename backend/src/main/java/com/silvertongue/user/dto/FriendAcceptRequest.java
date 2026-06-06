package com.silvertongue.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FriendAcceptRequest {

    @NotNull(message = "applyId must not be null")
    private Long applyId;
}
