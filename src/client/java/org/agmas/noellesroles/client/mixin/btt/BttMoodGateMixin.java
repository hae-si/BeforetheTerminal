package org.agmas.noellesroles.client.mixin.btt;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.doctor4t.wathe.api.GameMode;
import dev.doctor4t.wathe.api.WatheGameModes;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.gui.MoodRenderer;
import org.agmas.noellesroles.btt.BeforeTheTerminalGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 理智/需求（C-016 回退隔离后）：MoodRenderer.renderHud 的门控为
 * `getGameMode() != WatheGameModes.MURDER`，BTT 模式被排除导致 mood HUD 不渲染。
 * 此 mixin 让 BTT 模式通过门控（与 HML MoodRendererMixin 对 modded 模式的处理相同）。
 * 服务端 serverTick 无 gameMode 门控，mood 消耗/需求生成本就在 BTT 内运行。
 */
@Mixin(MoodRenderer.class)
public abstract class BttMoodGateMixin {

    @WrapOperation(method = "renderHud",
        at = @At(value = "INVOKE",
                 target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;getGameMode()Ldev/doctor4t/wathe/api/GameMode;"))
    private static GameMode bttMoodGate(GameWorldComponent instance, Operation<GameMode> original) {
        GameMode mode = original.call(instance);
        if (mode instanceof BeforeTheTerminalGameMode) return WatheGameModes.MURDER;
        return mode;
    }
}
