package org.agmas.noellesroles.client.mixin.btt;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.client.MinecraftClient;
import org.agmas.noellesroles.btt.BttIdentity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 需求提示文案（C-133，用户 2026-09-13）：wathe 的 {@code MoodRenderer$TaskRenderer#tick}
 * 用 {@code WatheClient.isKiller()} 在 `task.fake`（"你可以假装……"）与 `task.feel`（"你觉得该……"）间二选一。
 * BTT 的**独行/外人**不是凶手席位（不走 killer 特性），但 mood 分类是 FAKE（蓝旗/品红旗）——
 * 于是显示成乘客口吻，与设计不符。这里把判定改为「wathe 原判定 **或** BTT 的 mood 分类是 FAKE」：
 * 凶手/疯子（wathe 判定为真）不受影响；独行/外人（FAKE）改口吻；其余乘客保持 feel。
 * 只改**文案选择**，不碰任何 killer 特性/本能/商店门控。
 */
@Mixin(targets = "dev.doctor4t.wathe.client.gui.MoodRenderer$TaskRenderer")
public abstract class BttMoodTaskTextMixin {

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Ldev/doctor4t/wathe/client/WatheClient;isKiller()Z"))
    private boolean bttTaskText(Operation<Boolean> original) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || !BttIdentity.isBttMode(client.world)) {
            return original.call();
        }
        Role role = GameWorldComponent.KEY.get(client.world).getRole(client.player);
        if (role != null && role.getMoodType() == Role.MoodType.FAKE) return true;
        return original.call();
    }
}
