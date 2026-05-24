package com.zjkl.emotion.model.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class EvolutionEventVO {
    @JsonProperty("trigger")
    private String trigger;

    @JsonProperty("result")
    private String result;

    @JsonProperty("time")
    private String time;
}
