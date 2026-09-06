package org.agmas.noellesroles.client.mixin.btt;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedHandledScreen;
import net.minecraft.client.option.KeyBinding;
import org.agmas.noellesroles.client.ui.btt.BttRoleWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BT-P2-UI 防误关（NR GuesserScreenDoNotClose 同构）：角色文本框聚焦时按 E 不关闭背包屏。
 * 与 NR 同点位 WrapOp 链式共存（任一 stopClosing 生效即不关闭）。
 */
@Mixin(LimitedHandledScreen.class)
public class BttScreenDoNotClose {

    @WrapOperation(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;matchesKey(II)Z", ordinal = 0))
    boolean bttDoNotCloseInventory(KeyBinding instance, int keyCode, int scanCode, Operation<Boolean> original) {
        if (BttRoleWidget.stopClosing) return false;
        return original.call(instance, keyCode, scanCode);
    }

    @Inject(method = "close", at = @At("HEAD"))
    void bttResetStopClosing(CallbackInfo ci) {
        BttRoleWidget.stopClosing = false;
    }
}
