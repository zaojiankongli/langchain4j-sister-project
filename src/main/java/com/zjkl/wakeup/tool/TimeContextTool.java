package com.zjkl.wakeup.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 时间上下文工具
 *
 */
@Slf4j
@Component
public class TimeContextTool {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * 时间上下文
     */
    public record TimeContext(
            String currentTime,
            String timeOfDay,
            String specialMoment,
            String dayOfWeek,
            boolean isWeekend,
            String greeting
    ) {}

    /**
     * 获取当前时间上下文
     *
     * @return 时间上下文
     */
    public TimeContext getCurrentContext() {
        LocalDateTime now = LocalDateTime.now();
        LocalTime time = now.toLocalTime();
        LocalDate date = now.toLocalDate();

        String currentTime = time.format(TIME_FORMATTER);
        String timeOfDay = getTimeOfDay(time);
        String specialMoment = getSpecialMoment(time, date);
        String dayOfWeek = date.getDayOfWeek().getValue() + ""; // 1=周一, 7=周日
        java.time.DayOfWeek dow = date.getDayOfWeek();
        boolean isWeekend = dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY;
        String greeting = getGreeting(time);

        return new TimeContext(
                currentTime,
                timeOfDay,
                specialMoment,
                dayOfWeek,
                isWeekend,
                greeting
        );
    }

    /**
     * 获取时间段描述
     */
    public String getTimeOfDay(LocalTime time) {
        int hour = time.getHour();

        if (hour >= 5 && hour < 9) {
            return "清晨";
        } else if (hour >= 9 && hour < 12) {
            return "上午";
        } else if (hour >= 12 && hour < 14) {
            return "中午";
        } else if (hour >= 14 && hour < 18) {
            return "下午";
        } else if (hour >= 18 && hour < 22) {
            return "晚上";
        } else {
            return "深夜";
        }
    }

    /**
     * 获取特殊时间点
     */
    public String getSpecialMoment(LocalTime time, LocalDate date) {
        int hour = time.getHour();
        int minute = time.getMinute();

        // 饭点
        if ((hour == 7 && minute >= 30) || (hour == 8 && minute < 30)) {
            return "早餐时间";
        } else if ((hour == 12 && minute >= 0) || (hour == 13 && minute < 30)) {
            return "午餐时间";
        } else if ((hour == 18 && minute >= 30) || (hour == 19 && minute < 30)) {
            return "晚餐时间";
        }

        // 节假日（简单判断周末）
        java.time.DayOfWeek dayOfWeek = date.getDayOfWeek();
        if (dayOfWeek == java.time.DayOfWeek.SATURDAY || dayOfWeek == java.time.DayOfWeek.SUNDAY) {
            return "周末";
        }

        return "平常日";
    }

    /**
     * 获取问候语
     */
    public String getGreeting(LocalTime time) {
        int hour = time.getHour();

        if (hour >= 5 && hour < 9) {
            return "早上好";
        } else if (hour >= 9 && hour < 12) {
            return "上午好";
        } else if (hour >= 12 && hour < 14) {
            return "中午好";
        } else if (hour >= 14 && hour < 18) {
            return "下午好";
        } else if (hour >= 18 && hour < 22) {
            return "晚上好";
        } else {
            return "夜深了";
        }
    }
}
