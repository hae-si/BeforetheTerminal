package org.agmas.noellesroles.client.mixin.btt;

import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedHandledScreen;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import org.agmas.noellesroles.btt.BttIdentity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 商店过滤（客户端，C-031 修订）：BTT 局内隐藏匕首/左轮/便签 widget。
 * widget 仍按原 index 添加（StoreBuyPayload 索引稳定）；entry public 直接判。
 */
@Mixin(LimitedInventoryScreen.class)
public abstract class BttShopFilterMixin extends LimitedHandledScreen<PlayerScreenHandler> {

    @Shadow @Final public ClientPlayerEntity player;

    public BttShopFilterMixin(PlayerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void bttHideWeaponEntries(CallbackInfo ci) {
        if (player == null || !BttIdentity.isBttMode(player.getWorld())) return;
        for (var child : this.children()) {
            if (child instanceof LimitedInventoryScreen.StoreItemWidget w) {
                var item = w.entry.stack().getItem();
                if (item == WatheItems.KNIFE || item == WatheItems.REVOLVER || item == WatheItems.NOTE) {
                    w.visible = false;
                }
            }
        }
    }
}
