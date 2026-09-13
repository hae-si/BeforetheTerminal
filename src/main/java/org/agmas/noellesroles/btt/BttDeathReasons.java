package org.agmas.noellesroles.btt;

import net.minecraft.util.Identifier;

/**
 * BTT 追加死因（docx 2026-09-07 死因列表；wathe 原生仅 7 个）。
 * 仅登记当前实现会产生差异的死因；其余 doc 死因随对应身份实装再补。
 */
public final class BttDeathReasons {
    private BttDeathReasons() {}

    /** 狙击魔法：猎人 <狙击> 命中主犯 */
    public static final Identifier SNIPE_MAGIC = Identifier.of("noellesroles", "snipe_magic");
    /** 预言中断：预言家 <猜测> 猜错自裁 */
    public static final Identifier PROPHECY_INTERRUPTED = Identifier.of("noellesroles", "prophecy_interrupted");
    /** 殉情：恋人一方死亡，另一方殉情 */
    public static final Identifier LOVER_SUICIDE = Identifier.of("noellesroles", "lover_suicide");
    /** 殉教：救世主死亡 → 信徒集体殉教 */
    public static final Identifier MARTYRDOM = Identifier.of("noellesroles", "martyrdom");
    /** 自裁：游侠误杀/魔像非误杀/暴乱期乘客误杀 */
    public static final Identifier SELF_EXECUTION = Identifier.of("noellesroles", "self_execution");
    /** 识破魔法：刺客 <识破> 命中 */
    public static final Identifier IDENTIFY_MAGIC = Identifier.of("noellesroles", "identify_magic");
    /** 氦气自爆：派对主变声两次 */
    public static final Identifier HELIUM_SELF_DESTRUCT = Identifier.of("noellesroles", "helium_self_destruct");
    /** 炸弹：恐怖分子炸弹箱爆炸 */
    public static final Identifier BOMB = Identifier.of("noellesroles", "bomb");
    /** 绽放：花匠成花（铃兰）吞噬第一个靠近者 */
    public static final Identifier BLOOM = Identifier.of("noellesroles", "bloom");
    /** 审判：异教领袖在计数窗口内被处决两次 → 其阵营对立方全员（用户 2026-09-12 追加） */
    public static final Identifier JUDGMENT = Identifier.of("noellesroles", "judgment");
    /** 崩溃：理智归零死亡（docx 死因表第 1 项；C-134 起用于黑死病病人耗尽理智的"精神崩溃"） */
    public static final Identifier COLLAPSE = Identifier.of("noellesroles", "collapse");
    /** 飞剑：剑客 <剑> 射线贯穿（docx 死因表"飞剑"；C-136） */
    public static final Identifier FLYING_SWORD = Identifier.of("noellesroles", "flying_sword");
}
