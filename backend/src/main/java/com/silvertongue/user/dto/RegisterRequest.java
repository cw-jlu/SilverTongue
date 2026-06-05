package com.silvertongue.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "username is required")
    @Size(min = 3, max = 64, message = "username length must be between 3 and 64")
    private String username;

    @NotBlank(message = "password is required")
    @Size(min = 6, max = 72, message = "password length must be between 6 and 72")
    private String password;

    @Size(max = 64, message = "nickname length must be no more than 64")
    private String nickname;
}
