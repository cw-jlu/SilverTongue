package com.silvertongue.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FriendRemarkRequest {

    @NotNull(message = "friendId must not be null")
    private Long friendId;

    @Size(max = 64, message = "remark must be at most 64 characters")
    private String remark;
}
