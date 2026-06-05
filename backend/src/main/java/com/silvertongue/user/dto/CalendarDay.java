package com.silvertongue.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarDay {

    /** 日期 (1-31) */
    private int day;

    /** 是否已签到 */
    private boolean signed;
}
