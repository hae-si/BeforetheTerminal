package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.agmas.noellesroles.AbilityPlayerComponent;
import org.agmas.noellesroles.voice.NoellesrolesVoiceChatPlugin;

import java.util.UUID;

/**
 * 饕餮 &lt;绑架&gt;（C-109）：G 键吞下身边者，被吞者**附身在饕餮身上**，直到理智归零或饕餮死亡后释放。
 * <p>
 * 用户 2026-09-11 研究结论（偷懒方案，**不碰 wathe 生死模型**）：NRS 的吞人 = 切旁观 + 相机接管，依赖
 * WatheSpark fork 才有的死亡注册表（`GameWorldComponent.markPlayerDead` + `GameFunctions.isPlayerPlayingAndAlive`，
 * 官方 1.3.2 不存在；详见 SYSTEM_SPEC §2）。BTT 改为全程保持被吞者 **survival**：
 * <ul>
 *   <li>原版 {@code setCameraEntity(饕餮)} = "附身"（用饕餮的眼睛看）；</li>
 *   <li>隐身（原版效果）+ {@code noClip = true}（public 字段；否则两个玩家实体互推会把饕餮顶得发抖）；</li>
 *   <li>逐 tick {@code requestTeleport(饕餮坐标)}（= wathe `limitPlayerToBox` 同款轻量位置包，不卡顿；顺带让
 *       被吞者的**语音位置**落在饕餮身上 → 胃袋分组里能听见饕餮周围的动静）；</li>
 *   <li>胃袋语音分组（{@link NoellesrolesVoiceChatPlugin}）= 被吞者之间互通、外部听不见他们；</li>
 *   <li>被吞期间 {@code AllowPlayerDeath} 否决死亡（"在肚子里"，防误伤/炸弹）。</li>
 * </ul>
 * 释放：饕餮死亡/离线（tick 自愈检测）或**被吞者理智归零**（wathe 需求未完成时每 tick 扣 MOOD_DRAIN = 1/80
 * → 满理智约 80 秒，天然有时限）。**不移植** NRS 的感染扩散/护盾联动/连环杀手通知/饕餮 Moment/"吞光全场"即时胜利
 * （docx 无；{饕餮尾声} 与 KIDNAPPER_WIN 已实装）。
 */
public final class BttKidnapper {
    private BttKidnapper() {}

    /** 吞人冷却（docx：冷却一分钟） */
    public static final int COOLDOWN_TICKS = GameConstants.getInTicks(1, 0);
    /** 身边者距离上限（BTT 身边者口径 6 格） */
    private static final double RANGE_SQUARED = 36.0;
    /** 隐身时长（长到无需续期；释放时移除） */
    private static final int INVISIBILITY_TICKS = Integer.MAX_VALUE / 4;

    /** G 键 &lt;绑架&gt;：吞下身边者（未命中/已在腹中 → 不扣冷却） */
    public static void swallow(ServerPlayerEntity user, ServerPlayerEntity target, BttPlayerComponent targetPc) {
        if (!GameFunctions.isPlayerAliveAndSurvival(user) || !GameFunctions.isPlayerAliveAndSurvival(target)) return;
        if (targetPc.isSwallowed()) return; // 已经在某个肚子里
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        if (user.squaredDistanceTo(target) > RANGE_SQUARED || !user.canSee(target)) return;
        ability.cooldown = COOLDOWN_TICKS;
        ability.sync();
        enter(user, target, targetPc);
        user.sendMessage(Text.literal("你吞下了 " + target.getName().getString() + "。")
                .withColor(BttRoles.KIDNAPPER.color()), true);
        target.sendMessage(Text.literal("你被吞进了黑暗里——附身于饕餮身上。")
                .withColor(BttRoles.KIDNAPPER.color()), true);
    }

    private static void enter(ServerPlayerEntity kidnapper, ServerPlayerEntity victim, BttPlayerComponent pc) {
        pc.setSwallowedBy(kidnapper.getUuidAsString());
        victim.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, INVISIBILITY_TICKS, 0, false, false, false));
        victim.noClip = true; // 不再推人（碰撞链读 noClip；穿地由逐 tick 传送兜住）
        victim.setCameraEntity(kidnapper);
        victim.requestTeleport(kidnapper.getX(), kidnapper.getY(), kidnapper.getZ());
        NoellesrolesVoiceChatPlugin.joinStomach(kidnapper, victim);
    }

    /** 每 tick（BttEvents 全局循环，按**被吞者**派发） */
    public static void tick(ServerPlayerEntity victim, BttPlayerComponent pc) {
        if (!pc.isSwallowed()) return;
        ServerPlayerEntity kidnapper = resolveKidnapper(victim, pc);
        // 释放条件：饕餮死亡/离线（tick 自愈）或 被吞者理智归零（docx「直到理智值归零」）
        if (kidnapper == null || !GameFunctions.isPlayerAliveAndSurvival(kidnapper)
                || dev.doctor4t.wathe.cca.PlayerMoodComponent.KEY.get(victim).getMood() <= 0.0F) {
            release(victim, pc, kidnapper);
            return;
        }
        // 跟随（轻量位置包，同 wathe limitPlayerToBox）+ 相机/隐身自愈
        victim.requestTeleport(kidnapper.getX(), kidnapper.getY(), kidnapper.getZ());
        if (victim.getCameraEntity() != kidnapper) victim.setCameraEntity(kidnapper);
        if (!victim.hasStatusEffect(StatusEffects.INVISIBILITY)) {
            victim.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, INVISIBILITY_TICKS, 0, false, false, false));
        }
        victim.noClip = true;
    }

    /** 出腹：一切复原（饕餮还在就落到他身上，否则就地） */
    public static void release(ServerPlayerEntity victim, BttPlayerComponent pc, ServerPlayerEntity kidnapper) {
        pc.clearSwallowed();
        victim.setCameraEntity(victim);
        victim.removeStatusEffect(StatusEffects.INVISIBILITY);
        victim.noClip = false;
        NoellesrolesVoiceChatPlugin.leaveStomach(victim);
        if (kidnapper != null && GameFunctions.isPlayerAliveAndSurvival(kidnapper)) {
            victim.requestTeleport(kidnapper.getX(), kidnapper.getY(), kidnapper.getZ());
        }
        victim.sendMessage(Text.literal("你被吐了出来。").withColor(BttRoles.KIDNAPPER.color()), true);
    }

    /** 回合结束兜底：释放全部被吞者（`finalizeGame` 调用） */
    public static void releaseAll(ServerWorld world) {
        for (ServerPlayerEntity p : world.getPlayers()) {
            BttPlayerComponent pc = BttPlayerComponent.KEY.get(p);
            if (pc.isSwallowed()) release(p, pc, resolveKidnapper(p, pc));
        }
    }

    private static ServerPlayerEntity resolveKidnapper(ServerPlayerEntity victim, BttPlayerComponent pc) {
        UUID uuid;
        try {
            uuid = UUID.fromString(pc.swallowedBy);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return victim.getServerWorld().getPlayerByUuid(uuid) instanceof ServerPlayerEntity s ? s : null;
    }

    /** 供外部（测试/未来技能）查询某玩家是否在腹中 */
    public static boolean isSwallowed(ServerPlayerEntity player) {
        return BttPlayerComponent.KEY.get(player).isSwallowed();
    }

}