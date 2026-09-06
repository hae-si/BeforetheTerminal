package org.agmas.noellesroles.mixin.btt;

import dev.doctor4t.wathe.api.Role;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.noellesroles.btt.BttIdentity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * /forceRole 兼容（C-041，用户裁定"直接重写 NR 加的 forceRole、不要 btt 命名空间"）：
 * HML 的 /forceRole <player> <role>（RoleArgumentType，补全=全 id）写 HML FORCED_MODDED_ROLE 表
 * （HML murder 局消费）——BTT 从不消费。本 mixin 在 addToForcedRoles TAIL **双写** BttIdentity.FORCED
 * （下一局 BTT 开局消费）；HML 原逻辑照跑（旧局回归兼容）。BttForceRoleCommand 已删（避免双根命令冲突）。
 */
@Mixin(Harpymodloader.class)
public abstract class BttHmlForceRoleMixin {

    @Inject(method = "addToForcedRoles", at = @At("TAIL"))
    private static void bttForce(Role role, PlayerEntity player, CallbackInfo ci) {
        BttIdentity.force(player.getUuid(), role);
    }
}
