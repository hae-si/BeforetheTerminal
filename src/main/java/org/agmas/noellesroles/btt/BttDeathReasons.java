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
}
