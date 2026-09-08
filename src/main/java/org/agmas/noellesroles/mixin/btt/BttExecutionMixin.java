package org.agmas.noellesroles.mixin.btt;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.util.GunShootPayload;
import dev.doctor4t.wathe.util.Scheduler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.btt.BttRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 处决链路（C-048，复用原版扔枪机制，2026-09-07 用户指令）：
 * ① 外层条件 {@code 目标 isInnocent} → {@code 射手 isInnocent}（仅 BTT）——
 *    原版 4t 定时扔枪例程由此对"乘客枪击命中任何人"生效（处决+误杀），凶手枪击不再触发；
 * ② 包裹 Scheduler.schedule：保留原版扔枪（移除左轮→掉落→GunDropPayload→setMood(0)），
 *    随后把理智改为射击前值 −0.4（doc −40 取代清零）；义警/乘警不扔枪（docx 2026-09-07），
 *    义警另免理智惩罚（docx：无理智限制）→ 空任务；
 * ③ 尾部 CD 覆写：命中玩家的枪击=处决 → 60s（射空保持原生 10s）；顺带修复
 *    强盗 onKill 的 60s 曾被尾部 10s 覆写的 bug（统一在此计算）。
 */
@Mixin(GunShootPayload.Receiver.class)
public abstract class BttExecutionMixin {

    @WrapOperation(method = "receive", at = @At(value = "INVOKE",
            target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;isInnocent(Lnet/minecraft/entity/player/PlayerEntity;)Z"))
    private static boolean bttShooterBased(GameWorldComponent instance, PlayerEntity arg, Operation<Boolean> op,
                                           GunShootPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity shooter = context.player();
        if (!BttIdentity.isBttMode(shooter.getWorld())) return op.call(instance, arg);
        Entity victim = shooter.getWorld().getEntityById(payload.target());
        if (arg == victim) return instance.isInnocent(shooter);
        return op.call(instance, arg);
    }

    @WrapOperation(method = "receive", at = @At(value = "INVOKE",
            target = "Ldev/doctor4t/wathe/util/Scheduler;schedule(Ljava/lang/Runnable;I)Ldev/doctor4t/wathe/util/Scheduler$ScheduledTask;"))
    private static Scheduler.ScheduledTask bttExecutionDrop(Runnable task, int delay, Operation<Scheduler.ScheduledTask> op,
                                                            GunShootPayload payload, ServerPlayNetworking.Context context) {
        ServerPlayerEntity shooter = context.player();
        if (!BttIdentity.isBttMode(shooter.getWorld())) return op.call(task, delay);
        GameWorldComponent gwc = GameWorldComponent.KEY.get(shooter.getWorld());
        if (!gwc.isInnocent(shooter)) return op.call(task, delay);
        boolean noDrop = gwc.isRole(shooter, BttRoles.VIGILANTE) || gwc.isRole(shooter, BttRoles.RAILWAY_POLICE);
        boolean noMood = gwc.isRole(shooter, BttRoles.VIGILANTE);
        float pre = PlayerMoodComponent.KEY.get(shooter).getMood();
        Runnable wrapped = noDrop
                ? (noMood ? () -> { } : () -> PlayerMoodComponent.KEY.get(shooter).setMood(Math.max(0f, pre - 0.4f)))
                : () -> {
                    task.run();
                    PlayerMoodComponent.KEY.get(shooter).setMood(Math.max(0f, pre - 0.4f));
                };
        return op.call(wrapped, delay);
    }

    @WrapOperation(method = "receive", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/ItemCooldownManager;set(Lnet/minecraft/item/Item;I)V"))
    private static void bttExecutionCd(ItemCooldownManager instance, Item item, int ticks, Operation<Void> op,
                                       GunShootPayload payload, ServerPlayNetworking.Context context) {
        int adjusted = ticks;
        PlayerEntity shooter = context.player();
        if (BttIdentity.isBttMode(shooter.getWorld())
                && shooter.getWorld().getEntityById(payload.target()) instanceof PlayerEntity) {
            GameWorldComponent gwc = GameWorldComponent.KEY.get(shooter.getWorld());
            Role shooterRole = gwc.getRole(shooter);
            var faction = BttRoles.factionOf(shooterRole);
            if (gwc.isInnocent(shooter)) adjusted = 1200;                 // 处决（含误杀）= 60s
            else if (faction == BttRoles.Faction.OUTSIDER) adjusted = 1200; // 魔女等外人枪：doc 一分钟
            else if (shooterRole == BttRoles.BANDIT) adjusted = 1200;     // 强盗：冷却一分钟（docx 2026-09-07）
        }
        op.call(instance, item, adjusted);
    }
}
