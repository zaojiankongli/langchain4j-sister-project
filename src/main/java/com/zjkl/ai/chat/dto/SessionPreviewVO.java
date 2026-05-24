package com.zjkl.ai.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SessionPreviewVO {
    @JsonProperty("date")
    private String date;

    @JsonProperty("preview")
    private String preview;
}
