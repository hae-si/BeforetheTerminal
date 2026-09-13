package org.agmas.noellesroles.mixin.btt;

import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.btt.BttIdentity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BTT 理智阈值（C-144，docx 2026-09-12「&lt;60 醉酒 / &lt;50 物品看错 / &lt;40 混淆外貌 / 归零死亡」）。
 * <p>
 * wathe 的两个常量（0.55 中档 / 0.2 抑郁档）是 `static final` 且被**内联**进调用方字节码，改常量没用；
 * 但**效果**都走这两个方法（`isLowerThanMid()` = 物品看错，服务端生成 + 客户端渲染同点；
 * `isLowerThanDepressed()` = NR 的"混淆他人外貌"皮肤错认），故在这里覆盖判定值：
 * 中档 → **0.5**、抑郁档 → **0.4**。仅 BTT 局内生效（NR 局保持原版）。
 * <p>
 * **HUD 面/本能配色**仍按 wathe 内联常量（0.55/0.2）切换（纯表现层，客户端改动需实测，暂不动）。
 * 0.6 的"醉酒"档在 wathe/NR 无对应实现站点（BTT 的醉酒是独立状态，非理智派生）——【待作者口径】。
 */
@Mixin(PlayerMoodComponent.class)
public abstract class BttMoodThresholdMixin {
    /** docx：&lt;50 物品看错 */
    private static final float BTT_MID_THRESHOLD = 0.5F;
    /** docx：&lt;40 混淆外貌 */
    private static final float BTT_DEPRESSIVE_THRESHOLD = 0.4F;

    @Shadow @Final private PlayerEntity player;

    @Inject(method = "isLowerThanMid", at = @At("HEAD"), cancellable = true)
    private void btt$midThreshold(CallbackInfoReturnable<Boolean> cir) {
        if (!BttIdentity.isBttMode(player.getWorld())) return;
        cir.setReturnValue(((PlayerMoodComponent) (Object) this).getMood() < BTT_MID_THRESHOLD);
    }

    @Inject(method = "isLowerThanDepressed", at = @At("HEAD"), cancellable = true)
    private void btt$depressiveThreshold(CallbackInfoReturnable<Boolean> cir) {
        if (!BttIdentity.isBttMode(player.getWorld())) return;
        cir.setReturnValue(((PlayerMoodComponent) (Object) this).getMood() < BTT_DEPRESSIVE_THRESHOLD);
    }
}
