package org.agmas.noellesroles.client.mixin.btt;

import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedHandledScreen;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
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

    // BTT：商店仅凶手阵营可见（处子=innocent 不显示；卧底=mimic 非 killer 阵营不显示）
    @WrapOperation(method = "init", at = @At(value = "INVOKE",
            target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;canUseKillerFeatures(Lnet/minecraft/entity/player/PlayerEntity;)Z"))
    private static boolean bttKillerOnlyShop(GameWorldComponent instance, PlayerEntity p, Operation<Boolean> original) {
        if (!BttIdentity.isBttMode(p.getWorld())) return original.call(instance, p);
        var role = instance.getRole(p);
        var f = role == null ? null : org.agmas.noellesroles.btt.BttRoles.factionOf(role);
        return f == org.agmas.noellesroles.btt.BttRoles.Faction.PRINCIPAL
                || f == org.agmas.noellesroles.btt.BttRoles.Faction.ACCOMPLICE;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void bttHideWeaponEntries(CallbackInfo ci) {
        if (player == null || !BttIdentity.isBttMode(player.getWorld())) return;
        for (var child : this.children()) {
            if (child instanceof LimitedInventoryScreen.StoreItemWidget w) {
                var item = w.entry.stack().getItem();
                if (item == WatheItems.KNIFE || item == WatheItems.REVOLVER || item == WatheItems.NOTE || item == WatheItems.FIRECRACKER) {
                    w.visible = false;
                }
            }
        }
    }
}
