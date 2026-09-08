package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.cca.PlayerPoisonComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.agmas.noellesroles.AbilityPlayerComponent;

import java.util.ArrayList;

/**
 * BT-P2-UI 服务端分派：预言家/刺客/小说家/魔术师/舞蛇人 共用选人 UI 的语义。
 * 冷却载体（CLEAN-004 约定的例外）：本批身份用 NR {@link AbilityPlayerComponent}（自动同步，客户端 UI 显示倒计时）。
 * 全部走 BTT 门控 + running 门控；猜测比较 = role path（NR Guesser 同口径）。
 */
public final class BttGuessReceiver {
    private BttGuessReceiver() {}

    /** 小说家独胜：猜对过半 */
    static final int NOVELIST_INITIAL_CD = 0;

    public static void register() {
        PayloadTypeRegistry.playC2S().register(BttGuessC2SPacket.ID, BttGuessC2SPacket.CODEC);
        PayloadTypeRegistry.playC2S().register(BttCorpseActionC2SPacket.ID, BttCorpseActionC2SPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BttCorpseActionC2SPacket.ID, (payload, context) -> {
            ServerPlayerEntity user = context.player();
            if (!BttIdentity.isBttMode(user.getWorld())) return;
            GameWorldComponent gwc = GameWorldComponent.KEY.get(user.getWorld());
            if (!gwc.isRunning()) return;
            // 醉酒：技能失效（不自知）
            if (BttState.isDrunk(user.getUuid())) return;
            if (!gwc.isRole(user, BttRoles.AMNESIAC)) return;
            if (!(user.getServerWorld().getEntity(payload.body()) instanceof dev.doctor4t.wathe.entity.PlayerBodyEntity body)) return;
            if (BttState.getInt(body.getUuid(), "corpseUsed") == 1) return;
            Role dead = gwc.getRole(body.getPlayerUuid());
            if (dead == null) return;
            BttState.setInt(body.getUuid(), "corpseUsed", 1);
            BttRoleDef d = BttRoleDefs.get(dead);
            if (d != null) d.dispatchKit(user);
            user.sendMessage(Text.literal("你取回了 "
                    + BttIdentity.displayName(dead).getString() + " 的遗物。")
                    .formatted(Formatting.LIGHT_PURPLE), true);
        });
        ServerPlayNetworking.registerGlobalReceiver(BttGuessC2SPacket.ID, (payload, context) -> {
            ServerPlayerEntity user = context.player();
            if (!BttIdentity.isBttMode(user.getWorld())) return;
            GameWorldComponent gwc = GameWorldComponent.KEY.get(user.getWorld());
            if (!gwc.isRunning()) return;
            // 醉酒：技能失效——无效果、不提示（不自知，BT-SYS-DRUNK）
            if (BttState.isDrunk(user.getUuid())) return;
            // 吟游诗人：<歌唱> 无需目标（G 键直发），全场醉酒
            if (gwc.isRole(user, BttRoles.MINSTREL)) {
                minstrel(user);
                return;
            }
            if (!(user.getServerWorld().getPlayerByUuid(payload.target()) instanceof ServerPlayerEntity target)) return;
            if (target == user) return;
            Role guessed = gwc.getRole(target);

            if (gwc.isRole(user, BttRoles.PROPHET)) {
                prophet(user, target, gwc, payload, guessed);
            } else if (gwc.isRole(user, BttRoles.NOVELIST)) {
                novelist(user, target, gwc, payload, guessed);
            } else if (gwc.isRole(user, BttRoles.HUNTER)) {
                hunter(user, target, gwc);
            } else if (gwc.isRole(user, BttRoles.MESSIAH)) {
                messiah(user, target, gwc, payload);
            } else if (gwc.isRole(user, BttRoles.BARTENDER)) {
                bartender(user, target);
            } else if (gwc.isRole(user, BttRoles.SMUGGLER)) {
                smuggler(user, target);
            } else if (gwc.isRole(user, BttRoles.DRUG_MAKER)) {
                drugMaker(user, target);
            } else if (gwc.isRole(user, BttRoles.SNAKE_CHARMER)) {
                snakeCharmer(user, target, gwc);
            }
        });
    }

    // ===== 预言家：<猜测> 玩家身份，猜错即死亡；CD 60s =====

