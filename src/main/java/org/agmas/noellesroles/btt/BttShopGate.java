package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.index.WatheItems;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 商店过滤（C-031 修订）：匕首/左轮/便签在 BTT 局内**隐藏且拒绝购买**。
 * 方案=索引稳定：不修改 SHOP_ENTRIES（改列表会造成客户端/服务端两份静态列表索引错位→商店失灵），
 * 客户端 BttShopFilterMixin 隐藏 widget（保留 index 参数）、服务端 BttShopRejectMixin 拒绝（按索引）。
 */
public final class BttShopGate {
    private BttShopGate() {}

    public static final Set<Integer> HIDE_INDEXES = new HashSet<>();

    /** mod init 时反射捕获过滤项索引（SHOP_ENTRIES 包私有；客户端/服务端各自执行一致） */
    public static void init() {
        HIDE_INDEXES.clear();
        try {
            var field = Class.forName("dev.doctor4t.wathe.game.GameConstants").getDeclaredField("SHOP_ENTRIES");
            field.setAccessible(true);
            List<?> entries = (List<?>) field.get(null);
            for (int i = 0; i < entries.size(); i++) {
                var stack = (net.minecraft.item.ItemStack) entries.get(i).getClass().getMethod("stack").invoke(entries.get(i));
                var item = stack.getItem();
                if (item == WatheItems.KNIFE || item == WatheItems.REVOLVER || item == WatheItems.NOTE) {
                    HIDE_INDEXES.add(i);
                }
            }
            BeforeTheTerminalGameMode.LOGGER.info("[BTT] shop hidden indexes: {}", HIDE_INDEXES);
        } catch (Exception e) {
            BeforeTheTerminalGameMode.LOGGER.warn("[BTT] shop gate init failed: {}", e.toString());
        }
    }
}
