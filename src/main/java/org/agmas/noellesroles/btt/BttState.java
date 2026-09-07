package org.agmas.noellesroles.btt;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BT-ARCH-002 轻量版：每玩家回合级状态存储（静态 Map，finalizeGame 时清空）。
 * 适用于简单计数/标记/一次性状态，不需要 NBT 持久化或跨回合同步。
 * Phase 2 后续如需持久化/客户端同步，升级为 CCA 组件。
 */
public final class BttState {
    private BttState() {}

    private static final Map<UUID, Map<String, Integer>> INTS = new HashMap<>();
    /** 全局（回合级）计数键：窃贼搜刮数等。 */
    private static final UUID GLOBAL = new UUID(0L, 0x6C0BA17L);
    /** 魔女尾声剩余 tick（BT-SYS-EPILOGUE 最小实现；0=无） */
    public static int majoEpilogueTicks = 0;

    public static int getInt(UUID player, String key) {
        Map<String, Integer> m = INTS.get(player);
        return m == null ? 0 : m.getOrDefault(key, 0);
    }

    public static void setInt(UUID player, String key, int value) {
        INTS.computeIfAbsent(player, k -> new HashMap<>()).put(key, value);
    }

    public static void increment(UUID player, String key) {
        setInt(player, key, getInt(player, key) + 1);
    }

    /** 回合级全局计数（窃贼搜刮数等） */
    public static int getGlobal(String key) {
        return getInt(GLOBAL, key);
    }

    public static void setGlobal(String key, int value) {
        setInt(GLOBAL, key, value);
    }

    public static void resetRound() {
        INTS.clear();
        majoEpilogueTicks = 0;
    }
}
