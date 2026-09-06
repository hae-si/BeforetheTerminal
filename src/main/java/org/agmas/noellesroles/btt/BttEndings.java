package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.game.GameFunctions;

/**
 * Demo（N=6，无从犯/外人）下的 doc 结局判定（策划 2026-09-04 修订版：乘客两种胜利结局，
 * 均乘客全胜；执法/平民不再分账）：
 * - 审判完成：到站前凶手全灭 → 乘客全胜（↔ wathe WinStatus.PASSENGERS）
 * - 旅途结束：到站时凶手未灭 → 乘客全胜（↔ wathe WinStatus.TIME）
 * - 血染快车：乘客全灭且主犯存活 → 凶手胜（↔ wathe WinStatus.KILLERS；凶手侧文案与 wathe
 *   不对应属策划已知；无人生还需从犯，Phase 2 借 lastEnding 细分）
 * 结局文本由客户端 BttEndTextMixin 直接改写 wathe getEndText（不在聊天框/不自绘）。
 */
public final class BttEndings {
    private BttEndings() {}

    public enum Ending {
        NONE, TRIAL_COMPLETE, JOURNEY_END, BLOOD_EXPRESS,
        /** 无人生还：乘客与凶手全灭（凶手侧结局，doc 第二凶手结局；2026-09-05 用户报告补齐） */
        NO_SURVIVORS,
        /** 窃贼独胜：搜刮过半（BT-THIEF-SOLO：wathe WinStatus 无单人位，暂映射 NONE，文本由 BttEndTextMixin 呈现） */
        THIEF_WIN,
        /** 小说家独胜：猜对过半（同上 BT-NOVELIST-SOLO） */
        NOVELIST_WIN
    }

    /**
     * 纯函数：aliveMurderers=存活主犯数; alivePassengers=存活乘客数; aliveOutsiders=存活中立+外人数; stationReached=到站。
     * 判定优先级：乘客+外人全灭(主犯灭=无人生还/主犯活=血染) → 凶手团灭(审判) → 到站(旅途)。
     */
    public static Ending decide(int aliveMurderers, int alivePassengers, int aliveOutsiders, boolean stationReached) {
        // doc：无人生还=主犯全灭后从犯杀光乘客与外人（血染快车=主犯仍存活杀光乘客）
        if (alivePassengers <= 0 && aliveOutsiders <= 0 && aliveMurderers <= 0) {
            return Ending.NO_SURVIVORS;
        }
        if (alivePassengers <= 0) {
            return Ending.BLOOD_EXPRESS;
        }
        if (aliveMurderers <= 0) {
            return Ending.TRIAL_COMPLETE;
        }
        if (stationReached) {
            return Ending.JOURNEY_END;
        }
        return Ending.NONE;
    }

    /** wathe WinStatus 映射（驱动原版回合结束流程与结束覆盖层文本选择） */
    public static GameFunctions.WinStatus winStatusOf(Ending ending) {
        return switch (ending) {
            case BLOOD_EXPRESS, NO_SURVIVORS -> GameFunctions.WinStatus.KILLERS;
            case JOURNEY_END -> GameFunctions.WinStatus.TIME;
            case TRIAL_COMPLETE -> GameFunctions.WinStatus.PASSENGERS;
            default -> GameFunctions.WinStatus.NONE;
        };
    }
}
