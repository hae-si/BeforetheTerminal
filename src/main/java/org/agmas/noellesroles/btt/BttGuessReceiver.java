package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
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
            if (!(user.getServerWorld().getPlayerByUuid(payload.target()) instanceof ServerPlayerEntity target)) return;
            if (target == user) return;
            Role guessed = gwc.getRole(target);

            if (gwc.isRole(user, BttRoles.PROPHET)) {
                prophet(user, target, gwc, payload, guessed);
            } else if (gwc.isRole(user, BttRoles.NOVELIST)) {
                novelist(user, target, gwc, payload, guessed);
            } else if (gwc.isRole(user, BttRoles.HUNTER)) {
                hunter(user, target, gwc);
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
            GameFunctions.killPlayer(user, true, null, GameConstants.DeathReasons.KNIFE);
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
        } else {
            setCd(ability, GameConstants.getInTicks(0, 30));
        }
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

    // ===== 卖糖人：<给糖> 解毒，CD 60s =====

    private static void candy(ServerPlayerEntity user, ServerPlayerEntity target) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        setCd(ability, GameConstants.getInTicks(1, 0));
        PlayerPoisonComponent poison = PlayerPoisonComponent.KEY.get(target);
        if (poison.poisonTicks > 0) poison.reset();
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
            // 能杀死人而非枪击：用 KNIFE 口径避免触发处决链（doc 狙击=授权能力）
            GameFunctions.killPlayer(target, true, user, GameConstants.DeathReasons.KNIFE);
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
