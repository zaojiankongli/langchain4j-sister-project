package com.zjkl.wakeup.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WakeUpScoreResult {
    private int score;
    private String reason;
}
