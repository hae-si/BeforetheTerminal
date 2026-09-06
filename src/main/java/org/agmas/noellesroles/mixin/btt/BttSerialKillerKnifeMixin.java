package org.agmas.noellesroles.mixin.btt;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.KnifeStabPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.btt.BttRoles;
import org.agmas.noellesroles.btt.BttState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/**
 * 连环杀手（doc）：杀死祭品 → 刀不进入冷却（本刀），且基础冷却永久 −10s。
 * 刀 CD 由 KnifeStabPayload.Receiver 在 killPlayer 之后直接 set（1200t），故必须在此覆写：
 * - lastKillWasSacrifice（BttKillHookMixin 于 killPlayer 内置位）→ 跳过本刀 CD；
 * - 否则 CD = max(0, 原值 + serialCdDelta)（serialCdDelta 为负，每祭品 −200t）。
 */
@Mixin(KnifeStabPayload.Receiver.class)
public abstract class BttSerialKillerKnifeMixin {

    @WrapOperation(
            method = "receive",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/ItemCooldownManager;set(Lnet/minecraft/item/Item;I)V")
    )
    private void bttSerialKnifeCd(ItemCooldownManager manager, Item item, int ticks, Operation<Void> original,
                                  KnifeStabPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity player = context.player();
        if (BttIdentity.isBttMode(player.getWorld())
                && GameWorldComponent.KEY.get(player.getWorld()).isRole(player, BttRoles.SERIALKILLER)) {
            if (BttState.lastKillWasSacrifice) {
                BttState.lastKillWasSacrifice = false; // 本刀不进 CD
                return;
            }
            int delta = BttState.getInt(player.getUuid(), "serialCdDelta");
            if (delta != 0) {
                original.call(manager, item, Math.max(0, ticks + delta));
                return;
            }
        }
        original.call(manager, item, ticks);
    }
}
