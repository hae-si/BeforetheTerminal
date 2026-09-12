package org.agmas.noellesroles.mixin.btt;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.btt.BttPlayerComponent;
import org.agmas.noellesroles.btt.BttRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拾取门控（docx 2026-09-12，C-122）：「[枪] 和 [万能钥匙] **只有乘客可以捡**」。
 * <p>
 * wathe 原生只对 [枪]（`WatheItemTags.GUNS`）做了「无辜者 + 非投掷者 + 身上无枪」限制（`ItemEntityMixin`），
 * [万能钥匙] 与 BTT 的阵营变换都不在其中，故本 mixin 在 {@code ItemEntity#onPlayerCollision} HEAD 追加：
 * 目标物为 [枪]/[万能钥匙] 且拾取者**不是 BTT 乘客阵营**（含 **教团信徒**——docx：入教后「不能捡枪」）时取消拾取。
 * <p>
 * 与 wathe 的 `@WrapMethod` 同挂一个方法：wrap 决定是否进入原方法，本注入在**原方法内**运行，只做额外否决。
 */
@Mixin(ItemEntity.class)
public abstract class BttPickupGateMixin {

    @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
    private void bttPickupGate(PlayerEntity player, CallbackInfo ci) {
        ItemStack stack = ((ItemEntity) (Object) this).getStack();
        if (stack.isEmpty()) return;
        if (!stack.isOf(WatheItems.REVOLVER) && !stack.isOf(WatheItems.KEY)) return;
        if (!BttIdentity.isBttMode(player.getWorld())) return;
        GameWorldComponent gwc = GameWorldComponent.KEY.get(player.getWorld());
        if (!gwc.isRunning()) return;
        Role role = gwc.getRole(player);
        if (!BttRoles.isPassengerCamp(role)) {
            ci.cancel();
            return;
        }
        // 教团信徒：阵营已变教团 → 不能捡枪（docx「捡枪、处决规则与中立一致，即不能捡枪」）
        if (BttPlayerComponent.KEY.get(player).isCult()) ci.cancel();
    }
}
