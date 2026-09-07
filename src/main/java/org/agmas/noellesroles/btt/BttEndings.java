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
        /** 鸣泣之时（原"无人生还"，2026-09-06 更名）：主犯全灭后从犯杀光乘客与外人（凶手侧结局） */
        NAKU_KORO,
        /** 窃贼独胜：搜刮过半（BT-THIEF-SOLO：wathe WinStatus 无单人位，暂映射 NONE，文本由 BttEndTextMixin 呈现） */
        THIEF_WIN,
        /** 小说家独胜：猜对过半（同上 BT-NOVELIST-SOLO） */
        NOVELIST_WIN,
        /** 异端分子特殊结局（doc：对调胜负+特殊宣言）：乘客达成条件→翻转为凶手胜 */
        HERETIC_KILLER,
        /** 异端分子特殊结局：凶手达成条件→翻转为乘客胜 */
        HERETIC_PASSENGER
    }

    /**
     * 纯函数：aliveMurderers=存活主犯数; alivePassengers=存活乘客数; aliveOutsiders=存活中立+外人数; stationReached=到站。
     * 判定优先级：乘客+外人全灭(主犯灭=无人生还/主犯活=血染) → 凶手团灭(审判) → 到站(旅途)。
     */
    public static Ending decide(int alivePrincipals, int aliveAccomplices, int alivePassengers,
                                int aliveOutsiderNeutrals, boolean stationReached) {
        // 2026-09-06 策划修订：审判落幕=杀光凶手和外人中立（独行/狂人不阻塞）；
        // 血染=杀光乘客和外人且主犯未死；鸣泣之时=主犯死后从犯杀光乘客和外人。
        // aliveOutsiderNeutrals 仅数外人中立（魔女/救世主/饕餮/花匠）；独行/狂人不参与结局阻塞。
        if (alivePassengers <= 0 && aliveOutsiderNeutrals <= 0) {
            if (alivePrincipals > 0) return Ending.BLOOD_EXPRESS;
            if (aliveAccomplices > 0) return Ending.NAKU_KORO;
            return Ending.TRIAL_COMPLETE; // 审判落幕：凶手全灭+外人中立全灭
        }
        if (alivePrincipals <= 0 && aliveAccomplices <= 0 && aliveOutsiderNeutrals <= 0) {
            return Ending.TRIAL_COMPLETE; // 审判落幕（乘客存活路径）
        }
        // 主犯全灭：有从犯存活 → 游戏继续（鸣泣之时候选，到站仍=旅途结束）；无从犯 → 等待/到站
        if (alivePrincipals <= 0) {
            return stationReached ? Ending.JOURNEY_END : Ending.NONE;
        }
        if (stationReached) {
            return Ending.JOURNEY_END;
        }
        return Ending.NONE;
    }

    /** 异端分子（未决点#5 字面口径）：乘客侧胜↔凶手侧胜 对调 */
    public static GameFunctions.WinStatus flip(GameFunctions.WinStatus ws) {
        return switch (ws) {
            case PASSENGERS, TIME -> GameFunctions.WinStatus.KILLERS;
            case KILLERS -> GameFunctions.WinStatus.PASSENGERS;
            default -> ws;
        };
    }

    /** wathe WinStatus 映射（驱动原版回合结束流程与结束覆盖层文本选择） */
    public static GameFunctions.WinStatus winStatusOf(Ending ending) {
        return switch (ending) {
            case BLOOD_EXPRESS, NAKU_KORO -> GameFunctions.WinStatus.KILLERS;
            case JOURNEY_END -> GameFunctions.WinStatus.TIME;
            case TRIAL_COMPLETE -> GameFunctions.WinStatus.PASSENGERS;
            case HERETIC_KILLER -> GameFunctions.WinStatus.KILLERS;
            case HERETIC_PASSENGER -> GameFunctions.WinStatus.PASSENGERS;
            default -> GameFunctions.WinStatus.NONE;
        };
    }
}
