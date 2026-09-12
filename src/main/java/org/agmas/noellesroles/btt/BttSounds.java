package org.agmas.noellesroles.btt;

import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.Noellesroles;

/**
 * BTT 音频。
 * <ul>
 *   <li>尾声 BGM：`assets/noellesroles/sounds/epilogue/*.ogg`（**已是正式音频**，5 首，2026-09-12 核对）。</li>
 *   <li>技能/UI 音效键（C-127 建立，C-128 接线）：`assets/noellesroles/sounds/btt/*.ogg`——
 *       当前为 0.1 秒**静音**但格式合法的 Vorbis（作者裁定"只建空 ogg"），待作者替换为正式音源，替换后无需改代码。</li>
 * </ul>
 */
public final class BttSounds {
    private BttSounds() {}

    public static final SoundEvent EPILOGUE_MAJO = SoundEvent.of(Identifier.of(Noellesroles.MOD_ID, "epilogue_majo"));
    public static final SoundEvent EPILOGUE_CULT = SoundEvent.of(Identifier.of(Noellesroles.MOD_ID, "epilogue_cult"));
    public static final SoundEvent EPILOGUE_KIDNAPPER = SoundEvent.of(Identifier.of(Noellesroles.MOD_ID, "epilogue_kidnapper"));
    public static final SoundEvent EPILOGUE_GARDENER = SoundEvent.of(Identifier.of(Noellesroles.MOD_ID, "epilogue_gardener"));
    public static final SoundEvent EPILOGUE_SURVIVAL = SoundEvent.of(Identifier.of(Noellesroles.MOD_ID, "epilogue_survival"));

    public static final SoundEvent[] EPILOGUE_ALL = {
            EPILOGUE_MAJO, EPILOGUE_CULT, EPILOGUE_KIDNAPPER, EPILOGUE_GARDENER, EPILOGUE_SURVIVAL
    };

    // ===== 技能/UI 音效（C-127 建键，C-128 接线；暂为静音空 ogg） =====

    /** 纵火犯 <浇汽油> */
    public static final SoundEvent POUR_GASOLINE = btt("pour_gasoline");
    /** 恐怖分子炸弹滴滴（每 6 ticks） */
    public static final SoundEvent BOMB_BEEP = btt("bomb_beep");
    /** 小说家 <猜测> 猜对 */
    public static final SoundEvent NOVELIST_CORRECT = btt("novelist_correct");
    /** 花匠：小花生长（种子→幼苗 / 幼苗→成花） */
    public static final SoundEvent FLOWER_GROW = btt("flower_grow");
    /** 花匠：成花吞噬靠近者 */
    public static final SoundEvent FLOWER_BLOOM = btt("flower_bloom");
    /** 花匠：成花替死（消耗一株花） */
    public static final SoundEvent FLOWER_SHIELD = btt("flower_shield");
    /** 魔术师 <交换> */
    public static final SoundEvent SWAPPER_SWAP = btt("swapper_swap");
    /** 绳艺师 <拘束> */
    public static final SoundEvent RIGGER_BIND = btt("rigger_bind");
    /** 窃贼 <搜刮> */
    public static final SoundEvent THIEF_SCAVENGE = btt("thief_scavenge");
    /** 食人族食用尸体 */
    public static final SoundEvent CANNIBAL_EAT = btt("cannibal_eat");
    /** 异教领袖「审判」 */
    public static final SoundEvent JUDGMENT = btt("judgment");
    /** 乘务员 <广播> 开 */
    public static final SoundEvent BROADCAST_ON = btt("broadcast_on");
    /** 乘务员 <广播> 关 */
    public static final SoundEvent BROADCAST_OFF = btt("broadcast_off");
    /** 处子死亡 */
    public static final SoundEvent VIRGIN_DEATH = btt("virgin_death");
    /** 明星死亡 */
    public static final SoundEvent STAR_DEATH = btt("star_death");
    /** 饕餮消化（吞下时） */
    public static final SoundEvent KIDNAPPER_DIGEST = btt("kidnapper_digest");
    /** 通用 E 键选择音（客户端本机播放，只有自己听得到） */
    public static final SoundEvent UI_KEY_E = btt("ui_key_e");
    /** 通用 G 键选择音（客户端本机播放，只有自己听得到） */
    public static final SoundEvent UI_KEY_G = btt("ui_key_g");

    private static SoundEvent btt(String name) {
        return SoundEvent.of(Identifier.of(Noellesroles.MOD_ID, "btt_" + name));
    }

    /** 按尾声主持人翁类型取 BGM（"MAJO"/"CULT"/"KIDNAPPER"/"GARDENER"/其余=生还） */
    public static SoundEvent forEpilogue(String type) {
        return switch (type) {
            case "MAJO" -> EPILOGUE_MAJO;
            case "CULT" -> EPILOGUE_CULT;
            case "KIDNAPPER" -> EPILOGUE_KIDNAPPER;
            case "GARDENER" -> EPILOGUE_GARDENER;
            default -> EPILOGUE_SURVIVAL;
        };
    }
}
