package org.agmas.noellesroles.client.mixin.btt;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.doctor4t.wathe.client.gui.RoleNameRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.agmas.noellesroles.client.spirit.SpiritCameraHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 梦游病 &lt;入梦&gt; 匿名化②：出窍中**其他玩家**的头顶名牌不显示
 * （doc「无法辨认任何人样貌」，C-088）。
 * <p>
 * 挂点与既有 {@code MorphlingRoleNameRendererMixin} 同款（renderHud 内的 getDisplayName 调用）。
 */
@Mixin(RoleNameRenderer.class)
public abstract class BttSpiritNameMixin {

    @WrapOperation(method = "renderHud",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getDisplayName()Lnet/minecraft/text/Text;"))
    private static Text btt$spiritHideName(PlayerEntity instance, Operation<Text> original) {
        if (SpiritCameraHandler.isActive() && instance != MinecraftClient.getInstance().player) {
            return Text.literal("");
        }
        return original.call(instance);
    }
}