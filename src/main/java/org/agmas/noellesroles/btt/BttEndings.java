package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.api.Role;

/**
 * Demo（N=6，无从犯/外人）下的 doc 结局判定（GD §4.7）：
 * - 审判完成：到站前凶手全灭，牺牲乘客 ≤ 一半 → 所有乘客胜
 * - 执法落幕：到站前凶手全灭，牺牲乘客 > 一半 → 平民胜，执法败
 * - 血染快车：乘客全灭且主犯存活 → 凶手胜
 * - 旅途结束：到站时凶手未灭 → 平民胜，执法败
 * 结局文本由客户端直接替换 wathe 回合结束覆盖层（BttEndScreenMixin），不在聊天框输出。
 */
public final class BttEndings {
    private BttEndings() {}

    public enum Ending {
        NONE, TRIAL_COMPLETE, ENFORCEMENT_END, JOURNEY_END, BLOOD_EXPRESS
    }

    /** 观看者个人结局（覆盖层第三行） */
    public enum Personal { NONE, WIN, LOSE, NEUTRAL }

    /**
     * 纯函数：aliveMurderers=存活主犯数; alivePassengers=存活乘客数; deadPassengers=死亡乘客数;
     * totalPassengers=乘客总数; stationReached=到站(计时归零)。
     * 判定优先级：凶手团灭 → 乘客团灭(血染) → 到站(旅途)。
     */
    public static Ending decide(int aliveMurderers, int alivePassengers, int deadPassengers, int totalPassengers, boolean stationReached) {
        if (aliveMurderers <= 0) {
            if (alivePassengers > 0) {
                return deadPassengers <= totalPassengers / 2 ? Ending.TRIAL_COMPLETE : Ending.ENFORCEMENT_END;
            }
            return Ending.JOURNEY_END; // 边缘：双方皆灭，无从犯无法达成“无人生还”，按旅途收束
        }
        if (alivePassengers <= 0) {
            return Ending.BLOOD_EXPRESS;
        }
        if (stationReached) {
            return Ending.JOURNEY_END;
        }
        return Ending.NONE;
    }

    /** wathe WinStatus 映射（驱动原版回合结束流程/二分近似；doc 真相由覆盖层承载） */
    public static dev.doctor4t.wathe.game.GameFunctions.WinStatus winStatusOf(Ending ending) {
        return switch (ending) {
            case BLOOD_EXPRESS -> dev.doctor4t.wathe.game.GameFunctions.WinStatus.KILLERS;
            case JOURNEY_END -> dev.doctor4t.wathe.game.GameFunctions.WinStatus.TIME;
            default -> dev.doctor4t.wathe.game.GameFunctions.WinStatus.PASSENGERS;
        };
    }

    /** 个人胜负（覆盖层“你”行）：义警=执法，医生/处子/列车长=平民，教父=凶手，小丑=中立 */
    public static Personal personalOutcome(Ending ending, Role role) {
        if (ending == null || ending == Ending.NONE || role == null) return Personal.NONE;
        boolean isMurderer = role.canUseKiller();
        boolean isEnforcer = role == BttRoles.VIGILANTE;
        boolean isInnocent = role.isInnocent();
        return switch (ending) {
            case TRIAL_COMPLETE -> isInnocent ? Personal.WIN : (isMurderer ? Personal.LOSE : Personal.NEUTRAL);
            case ENFORCEMENT_END, JOURNEY_END -> isEnforcer ? Personal.LOSE : (isInnocent ? Personal.WIN : Personal.NEUTRAL);
            case BLOOD_EXPRESS -> isMurderer ? Personal.WIN : (isInnocent ? Personal.LOSE : Personal.NEUTRAL);
            default -> Personal.NONE;
        };
    }

    /** 覆盖层配色（ARGB） */
    public static int titleColor(Ending ending) {
        return switch (ending) {
            case TRIAL_COMPLETE -> 0xFF55FF55; // 绿
            case ENFORCEMENT_END -> 0xFFFFAA00; // 金
            case JOURNEY_END -> 0xFF55FFFF;    // 青
            case BLOOD_EXPRESS -> 0xFFFF5555;  // 红
            default -> 0xFFFFFFFF;
        };
    }
}
