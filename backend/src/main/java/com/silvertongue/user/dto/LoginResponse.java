package com.silvertongue.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private String tokenType;
    private Long expiresIn;
    private UserProfileResponse user;
}