    private static void prophet(ServerPlayerEntity user, ServerPlayerEntity target, GameWorldComponent gwc,
                                BttGuessC2SPacket payload, Role guessed) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        if (guessed != null && guessed.identifier().getPath().equalsIgnoreCase(payload.guess())) {
            user.sendMessage(Text.literal("猜测正确：" + target.getName().getString() + " 是 "
                    + BttIdentity.displayName(guessed).getString()).formatted(Formatting.GOLD), true);
            setCd(ability, GameConstants.getInTicks(1, 0));
        } else {
            user.sendMessage(Text.literal("你猜错了。").formatted(Formatting.RED), true);
            GameFunctions.killPlayer(user, true, null, BttDeathReasons.PROPHECY_INTERRUPTED);
        }
    }

    // ===== 小说家：<猜测> 任何人身份；对→广播可继续，错→CD 30s；猜对过半→独胜 =====

    private static void novelist(ServerPlayerEntity user, ServerPlayerEntity target, GameWorldComponent gwc,
                                 BttGuessC2SPacket payload, Role guessed) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        if (guessed != null && guessed.identifier().getPath().equalsIgnoreCase(payload.guess())) {
            int hits = BttState.getInt(user.getUuid(), "novelistHits") + 1;
            BttState.setInt(user.getUuid(), "novelistHits", hits);
            // doc 口径文案（用户指定 2026-09-05）；猜错不播报
            broadcast(user, Text.literal("小说家进行了正确的猜测！").formatted(Formatting.LIGHT_PURPLE));
            // 独胜判定前移到 receiver（2026-09-07 用户指令，与窃贼 BttWatheVultureThiefMixin 同模式）：
            // 猜对过半 → 立即写结局并 stopGame，不再等 GameMode tick
            if (hits * 2 >= user.getServerWorld().getPlayers().size()) {
                BttGameWorldComponent btt = BttGameWorldComponent.KEY.get(user.getWorld());
                btt.lastEnding = BttEndings.Ending.NOVELIST_WIN.name();
                btt.winners = user.getUuid().toString();
                btt.sync();
                // 与 GameMode 终局路径同构：per-role 结局数据 + stopGame（独胜 WinStatus=NONE）
                dev.doctor4t.wathe.cca.GameRoundEndComponent.KEY.get(user.getServerWorld())
                        .setRoundEndData(new ArrayList<>(user.getServerWorld().getPlayers()),
                                GameFunctions.WinStatus.NONE);
                GameFunctions.stopGame(user.getServerWorld());
            }
        } else {
            setCd(ability, GameConstants.getInTicks(0, 30));
        }
    }

    // ===== 救世主：<预知> 任何人身份（CD 2min 含初始）；对→目标入教团；错→救世主身份暴露 =====

    private static void messiah(ServerPlayerEntity user, ServerPlayerEntity target, GameWorldComponent gwc,
                                BttGuessC2SPacket payload) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        setCd(ability, GameConstants.getInTicks(2, 0));
        Role guessed = gwc.getRole(target);
        if (guessed != null && guessed.identifier().getPath().equalsIgnoreCase(payload.guess())) {
            BttState.setInt(target.getUuid(), "cult", 1);
            broadcast(user, Text.literal(target.getName().getString() + " 已成为教团信徒！")
                    .formatted(Formatting.DARK_PURPLE));
            target.sendMessage(Text.literal("你成为了教团信徒（教团可互相透视）。")
                    .formatted(Formatting.LIGHT_PURPLE), true);
        } else {
            broadcast(user, Text.literal("救世主是 " + user.getName().getString() + "！")
                    .formatted(Formatting.DARK_RED));
        }
    }

    // ===== 酒保：<灌酒> 身边者醉酒 1 分钟，CD 1 分钟（docx 2026-09-07） =====

    private static void bartender(ServerPlayerEntity user, ServerPlayerEntity target) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        if (user.distanceTo(target) > 6) {
            user.sendMessage(Text.literal("目标不在身边。").formatted(Formatting.RED), true);
            return;
        }
        setCd(ability, GameConstants.getInTicks(1, 0));
        BttState.applyDrunk(target.getUuid(), GameConstants.getInTicks(1, 0));
        user.sendMessage(Text.literal("灌酒成功。").formatted(Formatting.BLUE), true);
    }

    // ===== 吟游诗人：<歌唱> 全场醉酒 1 分钟，CD 2 分钟（docx 2026-09-07） =====

    private static void minstrel(ServerPlayerEntity user) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        setCd(ability, GameConstants.getInTicks(2, 0));
        for (ServerPlayerEntity p : user.getServerWorld().getPlayers()) {
            BttState.applyDrunk(p.getUuid(), GameConstants.getInTicks(1, 0));
        }
        user.sendMessage(Text.literal("你唱起了一支歌……").formatted(Formatting.LIGHT_PURPLE), true);
    }

    // ===== 走私犯：<灌酒> 身边者醉酒 1 分钟，CD 30 秒（标记机制 GAP，简化为直接灌酒） =====

    private static void smuggler(ServerPlayerEntity user, ServerPlayerEntity target) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        if (user.distanceTo(target) > 6) {
            user.sendMessage(Text.literal("目标不在身边。").formatted(Formatting.RED), true);
            return;
        }
        setCd(ability, GameConstants.getInTicks(0, 30));
        BttState.applyDrunk(target.getUuid(), GameConstants.getInTicks(1, 0));
        user.sendMessage(Text.literal("灌酒成功。").formatted(Formatting.BLUE), true);
    }

    // ===== 毒师：<下药> 身边者醉酒+中毒 1 分钟，CD 30 秒（标记机制 GAP，简化为直接下药） =====

    private static void drugMaker(ServerPlayerEntity user, ServerPlayerEntity target) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        if (user.distanceTo(target) > 6) {
            user.sendMessage(Text.literal("目标不在身边。").formatted(Formatting.RED), true);
            return;
        }
        setCd(ability, GameConstants.getInTicks(0, 30));
        BttState.applyDrunk(target.getUuid(), GameConstants.getInTicks(1, 0));
        PlayerPoisonComponent.KEY.get(target).setPoisonTicks(GameConstants.getInTicks(1, 0), user.getUuid());
        user.sendMessage(Text.literal("下药成功。").formatted(Formatting.DARK_GREEN), true);
    }

    // ===== 侦探：<调查> 身边者（选人），CD 60s =====

    private static void detective(ServerPlayerEntity user, ServerPlayerEntity target, GameWorldComponent gwc) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        setCd(ability, GameConstants.getInTicks(1, 0));
        boolean killed = BttState.getInt(target.getUuid(), "hasKilled") > 0;
        user.sendMessage(Text.literal(target.getName().getString()
                + (killed ? " 曾经杀过人" : " 没有杀过人")).formatted(killed ? Formatting.RED : Formatting.GREEN), true);
    }

    // ===== 绳艺师：<拘束> 目标 15s，CD 60s =====

    private static void rigger(ServerPlayerEntity user, ServerPlayerEntity target) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        setCd(ability, GameConstants.getInTicks(1, 0));
        target.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                net.minecraft.entity.effect.StatusEffects.SLOWNESS, GameConstants.getInTicks(0, 15), 250, false, true));
    }

    // ===== 药剂师：<喂药> 解毒；健康人回满理智（docx 2026-09-07），CD 60s =====

    private static void candy(ServerPlayerEntity user, ServerPlayerEntity target) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        setCd(ability, GameConstants.getInTicks(1, 0));
        PlayerPoisonComponent poison = PlayerPoisonComponent.KEY.get(target);
        if (poison.poisonTicks > 0) poison.reset();
        else PlayerMoodComponent.KEY.get(target).setMood(1.0f);
    }

    // ===== 猎人：仅限一次 <狙击>——目标为主犯则死亡，否则无效（揭示与否=作者确认 TODO）；UI instant 复用 =====

    private static void hunter(ServerPlayerEntity user, ServerPlayerEntity target, GameWorldComponent gwc) {
        if (BttState.getInt(user.getUuid(), "hunterShot") == 1) {
            user.sendMessage(Text.literal("你已经用过狙击了。").formatted(Formatting.RED), true);
            return;
        }
        BttState.setInt(user.getUuid(), "hunterShot", 1);
        // 一次性：此后选人件长期灰显（AbilityPlayerComponent 大 CD）
        AbilityPlayerComponent huntAbility = AbilityPlayerComponent.KEY.get(user);
        huntAbility.setCooldown(20 * 60 * 60);
        huntAbility.sync();
        Role targetRole = gwc.getRole(target);
        if (targetRole != null && BttRoles.factionOf(targetRole) == BttRoles.Faction.PRINCIPAL) {
            broadcast(user, Text.literal("猎人狙击成功了！").formatted(Formatting.GREEN));
            // 能杀死人而非枪击：用狙击魔法死因（doc 死因表），避开处决链
            GameFunctions.killPlayer(target, true, user, BttDeathReasons.SNIPE_MAGIC);
        } else {
            user.sendMessage(Text.literal("狙击落空。").formatted(Formatting.RED), true);
        }
    }

    // ===== 舞蛇人：控诉主犯；对→身份阵营互换+新舞蛇人中毒；错→CD 60s =====

    private static void snakeCharmer(ServerPlayerEntity user, ServerPlayerEntity target, GameWorldComponent gwc) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        setCd(ability, GameConstants.getInTicks(1, 0));
        Role targetRole = gwc.getRole(target);
        if (targetRole != null && BttRoles.factionOf(targetRole) == BttRoles.Faction.PRINCIPAL) {
            Role mine = gwc.getRole(user);
            gwc.addRole(user.getUuid(), targetRole);
            gwc.addRole(target.getUuid(), mine);
            gwc.sync();
            // 新舞蛇人（原主犯）中毒（doc）
            PlayerPoisonComponent.KEY.get(target).setPoisonTicks(1000, user.getUuid());
            broadcast(user, Text.literal("舞蛇人识破了主犯！两人身份互换——"
                    + target.getName().getString() + " 成为了新的舞蛇人（且已中毒）。").formatted(Formatting.DARK_PURPLE));
        } else {
            user.sendMessage(Text.literal("他不是主犯。").formatted(Formatting.RED), true);
        }
    }

    // ===== 通用 =====

    private static void setCd(AbilityPlayerComponent ability, int ticks) {
        ability.setCooldown(ticks);
        ability.sync();
    }

    private static void broadcast(ServerPlayerEntity any, Text text) {
        for (ServerPlayerEntity p : any.getServerWorld().getPlayers()) {
            p.sendMessage(text, true);
        }
    }
}
