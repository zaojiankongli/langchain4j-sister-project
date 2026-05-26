package com.zjkl.emotion.service;

import com.zjkl.emotion.model.EmotionalState;

/**
 * 心情描述生成工具类
 */
public class MoodDescriptionGenerator {

    public static String generateMoodDescription(double p, double a, double d) {
        StringBuilder mood = new StringBuilder();

        // 害羞
        if (d < -0.5) {
            mood.append("羞涩得不敢抬头，脸颊发烫，手指紧张地绞着衣角，声音轻得像蚊子哼哼");
        } else if (d < -0.3) {
            mood.append("有些害羞，微微低着头，偶尔偷看你一眼又迅速移开视线");
        }
        // 开心
        else if (p > 0.5) {
            mood.append("心里甜甜的，眼睛亮晶晶的，嘴角忍不住微微上扬");
        } else if (p > 0.2) {
            mood.append("心情不错，嘴角带着淡淡的笑意，眼神很温柔");
        }
        // 难过
        else if (p < -0.4) {
            mood.append("心里酸酸的，眼眶有些发热，声音也变得哽咽");
        } else if (p < -0.15) {
            mood.append("心情有些低落，低着头不说话，手指无意识地摆弄着衣角");
        }
        // 紧张
        else if (a > 0.5) {
            mood.append("心跳得好快，手心都在出汗，说话都有些结巴了");
        } else if (a > 0.2) {
            mood.append("有点紧张，手指轻轻按在胸口，试图让心跳平复下来");
        }
        // 平静
        else if (a < -0.5) {
            mood.append("整个人很放松，像躺在云朵上一样，说话声音轻柔得像呢喃");
        } else if (a < -0.2) {
            mood.append("感觉很安心，身体放松地靠在那里，眼神柔和");
        }
        else {
            mood.append("安静地待在那里，眼神温和，带着淡淡的微笑");
        }

        if (p > 0.3 && a > 0.3 && d < -0.2) {
            mood.append("...心里像有小鹿乱撞，既开心又害羞，捂住发烫的脸从指缝里偷看你");
        }
        else if (d < -0.4 && p > 0.1) {
            mood.append("...乖巧地听你说话，眼神里满是信任和依赖");
        }
        else if (p < -0.2 && d < -0.2) {
            mood.append("...咬着嘴唇不说话，努力不让眼泪掉下来");
        }

        return mood.toString();
    }

    public static String generateMoodLabel(double p, double a, double d) {
        if (d < -0.5) return "极度害羞";
        if (d < -0.3) return "有些害羞";
        if (p > 0.5) return "非常开心";
        if (p > 0.2) return "心情不错";
        if (p < -0.4) return "很难过";
        if (p < -0.15) return "有点失落";
        if (a > 0.5) return "非常紧张";
        if (a > 0.2) return "有些紧张";
        if (a < -0.5) return "非常平静";
        if (a < -0.2) return "比较放松";
        return "平静";
    }

    public static String generateMoodDescription(EmotionalState emotion) {
        return generateMoodDescription(emotion.getPleasure(), emotion.getArousal(), emotion.getDominance());
    }

    public static String generateMoodLabel(EmotionalState emotion) {
        return generateMoodLabel(emotion.getPleasure(), emotion.getArousal(), emotion.getDominance());
    }
}
