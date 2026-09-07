package org.agmas.noellesroles.mixin.btt;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.btt.BttRoleDef;
import org.agmas.noellesroles.btt.BttRoleDefs;
import org.agmas.noellesroles.btt.BttState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BTT kill hook（注入点同 NR：killPlayer 内 setHeadYaw invoke）。
 * 结构（BT-ARCH-001）：
 * ① 全局前置：杀人历史（侦探<调查>）+ 祭品协议复位（BttSerialKillerKnifeMixin 消费瞬态标记）；
 * ② 声明式 per-role 钩子（BttRoleDefs：老兵/巫觋/强盗/精神病人/连环杀手）；
 * ③ 全局后置：处决规则（乘客枪杀：理智−0.4+误杀全员+100；掉枪在 BttGunDropMixin）。
 * （+1min 触发 = 每有一名乘客死亡，由 wathe TIME_ON_CIVILIAN_KILL 原生承担。）
 */
@Mixin(GameFunctions.class)
public abstract class BttKillHookMixin {

    @Inject(method = "killPlayer(Lnet/minecraft/entity/player/PlayerEntity;ZLnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Identifier;)V",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/entity/PlayerBodyEntity;setHeadYaw(F)V"))
    private static void bttKillHook(PlayerEntity victim, boolean spawnBody, PlayerEntity killer, Identifier identifier, CallbackInfo ci) {
        if (!BttIdentity.isBttMode(victim.getWorld())) return;
        if (!(killer instanceof ServerPlayerEntity shooter)) return;
        if (!(victim.getWorld() instanceof net.minecraft.server.world.ServerWorld world)) return;
        GameWorldComponent gwc = GameWorldComponent.KEY.get(world);

        // === ① 全局：杀人历史（任何击杀均记录） ===
        BttState.setInt(shooter.getUuid(), "hasKilled", 1);

        // === ① 全局：独行中立被杀加钱（2026-09-06 策划修订：+100 狂气，同乘客口径） ===
        if (gwc.getRole(victim) != null
                && org.agmas.noellesroles.btt.BttRoles.LONE_NEUTRALS.contains(gwc.getRole(victim))) {
            dev.doctor4t.wathe.cca.PlayerShopComponent.KEY.get(shooter).addToBalance(100);
        }

        // === ① 全局：明星死亡通知仅乘客（doc：中立/凶手/外人不收；P2A-002） ===
        if (gwc.isRole(victim, org.agmas.noellesroles.btt.BttRoles.STAR)) {
            for (ServerPlayerEntity p : world.getPlayers()) {
                if (gwc.isInnocent(p)) {
                    p.sendMessage(net.minecraft.text.Text.literal("明星死亡了！").formatted(net.minecraft.util.Formatting.GOLD), true);
                }
            }
        }

        // === ② 声明式 per-role 击杀钩子 ===
        BttRoleDef d = BttRoleDefs.get(gwc.getRole(shooter));
        if (d != null) {
            d.dispatchKill(shooter, victim, identifier, gwc);
        }

        // === ③ 全局：处决（乘客用枪杀人）===
        if (!gwc.isInnocent(shooter)) return;
        if (identifier != GameConstants.DeathReasons.GUN) return;

        // 理智 −40
        var mood = dev.doctor4t.wathe.cca.PlayerMoodComponent.KEY.get(shooter);
        mood.setMood(Math.max(0f, mood.getMood() - 0.4f));

        // 掉落枪 → 由 BttGunDropMixin 在 GunShootPayload.Receiver 层处理（更可靠）

        // 误杀（处决杀死乘客）→ 所有凶手 +100 狂气
        if (gwc.isInnocent(victim)) {
            for (ServerPlayerEntity p : world.getPlayers()) {
                if (gwc.canUseKillerFeatures(p)) {
                    dev.doctor4t.wathe.cca.PlayerShopComponent.KEY.get(p).addToBalance(100);
                }
            }
        }
    }
}
