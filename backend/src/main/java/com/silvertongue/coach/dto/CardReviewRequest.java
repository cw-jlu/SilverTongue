package com.silvertongue.coach.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CardReviewRequest {

    @NotNull(message = "cardId must not be null")
    private Long cardId;

    @Min(0)
    @Max(5)
    private int quality;   // 0-5: 0=全忘, 5=非常熟悉
}
