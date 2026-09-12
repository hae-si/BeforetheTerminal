package org.agmas.noellesroles.mixin.btt;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.btt.BttDeathReasons;
import org.agmas.noellesroles.btt.BttGuard;
import org.agmas.noellesroles.btt.BttIdentity;
import java.util.ArrayList;
import org.agmas.noellesroles.btt.BttPlayerComponent;
import org.agmas.noellesroles.btt.BttRoleDef;
import org.agmas.noellesroles.btt.BttRoleDefs;
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

    /**
     * 明星枪免（C-133 从 {@code AllowPlayerDeath} 迁到 killPlayer HEAD）：
     * <p>① **顺序无关**——HEAD 先于所有死亡监听，不会被其它免死/否决监听抢先吞掉；
     * ② **碎盾声必定可闻**——开枪距离 65 格 > 原版声音衰减 16 格，原实现只在尸体处 `playSound`，
     * 远处开枪者听不到（用户 2026-09-13"明星：被处决没有碎盾声"）。改为**定向**播放：
     * 明星本人 + 开枪者 + 16 格内旁观者各听一次（无重复播放）。
     */
    @Inject(method = "killPlayer(Lnet/minecraft/entity/player/PlayerEntity;ZLnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Identifier;)V",
            at = @At("HEAD"), cancellable = true)
    private static void bttStarShield(PlayerEntity victim, boolean spawnBody, PlayerEntity killer,
                                      Identifier reason, CallbackInfo ci) {
        if (!BttIdentity.isBttMode(victim.getWorld())) return;
        if (reason != GameConstants.DeathReasons.GUN) return;
        if (!(victim instanceof ServerPlayerEntity star)) return;
        GameWorldComponent gwc = GameWorldComponent.KEY.get(victim.getWorld());
        if (!gwc.isRole(victim, org.agmas.noellesroles.btt.BttRoles.STAR)) return;
        // 暴乱存活 → 明星枪免失效（处决明星也应死亡；docx 2026-09-10）
        for (PlayerEntity p : victim.getWorld().getPlayers()) {
            if (gwc.isRole(p, org.agmas.noellesroles.btt.BttRoles.RIOT)
                    && GameFunctions.isPlayerAliveAndSurvival(p)) return;
        }
        net.minecraft.sound.SoundEvent sfx = net.minecraft.sound.SoundEvents.ITEM_SHIELD_BREAK;
        star.playSoundToPlayer(sfx, net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
        if (killer != null && killer != star) {
            killer.playSoundToPlayer(sfx, net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
        }
        for (ServerPlayerEntity bystander : star.getServerWorld().getPlayers()) {
            if (bystander == star || bystander == killer) continue;
            if (bystander.getBlockPos().getSquaredDistance(star.getBlockPos()) <= 256) {
                bystander.playSoundToPlayer(sfx, net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
            }
        }
        ci.cancel(); // 不受伤
    }

    /**
     * 黑死病附身链（C-134）：病人（宿主）被杀 → 病原体转移到击杀者；无击杀者死亡（跳车/环境/崩溃）→ 黑死病终结。
     * 病人本体照常死亡（不 cancel）——附身的躯体只是承载物。
     */
    @Inject(method = "killPlayer(Lnet/minecraft/entity/player/PlayerEntity;ZLnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Identifier;)V",
            at = @At("HEAD"))
    private static void bttBlackdeathPatient(PlayerEntity victim, boolean spawnBody, PlayerEntity killer,
                                             Identifier reason, CallbackInfo ci) {
        if (!BttIdentity.isBttMode(victim.getWorld())) return;
        if (!(victim instanceof ServerPlayerEntity patient)) return;
        if (!org.agmas.noellesroles.btt.BttPlayerComponent.KEY.get(patient).blackdeathPatient) return;
        org.agmas.noellesroles.btt.BttBlackdeath.onPatientDeath(patient, killer);
    }

    @Inject(method = "killPlayer(Lnet/minecraft/entity/player/PlayerEntity;ZLnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Identifier;)V",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/entity/PlayerBodyEntity;setHeadYaw(F)V"))
    private static void bttKillHook(PlayerEntity victim, boolean spawnBody, PlayerEntity killer, Identifier identifier, CallbackInfo ci) {
        if (!BttIdentity.isBttMode(victim.getWorld())) return;
        // === ① 全局：救世主死亡 → 信徒集体殉教（docx 死因"殉教"；任意死因触发，先于 killer 判空） ===
        if (victim.getWorld() instanceof net.minecraft.server.world.ServerWorld sw0) {
            GameWorldComponent gwc0 = GameWorldComponent.KEY.get(sw0);
            if (gwc0.isRole(victim, org.agmas.noellesroles.btt.BttRoles.MESSIAH)) {
                for (ServerPlayerEntity p : sw0.getPlayers()) {
                    if (p == victim) continue;
                    if (!GameFunctions.isPlayerAliveAndSurvival(p)) continue;
                    if (BttPlayerComponent.KEY.get(p).isCult()) {
                        GameFunctions.killPlayer(p, true, victim, BttDeathReasons.MARTYRDOM);
                    }
                }
            }
        }
        if (!(killer instanceof ServerPlayerEntity shooter)) return;
        if (!(victim.getWorld() instanceof net.minecraft.server.world.ServerWorld world)) return;
        GameWorldComponent gwc = GameWorldComponent.KEY.get(world);

        // === ① 全局：杀人历史（任何击杀均记录） ===
        BttPlayerComponent.KEY.get(shooter).hasKilled = 1;

        // === ① 全局：保镖死亡带走被守护者 / 宿主死亡解除寄生（C-117） ===
        if (victim instanceof ServerPlayerEntity serverVictim) {
            BttGuard.onDeath(serverVictim, world, gwc);
        }

        // === ① 全局：独行中立被杀加钱（2026-09-06 策划修订：+100 狂气，同乘客口径） ===
        if (gwc.getRole(victim) != null
                && org.agmas.noellesroles.btt.BttRoles.LONE_NEUTRALS.contains(gwc.getRole(victim))) {
            dev.doctor4t.wathe.cca.PlayerShopComponent.KEY.get(shooter).addToBalance(100);
        }

        // === ① 全局：明星死亡通知仅乘客（doc：中立/凶手/外人不收；P2A-002） ===
        if (gwc.isRole(victim, org.agmas.noellesroles.btt.BttRoles.STAR)) {
            // C-128：明星死亡音（在尸体处）
            world.playSound(null, victim.getBlockPos(), org.agmas.noellesroles.btt.BttSounds.STAR_DEATH,
                    net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
            for (ServerPlayerEntity p : world.getPlayers()) {
                if (gwc.isInnocent(p)) {
                    // C-098：身份通知（动作栏）用明星的身份色
                    p.sendMessage(net.minecraft.text.Text.translatable("noellesroles.btt.action.star.died")
                            .withColor(org.agmas.noellesroles.btt.BttRoles.STAR.color()), true);
                }
            }
        }

        // === ① 全局：处子死亡音（C-128；处子死亡本就会让全体透视其尸体 1 分钟） ===
        if (gwc.isRole(victim, org.agmas.noellesroles.btt.BttRoles.VIRGIN)) {
            world.playSound(null, victim.getBlockPos(), org.agmas.noellesroles.btt.BttSounds.VIRGIN_DEATH,
                    net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
        }

        // === ① 全局：恋人殉情（C-061；doc 死因"殉情"） ===
        java.util.UUID lover = org.agmas.noellesroles.btt.BttRelationships.partnerOf(victim);
        if (lover != null && org.agmas.noellesroles.btt.BttRelationships.isLover(victim)) {
            ServerPlayerEntity partner = (ServerPlayerEntity) world.getPlayerByUuid(lover);
            if (partner != null && GameFunctions.isPlayerAliveAndSurvival(partner)) {
                GameFunctions.killPlayer(partner, true, victim, BttDeathReasons.LOVER_SUICIDE);
            }
        }

        // === ① 全局：宿敌（C-064）：宿敌乘客被处决 → 宿敌凶手单独胜利（docx 2026-09-09） ===
        if ("ARCHENEMY".equals(org.agmas.noellesroles.btt.BttRelationships.typeOf(victim))
                && org.agmas.noellesroles.btt.BttRoles.factionOf(gwc.getRole(victim)) == org.agmas.noellesroles.btt.BttRoles.Faction.CIVILIAN
                && identifier == GameConstants.DeathReasons.GUN
                && gwc.isInnocent(shooter)) {
            java.util.UUID archenemy = org.agmas.noellesroles.btt.BttRelationships.partnerOf(victim);
            ServerPlayerEntity archenemyPlayer = archenemy == null ? null : (ServerPlayerEntity) world.getPlayerByUuid(archenemy);
            if (archenemyPlayer != null) {
                var btt = org.agmas.noellesroles.btt.BttGameWorldComponent.KEY.get(world);
                btt.lastEnding = org.agmas.noellesroles.btt.BttEndings.Ending.ARCHENEMY_WIN.name();
                btt.winners = archenemyPlayer.getUuid().toString();
                btt.sync();
                dev.doctor4t.wathe.cca.GameRoundEndComponent.KEY.get(world).setRoundEndData(
                        world.getPlayers().stream().filter(p -> gwc.getRole(p) != null)
                                .collect(java.util.stream.Collectors.toList()), GameFunctions.WinStatus.NONE);
                GameFunctions.stopGame(world);
                return;
            }
        }

        // === ① 全局：宿敌护盾（v2 已删除，改为单独胜利） ===

        // === ② 声明式 per-role 击杀钩子 ===
        BttRoleDef d = BttRoleDefs.get(gwc.getRole(shooter));
        if (d != null) {
            d.dispatchKill(shooter, victim, identifier, gwc);
        }

        // === ③ 全局：处决（乘客用枪杀人）===
        if (!gwc.isInnocent(shooter)) return;
        if (identifier != GameConstants.DeathReasons.GUN) return;
        if (shooter == victim) return; // 自裁结不再嵌套

        // 理智 −40
        var mood = dev.doctor4t.wathe.cca.PlayerMoodComponent.KEY.get(shooter);
        mood.setMood(Math.max(0f, mood.getMood() - 0.4f));

        // 暴乱存活判定（C-063）
        boolean riotAlive = false;
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (gwc.isRole(p, org.agmas.noellesroles.btt.BttRoles.RIOT) && GameFunctions.isPlayerAliveAndSurvival(p)) {
                riotAlive = true;
                break;
            }
        }

        boolean misfire = gwc.isInnocent(victim);
        boolean knight = gwc.isRole(shooter, org.agmas.noellesroles.btt.BttRoles.CABALLERO);
        boolean ranger = gwc.isRole(shooter, org.agmas.noellesroles.btt.BttRoles.RANGER);
        if (misfire) {
            // 误杀：暴乱/骑士 → 射手自裁；暴乱不给凶手狂气，骑士误杀仍按误杀规则给
            if (riotAlive || knight) {
                GameFunctions.killPlayer(shooter, true, shooter, BttDeathReasons.SELF_EXECUTION);
            }
            if (!riotAlive) {
                // 误杀计数（小丑护盾公式用）
                org.agmas.noellesroles.btt.BttGameWorldComponent.KEY.get(world).misfireCount++;
                for (ServerPlayerEntity p : world.getPlayers()) {
                    if (gwc.canUseKillerFeatures(p)) {
                        dev.doctor4t.wathe.cca.PlayerShopComponent.KEY.get(p).addToBalance(100);
                    }
                }
            }
        } else {
            // 非误杀处决：游侠自裁；暴乱存活 → 所有凶手 +100
            if (ranger) {
                GameFunctions.killPlayer(shooter, true, shooter, BttDeathReasons.SELF_EXECUTION);
            } else if (riotAlive) {
                for (ServerPlayerEntity p : world.getPlayers()) {
                    if (gwc.canUseKillerFeatures(p)) {
                        dev.doctor4t.wathe.cca.PlayerShopComponent.KEY.get(p).addToBalance(100);
                    }
                }
            }
        }
    }
}
