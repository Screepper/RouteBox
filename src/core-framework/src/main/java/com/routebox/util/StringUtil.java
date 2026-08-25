package com.routebox.util;

import java.util.Random;

/**
 * @Description String Tool
 * @Date 2025/5/8
**/
public class StringUtil {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final int DEFAULT_ID_LENGTH = 16;
    private static final int DEFAULT_TIMESTAMP_LENGTH = 8;
    private static final Random random = new Random();

    /**
     * @Description Generate a random ID based on a hexadecimal timestamp and a random string
     * @Date 2025/5/9
     **/
    public static String generateId(int... lens) {
        int len = DEFAULT_ID_LENGTH;

        if (lens.length > 0 && lens[0] > DEFAULT_TIMESTAMP_LENGTH) {
            len = lens[0];
        }

        // Timestamp part (hex)
        String timePart = Long.toHexString(System.currentTimeMillis());

        // Capture a fixed length timestamp
        int hexLen = timePart.length();
        int timeLength = Math.min(DEFAULT_TIMESTAMP_LENGTH, hexLen);

        String truncatedTime = timePart.substring(hexLen - timeLength);

        StringBuilder sb = new StringBuilder(len);
        sb.append(truncatedTime);

        // Fill the remaining length with random characters
        int remainingLength = len - timeLength;

        for (int i = 0; i < remainingLength; i++) {
            sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }

        return sb.toString();
    }
}
