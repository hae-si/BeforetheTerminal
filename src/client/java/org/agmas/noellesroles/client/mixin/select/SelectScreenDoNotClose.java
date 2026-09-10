package org.agmas.noellesroles.client.mixin.select;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedHandledScreen;
import net.minecraft.client.option.KeyBinding;
import org.agmas.noellesroles.client.ui.select.SelectRoleWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 选人屏防误关（R3 合并原 {@code GuesserScreenDoNotClose} + {@code BttScreenDoNotClose}）：
 * 角色文本框聚焦时按 E 不关闭背包屏。
 */
@Mixin(LimitedHandledScreen.class)
public class SelectScreenDoNotClose {

    @WrapOperation(method = "keyPressed", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/option/KeyBinding;matchesKey(II)Z", ordinal = 0))
    boolean selectDoNotClose(KeyBinding instance, int keyCode, int scanCode, Operation<Boolean> original) {
        if (SelectRoleWidget.stopClosing) return false;
        return original.call(instance, keyCode, scanCode);
    }

    @Inject(method = "close", at = @At("HEAD"))
    void selectResetStopClosing(CallbackInfo ci) {
        SelectRoleWidget.stopClosing = false;
    }
}
