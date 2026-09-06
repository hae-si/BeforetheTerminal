package org.agmas.noellesroles.mixin.btt;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.util.GunShootPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.btt.BttRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 处决掉枪（C-019）：WrapOperation 在 GunShootPayload.Receiver 的 killPlayer
 * STATIC INVOKE 点（无 receiver，需捕获外部方法参数 payload+context）。
 * 每次 GUN 击杀后检查射手：
 * - 义警 → 不扔枪，CD 60s（策划特例）
 * - 其他乘客（处决）→ 扔枪 + CD 60s
 * - 非乘客（从犯/外人）→ 非处决，不扔枪
 */
@Mixin(GunShootPayload.Receiver.class)
public abstract class BttGunDropMixin {

    @WrapOperation(method = "receive", at = @At(value = "INVOKE",
            target = "Ldev/doctor4t/wathe/game/GameFunctions;killPlayer(Lnet/minecraft/entity/player/PlayerEntity;ZLnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Identifier;)V"))
    private static void bttGunDrop(PlayerEntity victim, boolean spawnBody,
                                   PlayerEntity killer, Identifier reason,
                                   Operation<Void> original,
                                   GunShootPayload payload, ServerPlayNetworking.Context context) {
        original.call(victim, spawnBody, killer, reason);

        if (!BttIdentity.isBttMode(victim.getWorld())) return;
        if (reason != GameConstants.DeathReasons.GUN) return;
        if (!(killer instanceof ServerPlayerEntity shooter)) return;
        GameWorldComponent gwc = GameWorldComponent.KEY.get(shooter.getWorld());

        // 非乘客 → 非处决 → 不扔枪（强盗/魔女等）
        if (!gwc.isInnocent(shooter)) return;

        // 义警特例：处决不扔枪，CD 60s
        if (gwc.isRole(shooter, BttRoles.VIGILANTE)) {
            shooter.getItemCooldownManager().set(WatheItems.REVOLVER, 1200);
            return;
        }

        // 其他乘客处决 → 扔枪 + CD 60s
        for (int i = 0; i < shooter.getInventory().size(); i++) {
            if (shooter.getInventory().getStack(i).isOf(WatheItems.REVOLVER)) {
                shooter.getInventory().removeStack(i, 1);
                ItemEntity item = shooter.dropItem(new ItemStack(WatheItems.REVOLVER), false, false);
                if (item != null) {
                    item.setPickupDelay(10);
                    item.setThrower(shooter);
                }
                break;
            }
        }
        shooter.getItemCooldownManager().set(WatheItems.REVOLVER, 1200);
    }
}
