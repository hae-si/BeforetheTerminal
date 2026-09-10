package org.agmas.noellesroles.client.mixin.btt;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.item.KnifeItem;
import dev.doctor4t.wathe.util.KnifeStabPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.btt.BttRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 清道夫匕首（doc：1 分钟冷却、**无蓄力**、无声）——**客户端** mixin：
 * wathe 的 KnifeItem.use 会进入蓄力并播放 ITEM_KNIFE_PREPARE 音效；清道夫改为**即时出手**——
 * HEAD 取消 use（不进入蓄力、不播音效），客户端直接射线 3 格并发 KnifeStabPayload。
 * 客户端取消后不会上行 use 包，服务端不会进入蓄力路径。
 * （命中音效由 BttCleanerSilentKnifeMixin 静音；未来单独做匕首纹理。）
 */
@Mixin(KnifeItem.class)
public abstract class BttCleanerKnifeMixin {

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void bttCleanerInstant(World world, PlayerEntity user, Hand hand,
                                   CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {
        if (!BttIdentity.isBttMode(world)) return;
        GameWorldComponent gwc = GameWorldComponent.KEY.get(world);
        if (!gwc.isRunning() || !gwc.isRole(user, BttRoles.CLEANER)) return;
        HitResult hit = KnifeItem.getKnifeTarget(user);
        if (hit instanceof EntityHitResult ehr) {
            ClientPlayNetworking.send(new KnifeStabPayload(ehr.getEntity().getId()));
        }
        cir.setReturnValue(TypedActionResult.success(user.getStackInHand(hand)));
        cir.cancel();
    }
}
