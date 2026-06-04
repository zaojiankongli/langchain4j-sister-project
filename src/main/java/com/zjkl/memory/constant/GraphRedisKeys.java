package com.zjkl.memory.constant;

import java.time.Duration;

public final class GraphRedisKeys {

    public static final String LAST_HASH_KEY = "graph:lasthash:";
    public static final String RAPID_FIRE_KEY = "graph:rapidfire:";
    public static final String SNAPSHOT_KEY = "graph:snapshot:";
    public static final String SNAPSHOT_VERSION_KEY = "graph:snapshotVersion:";
    public static final String LAST_WRITE_BATCH_KEY = "graph:lastWriteBatch:";
    public static final String LAST_REBUILD_AT_KEY = "graph:lastRebuildAt:";
    public static final String KNOWN_USERS_KEY = "graph:knownUsers";

    public static final Duration RAPID_FIRE_WINDOW = Duration.ofSeconds(30);
    public static final Duration RAPID_FIRE_BLOCK = Duration.ofMinutes(5);
    public static final Duration SNAPSHOT_TTL = Duration.ofDays(7);
    public static final Duration HASH_TTL = Duration.ofDays(7);

    private GraphRedisKeys() {
    }
}
