package com.zjkl.memory.constant;

import java.time.Duration;

public final class MemoryRedisKeys {

    public static final String HISTORY_KEY = "chat:history:";
    public static final String SUMMARY_KEY = "chat:summary:";
    public static final String LAST_COMPRESSED_SIZE_KEY = "chat:lastCompressedSize:";

    public static final int SUMMARY_THRESHOLD = 180;
    public static final int KEEP_RECENT_COUNT = 50;
    public static final int OFFLINE_NEW_MESSAGES_THRESHOLD = 110;
    public static final int INCREMENTAL_SUMMARY_WINDOW = 130;

    public static final Duration EXPIRATION_1_DAY = Duration.ofDays(1);
    public static final Duration EXPIRATION_7_DAYS = Duration.ofDays(7);

    public static final String LOCK_KEY_PREFIX = "lock:chat:compact:";

    private MemoryRedisKeys() {}
}
