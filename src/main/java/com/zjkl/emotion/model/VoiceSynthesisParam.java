package com.zjkl.emotion.model;

import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisAudioFormat;
import com.alibaba.dashscope.audio.ttsv2.SpeechSynthesisParam;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class VoiceSynthesisParam {

    private String model = "cosyvoice-v3.5-flash";

    private String voice = "cosyvoice-v3.5-flash-bailian-44f6c28eb15c4f80a5631c7e1abc21a9";

    private SpeechSynthesisAudioFormat format =
            SpeechSynthesisAudioFormat.PCM_48000HZ_MONO_16BIT;

    private Integer volume = 50;

    private Float speechRate = 1.0f;

    private Float pitchRate = 1.0f;

    private String instruction;

    private Boolean enableWordTimestamp = false;

    private Integer seed = 0;

    public static VoiceSynthesisParam defaults() {
        return new VoiceSynthesisParam();
    }

    public SpeechSynthesisParam toDashScopeParam(String apiKey) {
        Map<String, Object> parameters = new HashMap<>();
        if (instruction != null && !instruction.isEmpty()) {
            parameters.put("instruction", instruction);
        }
        if (enableWordTimestamp != null) {
            parameters.put("enableWordTimestamp", enableWordTimestamp);
        }
        if (seed != null && seed != 0) {
            parameters.put("seed", seed);
        }

        return SpeechSynthesisParam.builder()
                .apiKey(apiKey)
                .model(this.model)
                .voice(this.voice)
                .format(this.format)
                .volume(this.volume)
                .speechRate(this.speechRate)
                .pitchRate(this.pitchRate)
                .parameters(parameters)
                .build();
    }
}
