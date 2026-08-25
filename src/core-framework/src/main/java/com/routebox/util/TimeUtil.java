package com.routebox.util;

import java.time.*;
import java.time.format.DateTimeFormatter;

/**
 * @Description Time Tool
 * @Date 2024/10/24
**/
public class TimeUtil {

    private static final DateTimeFormatter DATE_TIME_DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter TIME_DTF = DateTimeFormatter.ofPattern("HH:mm:ss");


    /**
     * @Description Get current 10-digit timestamp
     * @Date 2025/4/17
     **/
    public static Long getNowTimestamp10() {
        return System.currentTimeMillis() / 1000;
    }

    /**
     * @Description Get current time string
     * @Date 2025/7/25
     **/
    public static String getNowTimeStr() {
        return LocalDateTime.now().format(DATE_TIME_DTF);
    }

    /**
     * @Description Transform 10-digit timestamp to datetime string
     * @Date 2025/7/25
     **/
    public static String timestamp10ToDateTimeStr(Long timestamp) {
        if (timestamp == null) return "Unknown Time";

        return Instant.ofEpochSecond(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .format(DATE_TIME_DTF);
    }

    /**
     * @Description Transform 10-digit timestamp to time string
     * @Date 2025/7/25
     **/
    public static String timestamp10ToTimeStr(Long timestamp) {
        if (timestamp == null) return "Unknown Time";

        return Instant.ofEpochSecond(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .format(TIME_DTF);
    }

    /**
     * @Description Transform datetime string to 10-digit timestamp
     * @Date 2025/7/25
     **/
    public static Long dateTimeStr2Timestamp10(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) return null;

        return LocalDateTime.parse(timeStr.trim(), DATE_TIME_DTF)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .getEpochSecond();
    }

    /**
     * @Description Transform FRR format datetime string to 10-digit timestamp
     * @Date 2025/6/5
     **/
    public static Long frrDate2Timestamp10(String date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd-HH:mm:ss");
        LocalDateTime dateTime = LocalDateTime.parse(date, formatter);

        return dateTime.toInstant(ZoneOffset.UTC).toEpochMilli() / 1000;
    }
}
