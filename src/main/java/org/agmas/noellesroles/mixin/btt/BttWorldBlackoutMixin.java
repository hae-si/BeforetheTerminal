package org.agmas.noellesroles.mixin.btt;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.WorldBlackoutComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.agmas.noellesroles.btt.BttIdentity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.objectweb.asm.Opcodes;

/**
 * 熄灯（短路器 `useBlackout` → wathe {@link WorldBlackoutComponent}）BTT 口径（C-100）：
 * <p>
 * 用户 2026-09-11：「熄灯改为和 NRS 一样给失明，但只有 30 秒。」
 * <ul>
 *   <li><b>时长</b>：官方 {@code triggerBlackout} 用 {@code BLACKOUT_MIN_DURATION + rand(MAX-MIN)}
 *       （15–20 秒随机），这里把该方法的两个字段读取改写成 600 / 601 ⇒ 恒 600 ticks = **30 秒**；</li>
 *   <li><b>效果</b>：照 NRS（WatheSpark {@code WorldBlackoutComponent}）——凶手阵营（`canUseKillerFeatures`）
 *       得**夜视**，其余存活玩家得**失明**，时长 = 熄灯剩余时间（每 tick 刷新，自愈）；</li>
 *   <li><b>读秒</b>：每秒一次行动栏 `game.blackout.countdown`（BTT 已有该 lang 键；NRS 同款）；</li>
 *   <li><b>清理</b>：熄灯结束时移除两种效果；官方 {@code reset()} 只恢复灯、**不归零 ticks**
 *       （回合结束后残留会持续致盲），故 TAIL 补 `ticks = 0` + 清理。</li>
 * </ul>
 * 官方 1.3.2 无 WatheSpark 的 {@code BlackoutEffect} 事件（金酒免疫等属 fork-only），不移植。
 */
@Mixin(WorldBlackoutComponent.class)
public abstract class BttWorldBlackoutMixin {

    /** 熄灯时长 30 秒（用户 2026-09-11） */
    @Unique private static final int BTT_BLACKOUT_TICKS = 600;

    @Shadow @Final private World world;
    @Shadow private int ticks;
    /** 上次读秒播报的剩余秒数（避免每 tick 重发同一条行动栏消息） */
    @Unique private int bttLastCountdownSec = -1;
    @Unique private boolean bttEffectsActive = false;

    /** MIN → 600（该方法内两处读取同时改写） */
    @ModifyExpressionValue(method = "triggerBlackout",
            at = @At(value = "FIELD",
                    target = "Ldev/doctor4t/wathe/game/GameConstants;BLACKOUT_MIN_DURATION:I",
                    opcode = Opcodes.GETSTATIC))
    private int bttBlackoutMin(int original) {
        return BTT_BLACKOUT_TICKS;
    }

    /** MAX → 601 ⇒ 600 + rand(601-600) = 恒 600 */
    @ModifyExpressionValue(method = "triggerBlackout",
            at = @At(value = "FIELD",
                    target = "Ldev/doctor4t/wathe/game/GameConstants;BLACKOUT_MAX_DURATION:I",
                    opcode = Opcodes.GETSTATIC))
    private int bttBlackoutMax(int original) {
        return BTT_BLACKOUT_TICKS + 1;
    }

    @Inject(method = "serverTick", at = @At("TAIL"))
    private void bttBlackoutTick(CallbackInfo ci) {
        if (!(this.world instanceof ServerWorld sw)) return;
        if (!BttIdentity.isBttMode(sw)) return;
        if (this.ticks > 0) {
            this.bttEffectsActive = true;
            applyEffects(sw);
        } else if (this.bttEffectsActive) {
            this.bttEffectsActive = false;
            this.bttLastCountdownSec = -1;
            clearEffects(sw);
        }
    }

    @Inject(method = "reset", at = @At("TAIL"))
    private void bttBlackoutReset(CallbackInfo ci) {
        // 官方 reset() 不归零 ticks（回合结束残留 → 永久致盲）；BTT 补清并移除效果
        this.ticks = 0;
        this.bttLastCountdownSec = -1;
        this.bttEffectsActive = false;
        if (this.world instanceof ServerWorld sw && BttIdentity.isBttMode(sw)) clearEffects(sw);
    }

    /** 凶手夜视 / 其余失明（NRS 同款），时长 = 熄灯剩余 tick；另每秒播报读秒 */
    @Unique private void applyEffects(ServerWorld sw) {
        GameWorldComponent gwc = GameWorldComponent.KEY.get(sw);
        int seconds = (this.ticks + 19) / 20;
        Text countdown = Text.translatable("game.blackout.countdown", seconds).formatted(Formatting.RED);
        boolean announce = seconds != this.bttLastCountdownSec;
        if (announce) this.bttLastCountdownSec = seconds;
        for (ServerPlayerEntity p : sw.getPlayers()) {
            if (!GameFunctions.isPlayerAliveAndSurvival(p)) continue;
            if (gwc.canUseKillerFeatures(p)) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, this.ticks, 0, false, false, true));
            } else {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, this.ticks, 0, false, false, true));
            }
            if (announce) p.sendMessage(countdown, true);
        }
    }

    @Unique private void clearEffects(ServerWorld sw) {
        for (ServerPlayerEntity p : sw.getPlayers()) {
            p.removeStatusEffect(StatusEffects.NIGHT_VISION);
            p.removeStatusEffect(StatusEffects.BLINDNESS);
        }
    }
}
