package org.agmas.noellesroles.mixin.btt;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.doctor4t.wathe.api.GameMode;
import dev.doctor4t.wathe.api.MapEffect;
import dev.doctor4t.wathe.block.HornBlock;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.world.ServerWorld;
import org.agmas.noellesroles.btt.BttGameModes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 汽笛开局 → BTT（C-023）：wathe HornBlock.onUse 硬编码 startGame(MURDER, harpy_express_night)，
 * 与地图变量无关——BTT 安装后汽笛即开 BTT 局（doc 8min）。替换式演进语义（D3）：
 * 需开原版模式用 /wathe:start 显式指定。AutoStart 走世界当前模式（玩过 BTT 后自动跟随），不在此处理。
 */
@Mixin(HornBlock.class)
public abstract class BttHornStartMixin {

    @WrapOperation(method = "onUse", at = @At(value = "INVOKE",
            target = "Ldev/doctor4t/wathe/game/GameFunctions;startGame(Lnet/minecraft/server/world/ServerWorld;Ldev/doctor4t/wathe/api/GameMode;Ldev/doctor4t/wathe/api/MapEffect;I)V"))
    private void bttHornStart(ServerWorld world, GameMode gameMode, MapEffect mapEffect, int time, Operation<Void> original) {
        GameMode btt = BttGameModes.BEFORE_THE_TERMINAL;
        original.call(world, btt, mapEffect, GameConstants.getInTicks(btt.defaultStartTime, 0));
    }
}
