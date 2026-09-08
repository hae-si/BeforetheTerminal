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
    private static final Map<UUID, Map<String, String>> STRINGS = new HashMap<>();
    /** 全局（回合级）计数键：窃贼搜刮数等。 */
    private static final UUID GLOBAL = new UUID(0L, 0x6C0BA17L);
    /** 活跃尾声（BT-SYS-EPILOGUE 最小实现）："MAJO"/"CULT"；空=无 */
    public static String epilogueType = "";

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

    // ===== 醉酒（BT-SYS-DRUNK，C-060） =====

    /** 是否醉酒（不自知：调用方不得向本人提示状态，仅在其尝试行动时反馈失败） */
    public static boolean isDrunk(java.util.UUID player) {
        return getInt(player, "drunkTicks") > 0;
    }

    /** 施加/延长醉酒（取较大值，避免叠加覆盖短醉） */
    public static void applyDrunk(java.util.UUID player, int ticks) {
        setInt(player, "drunkTicks", Math.max(ticks, getInt(player, "drunkTicks")));
    }

    /** 每 tick 递减（BttEvents tick 循环调用） */
    public static void decrementDrunk(java.util.UUID player) {
        int v = getInt(player, "drunkTicks");
        if (v > 0) setInt(player, "drunkTicks", v - 1);
    }

    // ===== 字符串槽（关系搭档/类型等） =====

    public static String getString(java.util.UUID player, String key) {
        Map<String, String> m = STRINGS.get(player);
        return m == null ? null : m.get(key);
    }

    public static void setString(java.util.UUID player, String key, String value) {
        STRINGS.computeIfAbsent(player, k -> new HashMap<>()).put(key, value);
    }

    public static void resetRound() {
        INTS.clear();
        STRINGS.clear();
        epilogueType = "";
    }
}
