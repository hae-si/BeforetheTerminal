package org.agmas.noellesroles.mixin.btt;

import dev.doctor4t.wathe.index.WatheSounds;
import dev.doctor4t.wathe.util.KnifeStabPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.btt.BttRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/**
 * 清道夫：无声刀（doc"无蓄力、无声"）——BTT 局内清道夫的刀击杀不播放刺杀音效。
 * （蓄力：WATHE-API-001 §21 已证原版刀本身即时发送、无 1 秒蓄力，"删蓄力"条款自然满足。）
 * 改锥（250 兑换破坏掉枪）= BT-ITEM-SET TODO。
 */
@Mixin(KnifeStabPayload.Receiver.class)
public abstract class BttCleanerSilentKnifeMixin {

    @WrapOperation(method = "receive", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/PlayerEntity;playSound(Lnet/minecraft/sound/SoundEvent;FF)V"))
    private void bttSilentStab(PlayerEntity target, SoundEvent sound, float volume, float pitch, Operation<Void> original,
                               KnifeStabPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity killer = context.player();
        if (BttIdentity.isBttMode(killer.getWorld())
                && dev.doctor4t.wathe.cca.GameWorldComponent.KEY.get(killer.getWorld()).isRole(killer, BttRoles.CLEANER)
                && sound == WatheSounds.ITEM_KNIFE_STAB) {
            return; // 清道夫静音
        }
        original.call(target, sound, volume, pitch);
    }
}
