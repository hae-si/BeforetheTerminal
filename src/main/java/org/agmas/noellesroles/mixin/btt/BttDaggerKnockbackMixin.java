package org.agmas.noellesroles.mixin.btt;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.btt.BttDaggerKnockback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 匕首击退（C-133，用户 2026-09-13"清道夫：匕首应当可以击退"）。
 * wathe 的刀靠 `LivingEntityMixin` 在 tick 时挂 +0.5 攻击击退属性（持刀才生效，左键攻击推人）。
 * 匕首是 BTT 自研物品、不进 wathe 那条判定，故这里**并挂同一数值**（独立 modifier id，互不覆盖），
 * 持匕首时获得相同击退，其余行为（即时无声刀杀、1 分钟冷却）不变。
 * <p>
 * 本类**不持有任何静态字段**：mixin 的静态初始化会并入 {@code LivingEntity.<clinit>}（Bootstrap 早期），
 * 逻辑放 {@link BttDaggerKnockback}（首次 tick 才加载）。
 */
@Mixin(LivingEntity.class)
public abstract class BttDaggerKnockbackMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void bttDaggerKnockback(CallbackInfo ci) {
        if ((Object) this instanceof PlayerEntity player) BttDaggerKnockback.tick(player);
    }
}
