package org.agmas.noellesroles.mixin.btt;

import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.btt.BttIdentity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DEMO-004：BTT 局内停用旧 wathe mood 空转（serverTick/generateTask 不检查 moodType，
 * 仅设 MoodType=NONE 不足——见 SYSTEM_SPEC §21-④）。doc 理智（BT-ECO-SAN）落地后移除本守卫。
 */
@Mixin(PlayerMoodComponent.class)
public abstract class MoodServerTickGuardMixin {

    @Shadow @Final private PlayerEntity player;

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private void bttSkipMoodTick(CallbackInfo ci) {
        if (BttIdentity.isBttMode(player.getWorld())) {
            ci.cancel();
        }
    }
}
