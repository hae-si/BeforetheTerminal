package org.agmas.noellesroles.mixin.btt;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.doctor4t.wathe.api.GameMode;
import dev.doctor4t.wathe.api.MapEffect;
import dev.doctor4t.wathe.cca.AutoStartComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.world.ServerWorld;
import org.agmas.noellesroles.btt.BttGameModes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * AutoStart（ready 自动开局）→ BTT（C-031）：世界 gameMode 会持久化在存档（GameWorldComponent NBT），
 * 若曾用原版 murder 开局，重启后 AutoStart 仍开 murder——强制 BTT（同 BttHornStartMixin 口径）。
 */
@Mixin(AutoStartComponent.class)
public abstract class BttAutoStartMixin {

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Ldev/doctor4t/wathe/game/GameFunctions;startGame(Lnet/minecraft/server/world/ServerWorld;Ldev/doctor4t/wathe/api/GameMode;Ldev/doctor4t/wathe/api/MapEffect;I)V"))
    private void bttAutoStart(ServerWorld world, GameMode gameMode, MapEffect mapEffect, int time, Operation<Void> original) {
        GameMode btt = BttGameModes.BEFORE_THE_TERMINAL;
        original.call(world, btt, mapEffect, GameConstants.getInTicks(btt.defaultStartTime, 0));
    }
}
