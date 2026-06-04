package com.zjkl.ai.chat.stomp;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Converts backend chat/emotion semantics into desktop pet events carried by the existing STOMP push chain.
 */
@Service
@RequiredArgsConstructor
public class SemanticPetEventAdapter {

    private static final double DEFAULT_EXPRESSION_INTENSITY = 0.8;
    private static final long DEFAULT_EXPRESSION_DURATION_MS = 3000L;
    private static final PetPriority DEFAULT_PRIORITY = PetPriority.NORMAL;

    private final ChatPushService chatPushService;

    public void pushChatPhase(String userId, ChatPhase phase) {
        chatPushService.pushPetMotion(userId, phase.motion.semanticName(), DEFAULT_PRIORITY.semanticName());
    }

    public void pushMoodExpression(String userId, String moodLabel) {
        PetExpression expression = PetExpression.fromMoodLabel(moodLabel);
        chatPushService.pushPetExpression(userId, expression.semanticName(), DEFAULT_EXPRESSION_INTENSITY, DEFAULT_EXPRESSION_DURATION_MS);
    }

    public enum ChatPhase {
        THINKING(PetMotion.THINKING),
        SPEAKING(PetMotion.SPEAKING);

        private final PetMotion motion;

        ChatPhase(PetMotion motion) {
            this.motion = motion;
        }
    }

    enum PetMotion {
        THINKING("thinking"),
        SPEAKING("speaking");

        private final String semanticName;

        PetMotion(String semanticName) {
            this.semanticName = semanticName;
        }

        String semanticName() {
            return semanticName;
        }
    }

    enum PetPriority {
        NORMAL("normal");

        private final String semanticName;

        PetPriority(String semanticName) {
            this.semanticName = semanticName;
        }

        String semanticName() {
            return semanticName;
        }
    }

    enum PetExpression {
        HAPPY("happy"),
        SAD("sad"),
        THINKING("thinking"),
        SURPRISED("surprised"),
        ANNOYED("annoyed"),
        NEUTRAL("neutral");

        private final String semanticName;

        PetExpression(String semanticName) {
            this.semanticName = semanticName;
        }

        String semanticName() {
            return semanticName;
        }

        static PetExpression fromMoodLabel(String moodLabel) {
            if (moodLabel == null || moodLabel.isBlank()) {
                return NEUTRAL;
            }

            String normalized = moodLabel.toLowerCase(Locale.ROOT);
            if (containsAny(normalized, "怒", "烦", "恼", "annoy", "angry", "irritated")) {
                return ANNOYED;
            }
            if (containsAny(normalized, "惊", "surpris", "startled")) {
                return SURPRISED;
            }
            if (containsAny(normalized, "想", "思", "thinking", "curious")) {
                return THINKING;
            }
            if (containsAny(normalized, "伤", "sad", "难过", "低落")) {
                return SAD;
            }
            if (containsAny(normalized, "开", "喜", "happy", "joy", "愉悦")) {
                return HAPPY;
            }
            return NEUTRAL;
        }

        private static boolean containsAny(String text, String... keywords) {
            for (String keyword : keywords) {
                if (text.contains(keyword)) {
                    return true;
                }
            }
            return false;
        }
    }
}
