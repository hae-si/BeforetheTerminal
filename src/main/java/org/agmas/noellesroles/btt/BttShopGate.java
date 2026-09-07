package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.item.ItemStack;
import org.agmas.noellesroles.ModItems;

import java.util.List;

/**
 * BTT 凶手商店（C-047，NR 四店同模式）：**自建条目表、双端代码固定**，不触碰
 * wathe {@code GameConstants.SHOP_ENTRIES}（static final 双端副本无同步，改列表必致索引错位）。
 * 客户端 BttShopClientMixin 用本表替换 init 的条目源；服务端 BttShopBuyMixin 按同一顺序处理 tryBuy。
 * 条目 = 原版可用商品（手雷/疯魔/毒药/蝎子/撬棍/尸体袋/短路器）+ 裁定项"开锁器→万能钥匙 50"（2026-09-07）。
 * doc 价目表（BT-SYS-SHOP）与门闩道具仍待办；毒药价差（doc 50 / 现原生 100）随 BT-SYS-SHOP 一并对齐。
 */
public final class BttShopGate {
    private BttShopGate() {}

    /** BTT 局内凶手商店条目（顺序即索引约定；双端一致） */
    public static final List<ShopEntry> BTT_ENTRIES = List.of(
            new ShopEntry(WatheItems.GRENADE.getDefaultStack(), 350, ShopEntry.Type.WEAPON),
            new ShopEntry(WatheItems.PSYCHO_MODE.getDefaultStack(), 300, ShopEntry.Type.TOOL),
            new ShopEntry(WatheItems.POISON_VIAL.getDefaultStack(), 100, ShopEntry.Type.POISON),
            new ShopEntry(WatheItems.SCORPION.getDefaultStack(), 50, ShopEntry.Type.POISON),
            new ShopEntry(new ItemStack(ModItems.MASTER_KEY), 50, ShopEntry.Type.TOOL),
            new ShopEntry(WatheItems.CROWBAR.getDefaultStack(), 25, ShopEntry.Type.TOOL),
            new ShopEntry(WatheItems.BODY_BAG.getDefaultStack(), 200, ShopEntry.Type.TOOL),
            new ShopEntry(WatheItems.BLACKOUT.getDefaultStack(), 200, ShopEntry.Type.TOOL)
    );

    /** 保留调用点（BttEvents.init）；条目表为静态常量，无需初始化逻辑 */
    public static void init() {
        BeforeTheTerminalGameMode.LOGGER.info("[BTT] shop entries: {} (master key replaces lockpick)", BTT_ENTRIES.size());
    }
}
