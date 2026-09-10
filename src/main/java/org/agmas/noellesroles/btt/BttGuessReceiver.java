package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.cca.PlayerPoisonComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.agmas.noellesroles.AbilityPlayerComponent;

import java.util.ArrayList;

/**
 * BT-P2-UI 服务端分派：预言家/刺客/小说家/魔术师/舞蛇人/猎人/救世主/酒保/走私犯/侦探/绳艺师/药剂师 共用选人 UI 的语义。
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
        BttArchitect.register();
        ServerPlayNetworking.registerGlobalReceiver(BttCorpseActionC2SPacket.ID, (payload, context) -> {
            ServerPlayerEntity user = context.player();
            if (!BttIdentity.isBttMode(user.getWorld())) return;
            GameWorldComponent gwc = GameWorldComponent.KEY.get(user.getWorld());
            if (!gwc.isRunning()) return;
            // 醉酒：技能失效（不自知）
            if (BttPlayerComponent.KEY.get(user).isDrunk()) return;
            if (!gwc.isRole(user, BttRoles.AMNESIAC)) return;
            if (!(user.getServerWorld().getEntity(payload.body()) instanceof dev.doctor4t.wathe.entity.PlayerBodyEntity body)) return;
            if (BttBodyComponent.KEY.get(body).isAmnesiacUsed()) return;
            Role dead = gwc.getRole(body.getPlayerUuid());
            if (dead == null) return;
            BttBodyComponent.KEY.get(body).markAmnesiacUsed();
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
            // 炸弹传递（持有者 G 键对准他人；非技能，不受醉酒影响）
            if (BttPlayerComponent.KEY.get(user).bombPlaced) {
                if (user.getServerWorld().getPlayerByUuid(payload.target()) instanceof ServerPlayerEntity bombTarget) {
                    transferBomb(user, bombTarget);
                }
                return;
            }
            // 醉酒：技能失效——无效果、不提示（不自知，BT-SYS-DRUNK）
            if (BttPlayerComponent.KEY.get(user).isDrunk()) return;
            // 吟游诗人/花匠：<歌唱>/<栽培> 无需目标（G 键直发）
            if (gwc.isRole(user, BttRoles.MINSTREL)) {
                minstrel(user);
                return;
            }
            if (gwc.isRole(user, BttRoles.GARDENER)) {
                gardener(user);
                return;
            }
            // 特工：<查看> 本局身份列表（G 键直发）
            if (gwc.isRole(user, BttRoles.AGENT)) {
                agent(user, gwc);
                return;
            }
            // 工程师：<扫描> 透视全车 10 秒（G 键直发）
            if (gwc.isRole(user, BttRoles.ENGINEER)) {
                engineer(user);
                return;
            }
            // 建筑师：<修复> 准星所指被撬/被卡的门（G 键直发；docx 冷却 2 分钟）
            if (gwc.isRole(user, BttRoles.ARCHITECT)) {
                architect(user);
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
            } else if (gwc.isRole(user, BttRoles.SNAKE_CHARMER)) {
                snakeCharmer(user, target, gwc);
            } else if (gwc.isRole(user, BttRoles.ASSASSIN)) {
                assassin(user, target, gwc, payload, guessed);
            } else if (gwc.isRole(user, BttRoles.SMUGGLER)) {
                smuggler(user, target);
            } else if (gwc.isRole(user, BttRoles.IMPOSTOR)) {
                impostor(user, target, gwc, payload, guessed);
            } else if (gwc.isRole(user, BttRoles.JOURNALIST)) {
                journalist(user, target);
            } else if (gwc.isRole(user, BttRoles.TERRORIST)) {
                terroristPlace(user, target);
            } else if (gwc.isRole(user, BttRoles.PARTYHOST)) {
                partyhost(user, target);
            } else if (gwc.isRole(user, BttRoles.DETECTIVE)) {
                detective(user, target, gwc);
            } else if (gwc.isRole(user, BttRoles.RIGGER)) {
                rigger(user, target);
            } else if (gwc.isRole(user, BttRoles.PHARMACIST)) {
                pharmacist(user, target);
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
            BttPlayerComponent userComp = BttPlayerComponent.KEY.get(user);
            int hits = ++userComp.novelistHits;
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
                        .setRoundEndData(user.getServerWorld().getPlayers().stream()
                                        .filter(p -> gwc.getRole(p) != null).collect(java.util.stream.Collectors.toList()),
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
            BttPlayerComponent.KEY.get(target).setCult(true);
            broadcast(user, Text.literal(target.getName().getString() + " 已成为教团信徒！")
                    .formatted(Formatting.DARK_PURPLE));
            target.sendMessage(Text.literal("你成为了教团信徒（教团可互相透视）。")
                    .formatted(Formatting.LIGHT_PURPLE), true);
        } else {
            broadcast(user, Text.literal("救世主是 " + user.getName().getString() + "！")
                    .formatted(Formatting.DARK_RED));
        }
    }

    // ===== 特工：<查看> 本局身份列表（G 键直发，无冷却） =====

    private static void agent(ServerPlayerEntity user, GameWorldComponent gwc) {
        var seen = new java.util.LinkedHashMap<String, java.util.UUID>();
        for (var p : user.getServerWorld().getPlayers()) {
            Role r = gwc.getRole(p);
            if (r == null || seen.containsValue(p.getUuid())) continue;
            seen.putIfAbsent(BttIdentity.displayName(r).getString(), p.getUuid());
        }
        var unique = new java.util.LinkedHashSet<String>();
        for (var p : user.getServerWorld().getPlayers()) {
            Role r = gwc.getRole(p);
            if (r != null) unique.add(BttIdentity.displayName(r).getString());
        }
        user.sendMessage(Text.literal("── 本局身份列表 ──").formatted(Formatting.GOLD), true);
        user.sendMessage(Text.literal(String.join("、", unique)).formatted(Formatting.YELLOW), true);
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
        BttPlayerComponent.KEY.get(target).applyDrunk(GameConstants.getInTicks(1, 0));
        user.sendMessage(Text.literal("灌酒成功。").formatted(Formatting.BLUE), true);
    }

    // ===== 吟游诗人：<歌唱> 全场醉酒 1 分钟，CD 2 分钟（docx 2026-09-07） =====

    private static void minstrel(ServerPlayerEntity user) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        setCd(ability, GameConstants.getInTicks(2, 0));
        for (ServerPlayerEntity p : user.getServerWorld().getPlayers()) {
            BttPlayerComponent.KEY.get(p).applyDrunk(GameConstants.getInTicks(1, 0));
        }
        user.sendMessage(Text.literal("你唱起了一支歌……").formatted(Formatting.LIGHT_PURPLE), true);
    }

    // ===== 走私犯：<灌酒> 任何人永久醉酒，CD 30 秒（施加者死亡后解除，D11/D8） =====

    private static void smuggler(ServerPlayerEntity user, ServerPlayerEntity target) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        setCd(ability, GameConstants.getInTicks(0, 30));
        BttPlayerComponent.KEY.get(target).applyPermanentDrunk(user.getUuid());
        user.sendMessage(Text.literal("灌酒成功。").formatted(Formatting.BLUE), true);
    }

    // ===== 记者：<跟踪> 任意玩家持续透视（标记；未标记时透视最远者）；CD 30 秒 =====

    // ===== 工程师：<扫描> 透视全车 10 秒，CD 1 分钟（docx 2026-09-07） =====

    private static void engineer(ServerPlayerEntity user) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        setCd(ability, GameConstants.getInTicks(1, 0));
        BttPlayerComponent.KEY.get(user).engineerScanTicks = GameConstants.getInTicks(0, 10);
        user.sendMessage(Text.literal("扫描中……全车人员已标记 10 秒。").formatted(Formatting.AQUA), true);
    }

    // ===== 建筑师：<修复> 准星所指被撬/被卡的门；CD 2 分钟（docx） =====

    private static void architect(ServerPlayerEntity user) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        if (!BttArchitect.repair(user)) return; // 未命中/无需修复 → 不消耗冷却
        setCd(ability, GameConstants.getInTicks(2, 0));
    }

    private static void journalist(ServerPlayerEntity user, ServerPlayerEntity target) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        setCd(ability, GameConstants.getInTicks(0, 30));
        BttPlayerComponent.KEY.get(user).markedTarget = target.getUuid().toString();
        user.sendMessage(Text.literal("跟踪目标：" + target.getName().getString()).formatted(Formatting.GOLD), true);
    }

    // ===== 恐怖分子：<放置炸弹> 准星所指玩家；5 秒静默 → 15 秒倒计时 → 爆炸；CD 30 秒 =====

    private static void terroristPlace(ServerPlayerEntity user, ServerPlayerEntity target) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        setCd(ability, GameConstants.getInTicks(0, 30));
        BttPlayerComponent tc = BttPlayerComponent.KEY.get(target);
        if (tc.bombPlaced) {
            user.sendMessage(Text.literal("目标身上已有炸弹。").formatted(Formatting.RED), true);
            return;
        }
        tc.bombPlaced = true;
        tc.bombBeeping = false;
        tc.bombTimer = GameConstants.getInTicks(0, 5);
        tc.bombSource = user.getUuid().toString();
        tc.bombLastSec = -1;
        user.sendMessage(Text.literal("炸弹已放置。").formatted(Formatting.GOLD), true);
        target.sendMessage(Text.literal("你听到了一声轻响……").formatted(Formatting.DARK_RED), true);
    }

    /** 炸弹传递（持有者 G 键对准他人；倒计时阶段才可传递，3 秒传递冷却） */
    private static void transferBomb(ServerPlayerEntity holder, ServerPlayerEntity target) {
        BttPlayerComponent hc = BttPlayerComponent.KEY.get(holder);
        if (!hc.bombPlaced || !hc.bombBeeping || hc.bombTransferCd > 0) return;
        if (target == holder || !GameFunctions.isPlayerAliveAndSurvival(target)) return;
        BttPlayerComponent tc = BttPlayerComponent.KEY.get(target);
        if (tc.bombPlaced) return;
        tc.bombPlaced = true;
        tc.bombBeeping = true;
        tc.beepTimer = hc.beepTimer;
        tc.bombSource = hc.bombSource;
        tc.bombLastSec = -1;
        tc.bombTransferCd = GameConstants.getInTicks(0, 3);
        hc.bombPlaced = false;
        hc.bombBeeping = false;
        holder.getWorld().playSound(null, target.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_ITEM_PICKUP,
                net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
        holder.sendMessage(Text.literal("炸弹已脱手。").formatted(Formatting.GOLD), true);
        target.sendMessage(Text.literal("有人把炸弹塞给了你！快传出去！").formatted(Formatting.DARK_RED, Formatting.BOLD), true);
    }

    // ===== 冒牌货：猜身份；对=窃取 kit + 目标永久醉酒（直至窃取者死亡）；CD 60s =====

    private static void impostor(ServerPlayerEntity user, ServerPlayerEntity target, GameWorldComponent gwc,
                                 BttGuessC2SPacket payload, Role guessed) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        setCd(ability, GameConstants.getInTicks(1, 0));
        if (guessed != null && guessed.identifier().getPath().equalsIgnoreCase(payload.guess())) {
            BttRoleDef d = BttRoleDefs.get(guessed);
            if (d != null) d.dispatchKit(user); // 窃取（物品层；技能层移植见 ROADMAP BT-IMPOSTOR）
            BttPlayerComponent.KEY.get(target).applyPermanentDrunk(user.getUuid());
            user.sendMessage(Text.literal("窃取成功：" + BttIdentity.displayName(guessed).getString())
                    .formatted(Formatting.GOLD), true);
        } else {
            user.sendMessage(Text.literal("猜错了。").formatted(Formatting.RED), true);
        }
    }

    // ===== 派对主：<变声> 身边者；一次=醉酒，两次=氦气自爆；CD 30s =====

    private static void partyhost(ServerPlayerEntity user, ServerPlayerEntity target) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        setCd(ability, GameConstants.getInTicks(0, 30));
        BttPlayerComponent uc = BttPlayerComponent.KEY.get(user);
        uc.partyUses++;
        if (uc.partyUses >= 2) {
            user.sendMessage(Text.literal("氦气……").formatted(Formatting.RED), true);
            GameFunctions.killPlayer(user, true, user, BttDeathReasons.HELIUM_SELF_DESTRUCT);
            return;
        }
        BttPlayerComponent.KEY.get(target).applyDrunk(GameConstants.getInTicks(1, 0));
        target.sendMessage(Text.literal("你的声音变高了……").formatted(Formatting.LIGHT_PURPLE), true);
    }

    // ===== 舞蛇人 =====

    // ===== 花匠：<栽培> 于脚下种小花（相邻≥20m、非露天），CD 30 秒 =====

    private static void gardener(ServerPlayerEntity user) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        setCd(ability, GameConstants.getInTicks(0, 30));
        String err = BttFlowers.plant(user);
        user.sendMessage(err == null
                ? Text.literal("你种下了一粒种子。").formatted(Formatting.GREEN)
                : Text.literal(err).formatted(Formatting.RED), true);
    }

    // ===== 刺客：<识破> 猜身份；对=杀（识破魔法），错=仅被猜者收到通知（D3）；CD 60s =====

    private static void assassin(ServerPlayerEntity user, ServerPlayerEntity target, GameWorldComponent gwc,
                                 BttGuessC2SPacket payload, Role guessed) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        setCd(ability, GameConstants.getInTicks(1, 0));
        if (guessed != null && guessed.identifier().getPath().equalsIgnoreCase(payload.guess())) {
            user.sendMessage(Text.literal("识破成功。").formatted(Formatting.GOLD), true);
            GameFunctions.killPlayer(target, true, user, BttDeathReasons.IDENTIFY_MAGIC);
        } else {
            // 猜错：仅被猜者收到通知（不向全场揭示）
            target.sendMessage(Text.literal(user.getName().getString() + " 未能揭下你的面具……")
                    .formatted(Formatting.DARK_PURPLE), true);
        }
    }

    // ===== 侦探：<调查> 身边者（选人），CD 60s =====

    private static void detective(ServerPlayerEntity user, ServerPlayerEntity target, GameWorldComponent gwc) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        setCd(ability, GameConstants.getInTicks(1, 0));
        boolean killed = BttPlayerComponent.KEY.get(target).hasKilled > 0;
        user.sendMessage(Text.literal(target.getName().getString()
                + (killed ? " 曾经杀过人" : " 没有杀过人")).formatted(killed ? Formatting.RED : Formatting.GREEN), true);
    }

    // ===== 绳艺师：<拘束> 目标 15s，CD 60s =====

    private static void rigger(ServerPlayerEntity user, ServerPlayerEntity target) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        if (user.distanceTo(target) > 6) {
            user.sendMessage(Text.literal("目标不在身边。").formatted(Formatting.RED), true);
            return;
        }
        setCd(ability, GameConstants.getInTicks(1, 0));
        target.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                net.minecraft.entity.effect.StatusEffects.SLOWNESS, GameConstants.getInTicks(0, 15), 250, false, true));
    }

    // ===== 药剂师：<喂药> 解毒；健康人回满理智（docx 2026-09-07），CD 60s =====

    private static void pharmacist(ServerPlayerEntity user, ServerPlayerEntity target) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        if (user.distanceTo(target) > 6) {
            user.sendMessage(Text.literal("目标不在身边。").formatted(Formatting.RED), true);
            return;
        }
        setCd(ability, GameConstants.getInTicks(1, 0));
        PlayerPoisonComponent poison = PlayerPoisonComponent.KEY.get(target);
        if (poison.poisonTicks > 0) poison.reset();
        else PlayerMoodComponent.KEY.get(target).setMood(1.0f);
    }

    // ===== 猎人：仅限一次 <狙击>——目标为主犯则死亡，否则无效（揭示与否=作者确认 TODO）；UI instant 复用 =====

    private static void hunter(ServerPlayerEntity user, ServerPlayerEntity target, GameWorldComponent gwc) {
        if (BttPlayerComponent.KEY.get(user).hunterShot == 1) {
            user.sendMessage(Text.literal("你已经用过狙击了。").formatted(Formatting.RED), true);
            return;
        }
        BttPlayerComponent.KEY.get(user).hunterShot = 1;
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
            swapInventories(user, target); // 身份互换 → 物品栏一并互换
            swapBalances(user, target);    // 狂气一并互换
            gwc.addRole(user.getUuid(), targetRole);
            gwc.addRole(target.getUuid(), mine);
            gwc.sync();
            // 新舞蛇人（原主犯）中毒（doc）
            PlayerPoisonComponent.KEY.get(target).setPoisonTicks(100000, user.getUuid()); // 永久中毒（docx 2026-09-09）
            broadcast(user, Text.literal("舞蛇人识破了主犯！两人身份互换——"
                    + target.getName().getString() + " 成为了新的舞蛇人（且已中毒）。").formatted(Formatting.DARK_PURPLE));
        } else {
            user.sendMessage(Text.literal("他不是主犯。").formatted(Formatting.RED), true);
        }
    }

    // ===== 通用 =====

    /** 互换两名玩家的整份物品栏（主手/背包/护甲/副手） */
    private static void swapInventories(ServerPlayerEntity a, ServerPlayerEntity b) {
        var ia = a.getInventory();
        var ib = b.getInventory();
        int size = Math.min(ia.size(), ib.size());
        for (int i = 0; i < size; i++) {
            ItemStack sa = ia.getStack(i).copy();
            ItemStack sb = ib.getStack(i).copy();
            ia.setStack(i, sb);
            ib.setStack(i, sa);
        }
        ia.markDirty();
        ib.markDirty();
    }

    /** 互换两名玩家的狂气余额 */
    private static void swapBalances(ServerPlayerEntity a, ServerPlayerEntity b) {
        PlayerShopComponent pa = PlayerShopComponent.KEY.get(a);
        PlayerShopComponent pb = PlayerShopComponent.KEY.get(b);
        int ba = pa.balance;
        int bb = pb.balance;
        pa.setBalance(bb);
        pb.setBalance(ba);
    }

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
