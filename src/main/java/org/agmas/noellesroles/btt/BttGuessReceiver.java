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
        PayloadTypeRegistry.playC2S().register(BttLawyerC2SPacket.ID, BttLawyerC2SPacket.CODEC);
        BttArchitect.register();
        ServerPlayNetworking.registerGlobalReceiver(BttCorpseActionC2SPacket.ID, (payload, context) -> {
            ServerPlayerEntity user = context.player();
            if (!BttIdentity.isBttMode(user.getWorld())) return;
            GameWorldComponent gwc = GameWorldComponent.KEY.get(user.getWorld());
            if (!gwc.isRunning()) return;
            // 醉酒：技能失效（不自知）
            if (BttPlayerComponent.KEY.get(user).isDrunk()) return;
            // ===== 失忆患者（C-110，照 StupidExpress RoleSelectionHandler）：从尸体上得到**身份和阵营** =====
            // 「仅限一次」由角色改变本身保证（`AMNESIAC` 已不在 `getRole` 里）
            if (BttRoles.isPlayingAs(gwc, user, BttRoles.AMNESIAC)) {
                if (!(user.getServerWorld().getEntity(payload.body()) instanceof dev.doctor4t.wathe.entity.PlayerBodyEntity body)) return;
                if (BttBodyComponent.KEY.get(body).isAmnesiacUsed()) return;
                Role dead = gwc.getRole(body.getPlayerUuid());
                if (dead == null) return;
                BttBodyComponent.KEY.get(body).markAmnesiacUsed();
                BttSecondIdentity.takeOver(user, dead);
                user.sendMessage(BttSecondIdentity.tookOverText(dead), true);
                return;
            }
            // ===== 食人族（C-110）：从**平民乘客**尸体上"暂时习得他的技能"（不改阵营、不发道具），CD 1 分钟 =====
            if (BttRoles.isPlayingAs(gwc, user, BttRoles.CANNIBAL)) {
                if (!(user.getServerWorld().getEntity(payload.body()) instanceof dev.doctor4t.wathe.entity.PlayerBodyEntity body)) return;
                Role dead = gwc.getRole(body.getPlayerUuid());
                if (dead == null || BttRoles.factionOf(dead) != BttRoles.Faction.CIVILIAN) return; // 只吃平民乘客
                BttBodyComponent.KEY.get(body).markAmnesiacUsed(); // docx 2026-09-12：尸体被吃掉
                AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
                if (ability.cooldown > 0) return;
                ability.cooldown = GameConstants.getInTicks(1, 0);
                ability.sync();
                BttSecondIdentity.borrow(user, dead, GameConstants.getInTicks(1, 0)); // 借 60 秒（与冷却同步）【待作者】
                // C-128：食人族食用音
                user.getServerWorld().playSound(null, user.getBlockPos(), BttSounds.CANNIBAL_EAT,
                        net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
                user.sendMessage(Text.translatable("noellesroles.btt.action.cannibal.learn",
                                BttIdentity.displayName(dead).getString())
                        .withColor(BttRoles.CANNIBAL.color()), true);
                return;
            }
        });
        // 律师 <起诉>（C-103）：单包提交全部指名
        ServerPlayNetworking.registerGlobalReceiver(BttLawyerC2SPacket.ID, (payload, context) -> {
            ServerPlayerEntity user = context.player();
            if (!BttIdentity.isBttMode(user.getWorld())) return;
            GameWorldComponent gwc = GameWorldComponent.KEY.get(user.getWorld());
            if (!gwc.isRunning()) return;
            if (BttPlayerComponent.KEY.get(user).isDrunk()) return;
            BttFolklorist.onAnyoneAbility(user, gwc); // C-131：民俗学家被动透视「任何人」类技能的使用者
            // C-125：同一"多指名"上行包按角色分派（律师 <起诉> / 绳艺师 <拘束> 任意两人）
            if (BttRoles.isPlayingAs(gwc, user, BttRoles.LAWYER)) {
                lawyer(user, gwc, payload.picks());
            } else if (BttRoles.isPlayingAs(gwc, user, BttRoles.RIGGER)) {
                rigger(user, gwc, payload.picks());
            } else if (BttRoles.isPlayingAs(gwc, user, BttRoles.MAGICIAN)) {
                magician(user, payload.picks());
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(BttGuessC2SPacket.ID, (payload, context) -> {
            ServerPlayerEntity user = context.player();
            if (!BttIdentity.isBttMode(user.getWorld())) return;
            GameWorldComponent gwc = GameWorldComponent.KEY.get(user.getWorld());
            if (!gwc.isRunning()) return;
            // C-113：炸弹相关不再走 G 键（用户裁定）——传递只保留物品右键
            // 醉酒：技能失效——无效果、不提示（不自知，BT-SYS-DRUNK）
            if (BttPlayerComponent.KEY.get(user).isDrunk()) return;
            BttFolklorist.onAnyoneAbility(user, gwc); // C-131：民俗学家被动透视「任何人」类技能的使用者
            if (BttRoles.isPlayingAs(gwc, user, BttRoles.GARDENER)) {
                gardener(user);
                return;
            }
            // 纵火犯：<浇汽油> 最近的未浇湿者（G 键直发；C-092 抄 NRS 病原体，3 格 + 视线）
            if (BttRoles.isPlayingAs(gwc, user, BttRoles.ARSONIST)) {
                BttArsonist.douse(user, gwc);
                return;
            }
            // 工程师：<扫描> 透视全车 10 秒（G 键直发）
            if (BttRoles.isPlayingAs(gwc, user, BttRoles.ENGINEER)) {
                engineer(user);
                return;
            }
            // 建筑师：<修复> 准星所指被撬/被卡的门（G 键直发；docx 冷却 2 分钟）
            if (BttRoles.isPlayingAs(gwc, user, BttRoles.ARCHITECT)) {
                architect(user);
                return;
            }
            // 锁匠：<上锁> / 开锁 准星所指的门（G 键直发；C-119）
            if (BttRoles.isPlayingAs(gwc, user, BttRoles.LOCKSMITH)) {
                BttLocksmith.toggle(user);
                return;
            }
            // 乘务员：<广播> 开关（G 键直发，无目标；C-123）
            if (BttRoles.isPlayingAs(gwc, user, BttRoles.ATTENDANT)) {
                BttBroadcast.toggle(user);
                return;
            }
            // 演员：<装死> 开关（G 键直发，无目标；C-106）
            if (BttRoles.isPlayingAs(gwc, user, BttRoles.ACTOR)) {
                BttActor.toggle(user, BttPlayerComponent.KEY.get(user));
                return;
            }
            // 梦游病：<入梦> 灵魂出窍 ⇄ 回归（G 键直发，无目标；C-087）
            if (BttRoles.isPlayingAs(gwc, user, BttRoles.MEYUUBYOU)) {
                BttSpirit.toggle(user);
                return;
            }
            if (!(user.getServerWorld().getPlayerByUuid(payload.target()) instanceof ServerPlayerEntity target)) return;
            if (target == user) return;
            Role guessed = gwc.getRole(target);

            if (BttRoles.isPlayingAs(gwc, user, BttRoles.PROPHET)) {
                prophet(user, target, gwc, payload, guessed);
            } else if (BttRoles.isPlayingAs(gwc, user, BttRoles.NOVELIST)) {
                novelist(user, target, gwc, payload, guessed);
            } else if (BttRoles.isPlayingAs(gwc, user, BttRoles.HUNTER)) {
                hunter(user, target, gwc);
            } else if (BttRoles.isPlayingAs(gwc, user, BttRoles.MESSIAH)) {
                messiah(user, target, gwc, payload);
            } else if (BttRoles.isPlayingAs(gwc, user, BttRoles.BARTENDER)) {
                bartender(user, target);
            } else if (BttRoles.isPlayingAs(gwc, user, BttRoles.SNAKE_CHARMER)) {
                snakeCharmer(user, target, gwc);
            } else if (BttRoles.isPlayingAs(gwc, user, BttRoles.ASSASSIN)) {
                assassin(user, target, gwc, payload, guessed);
            } else if (BttRoles.isPlayingAs(gwc, user, BttRoles.SUCCUBUS)) {
                succubus(user, target);
            } else if (BttRoles.isPlayingAs(gwc, user, BttRoles.BODYGUARD)) {
                BttGuard.guard(user, target);
            } else if (BttRoles.isPlayingAs(gwc, user, BttRoles.LEECH)) {
                BttGuard.parasitize(user, target);
            } else if (BttRoles.isPlayingAs(gwc, user, BttRoles.CULT_LEADER)) {
                BttCultLeader.redeem(user, target);
            } else if (BttRoles.isPlayingAs(gwc, user, BttRoles.IMP)) {
                BttImp.mark(user, target);
            } else if (BttRoles.isPlayingAs(gwc, user, BttRoles.JOURNALIST)) {
                journalist(user, target);
            } else if (BttRoles.isPlayingAs(gwc, user, BttRoles.COMEDIAN)) {
                comedian(user, target);
            } else if (BttRoles.isPlayingAs(gwc, user, BttRoles.PROFESSOR)) {
                professor(user, target);
            } else if (BttRoles.isPlayingAs(gwc, user, BttRoles.KIDNAPPER)) {
                BttKidnapper.swallow(user, target, BttPlayerComponent.KEY.get(target));
            } else if (BttRoles.isPlayingAs(gwc, user, BttRoles.DETECTIVE)) {
                detective(user, target, gwc);
            } else if (BttRoles.isPlayingAs(gwc, user, BttRoles.PHARMACIST)) {
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
            user.sendMessage(Text.translatable("noellesroles.btt.action.prophet.correct",
                    target.getName().getString(), BttIdentity.displayName(guessed).getString())
                    .withColor(colorOf(user)), true);
            setCd(ability, GameConstants.getInTicks(1, 0));
        } else {
            user.sendMessage(Text.translatable("noellesroles.btt.action.prophet.wrong").withColor(colorOf(user)), true);
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
            // C-128：小说家猜对音（本人可闻）
            user.getServerWorld().playSound(null, user.getBlockPos(), BttSounds.NOVELIST_CORRECT,
                    net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
            // doc 口径文案（用户指定 2026-09-05）；猜错不播报
            broadcast(user, Text.translatable("noellesroles.btt.action.novelist.correct").withColor(colorOf(user)));
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

    // ===== 救世主：<预知> 任何人身份（docx：CD 1 分钟，**包括初始冷却**）；对→目标入教团；错→救世主身份暴露 =====

    private static void messiah(ServerPlayerEntity user, ServerPlayerEntity target, GameWorldComponent gwc,
                                BttGuessC2SPacket payload) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        setCd(ability, BttRoleDefs.CD_1MIN); // C-099：docx「冷却一分钟，包括初始冷却」（原 2 分钟）
        Role guessed = gwc.getRole(target);
        if (guessed != null && guessed.identifier().getPath().equalsIgnoreCase(payload.guess())) {
            BttPlayerComponent.KEY.get(target).setCult(true);
            broadcast(user, Text.translatable("noellesroles.btt.action.cult.converted", target.getName().getString())
                    .withColor(colorOf(user)));
            target.sendMessage(Text.translatable("noellesroles.btt.action.cult.joined")
                    .withColor(colorOf(user)), true);
        } else {
            broadcast(user, Text.translatable("noellesroles.btt.action.messiah.reveal", user.getName().getString())
                    .withColor(colorOf(user)));
        }
    }

    // ===== 酒保：<灌酒> 身边者醉酒 1 分钟，CD 1 分钟（docx 2026-09-07） =====

    private static void bartender(ServerPlayerEntity user, ServerPlayerEntity target) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        if (user.distanceTo(target) > 6) {
            user.sendMessage(Text.translatable("noellesroles.btt.action.common.not_nearby").withColor(colorOf(user)), true);
            return;
        }
        setCd(ability, GameConstants.getInTicks(1, 0));
        BttPlayerComponent.KEY.get(target).applyDrunk(GameConstants.getInTicks(1, 0));
        user.sendMessage(Text.translatable("noellesroles.btt.action.common.pour_done").withColor(colorOf(user)), true);
    }

    // ===== 魅魔（原走私犯改键）：<魅惑> 身边者 → 醉酒 1 分钟；CD 1 分钟（docx 2026-09-12） =====

    private static void succubus(ServerPlayerEntity user, ServerPlayerEntity target) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        if (user.distanceTo(target) > 6) {
            user.sendMessage(Text.translatable("noellesroles.btt.action.common.not_nearby").withColor(colorOf(user)), true);
            return;
        }
        setCd(ability, BttRoleDefs.CD_1MIN);
        BttPlayerComponent.KEY.get(target).applyDrunk(GameConstants.getInTicks(1, 0));
        user.sendMessage(Text.translatable("noellesroles.btt.action.succubus.charm", target.getName().getString())
                .withColor(colorOf(user)), true);
    }

    // ===== 记者：<跟踪> 任意玩家持续透视（**只**描边显式标记；已删「未标记时透视最远者」，C-089）CD 1 分钟（docx 2026-09-11：原 30 秒） =====

    // ===== 工程师：<扫描> 透视全车 10 秒，CD 1 分钟（docx 2026-09-07） =====

    private static void engineer(ServerPlayerEntity user) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        setCd(ability, GameConstants.getInTicks(1, 0));
        BttPlayerComponent.KEY.get(user).engineerScanTicks = GameConstants.getInTicks(0, 10);
        user.sendMessage(Text.translatable("noellesroles.btt.action.engineer.scan").withColor(colorOf(user)), true);
    }

    // ===== 建筑师：<修复> 准星所指被撬/被卡的门；CD 1 分钟（docx 2026-09-11：原 2 分钟） =====

    private static void architect(ServerPlayerEntity user) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        if (!BttArchitect.repair(user)) return; // 未命中/无需修复 → 不消耗冷却
        setCd(ability, BttRoleDefs.CD_1MIN); // C-099
    }

    private static void journalist(ServerPlayerEntity user, ServerPlayerEntity target) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        setCd(ability, BttRoleDefs.CD_1MIN); // C-099：docx 2026-09-11（原 30 秒）
        BttPlayerComponent c = BttPlayerComponent.KEY.get(user);
        c.markedTarget = target.getUuid().toString();
        c.sync(); // C-089：客户端 BttEntityHighlightRenderer 按本机组件描边，必须即时同步
        user.sendMessage(Text.translatable("noellesroles.btt.action.journalist.track", target.getName().getString()).withColor(colorOf(user)), true);
    }


    // ===== 派对主：<变声> 身边者——按键标记，10–30 秒后自动生效（一次=醉酒，两次=氦气自爆）；CD 30s（C-093/C-097） =====

    private static void comedian(ServerPlayerEntity user, ServerPlayerEntity target) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        if (!BttDelayed.schedule(user, BttDelayed.COMEDIAN, target)) return; // 已有待生效标记 → 不扣 CD
        setCd(ability, GameConstants.getInTicks(0, 30));
        user.sendMessage(Text.translatable("noellesroles.btt.action.partyhost.marked", target.getName().getString())
                .withColor(colorOf(user)), true);
    }


    // ===== 教授：<使用药剂> 身边者——目标获得 1 层护盾（免疫下一次致命伤），CD 1 分钟（C-104） =====

    private static void professor(ServerPlayerEntity user, ServerPlayerEntity target) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        if (user.distanceTo(target) > 6) {
            user.sendMessage(Text.translatable("noellesroles.btt.action.common.not_nearby").withColor(BttRoles.PROFESSOR.color()), true);
            return;
        }
        if (!BttPlayerComponent.KEY.get(target).applyProfessorShield()) { // 已有药剂 → 不扣冷却
            user.sendMessage(Text.translatable("noellesroles.btt.action.professor.already").withColor(BttRoles.PROFESSOR.color()), true);
            return;
        }
        setCd(ability, GameConstants.getInTicks(2, 0)); // docx 2026-09-12：教授 CD 2 分钟
        user.sendMessage(Text.translatable("noellesroles.btt.action.professor.give", target.getName().getString())
                .withColor(BttRoles.PROFESSOR.color()), true);
        target.sendMessage(Text.translatable("noellesroles.btt.action.professor.received")
                .withColor(BttRoles.PROFESSOR.color()), true);
    }

    // ===== 舞蛇人 =====

    // ===== 花匠：<栽培> 于脚下种小花（相邻≥20m、非露天），CD 30 秒 =====

    private static void gardener(ServerPlayerEntity user) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        Text err = BttFlowers.plant(user);
        if (err != null) { // 不宜栽培（露天 / 距其他小花 <20m）→ 不消耗冷却（与建筑师 <修复> 同口径）
            user.sendMessage(err.copy().withColor(colorOf(user)), true);
            return;
        }
        setCd(ability, GameConstants.getInTicks(0, 30));
        user.sendMessage(Text.translatable("noellesroles.btt.action.gardener.planted").withColor(colorOf(user)), true);
    }

    // ===== 刺客：<识破> 猜身份；对=杀（识破魔法），错=仅被猜者收到通知（D3）；CD 60s =====

    private static void assassin(ServerPlayerEntity user, ServerPlayerEntity target, GameWorldComponent gwc,
                                 BttGuessC2SPacket payload, Role guessed) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        setCd(ability, GameConstants.getInTicks(1, 0));
        if (guessed != null && guessed.identifier().getPath().equalsIgnoreCase(payload.guess())) {
            user.sendMessage(Text.translatable("noellesroles.btt.action.assassin.hit").withColor(colorOf(user)), true);
            GameFunctions.killPlayer(target, true, user, BttDeathReasons.IDENTIFY_MAGIC);
        } else {
            // 猜错：仅被猜者收到通知（不向全场揭示）
            target.sendMessage(Text.translatable("noellesroles.btt.action.assassin.miss", user.getName().getString())
                    .withColor(colorOf(user)), true);
        }
    }

    // ===== 侦探：<调查> 身边者（选人），CD 60s =====

    private static void detective(ServerPlayerEntity user, ServerPlayerEntity target, GameWorldComponent gwc) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        setCd(ability, GameConstants.getInTicks(1, 0));
        boolean killed = BttPlayerComponent.KEY.get(target).hasKilled > 0;
        user.sendMessage(Text.translatable(killed
                        ? "noellesroles.btt.action.detective.killed" : "noellesroles.btt.action.detective.clean",
                target.getName().getString()).withColor(colorOf(user)), true);
    }

    // ===== 绳艺师：<拘束> **任意两个人** 30 秒，CD 1 分钟（docx 2026-09-12；C-125 改为 E 键多指名） =====

    private static void rigger(ServerPlayerEntity user, GameWorldComponent gwc, java.util.List<java.util.UUID> picks) {
        AbilityPlayerComponent riggerAbility = AbilityPlayerComponent.KEY.get(user);
        if (riggerAbility.cooldown > 0) return;
        java.util.List<ServerPlayerEntity> riggerTargets = twoAlive(user, picks);
        if (riggerTargets.size() < 2) return;
        setCd(riggerAbility, BttRoleDefs.CD_1MIN);
        // C-128：拘束音（在施法者处播放）
        user.getServerWorld().playSound(null, user.getBlockPos(), BttSounds.RIGGER_BIND,
                net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
        for (ServerPlayerEntity target : riggerTargets) {
            target.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                    net.minecraft.entity.effect.StatusEffects.SLOWNESS,
                    GameConstants.getInTicks(0, 30), 250, false, false)); // C-113：绑缚的缓慢隐藏粒子
        }
        user.sendMessage(Text.translatable("noellesroles.btt.action.rigger.bound",
                        riggerTargets.get(0).getName().getString(), riggerTargets.get(1).getName().getString())
                .withColor(colorOf(user)), true);
    }

    // ===== 魔术师：<交换> **任意两个人** 的位置，CD 1 分钟（C-126：E 键多指名，取代 NR 原生两段式 UI） =====

    private static void magician(ServerPlayerEntity user, java.util.List<java.util.UUID> picks) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        java.util.List<ServerPlayerEntity> targets = twoAlive(user, picks);
        if (targets.size() < 2) return;
        ServerPlayerEntity a = targets.get(0);
        ServerPlayerEntity b = targets.get(1);
        var world = user.getServerWorld();
        // 复刻 NR 口径：两人的位置都必须"有空间"，否则不交换（也不扣冷却）
        if (!world.isSpaceEmpty(a) || !world.isSpaceEmpty(b)) {
            user.sendMessage(Text.translatable("noellesroles.btt.action.swapper.no_space")
                    .withColor(colorOf(user)), true);
            return;
        }
        var posA = a.getPos();
        var posB = b.getPos();
        a.refreshPositionAfterTeleport(posB.x, posB.y, posB.z);
        b.refreshPositionAfterTeleport(posA.x, posA.y, posA.z);
        setCd(ability, BttRoleDefs.CD_1MIN);
        // C-128：交换音（在施法者处播放）
        user.getServerWorld().playSound(null, user.getBlockPos(), BttSounds.SWAPPER_SWAP,
                net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
        user.sendMessage(Text.translatable("noellesroles.btt.action.swapper.swapped",
                        a.getName().getString(), b.getName().getString())
                .withColor(colorOf(user)), true);
    }

    /** 多指名技能通用取人：最多 2 名、去重、排除自己、必须是存活玩家 */
    private static java.util.List<ServerPlayerEntity> twoAlive(ServerPlayerEntity user,
                                                               java.util.List<java.util.UUID> picks) {
        java.util.List<ServerPlayerEntity> targets = new java.util.ArrayList<>(2);
        for (java.util.UUID uuid : picks) {
            if (targets.size() >= 2) break;
            if (uuid == null || uuid.equals(user.getUuid())) continue;
            if (!(user.getServerWorld().getPlayerByUuid(uuid) instanceof ServerPlayerEntity p)) continue;
            if (!GameFunctions.isPlayerAliveAndSurvival(p)) continue;
            if (!targets.contains(p)) targets.add(p);
        }
        return targets;
    }

    // ===== 药剂师：<喂药> 解毒；健康人回满理智（docx 2026-09-07），CD 60s =====

    private static void pharmacist(ServerPlayerEntity user, ServerPlayerEntity target) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        if (user.distanceTo(target) > 6) {
            user.sendMessage(Text.translatable("noellesroles.btt.action.common.not_nearby").withColor(colorOf(user)), true);
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
            user.sendMessage(Text.translatable("noellesroles.btt.action.hunter.used").withColor(colorOf(user)), true);
            return;
        }
        BttPlayerComponent.KEY.get(user).hunterShot = 1;
        // 一次性：此后选人件长期灰显（AbilityPlayerComponent 大 CD）
        AbilityPlayerComponent huntAbility = AbilityPlayerComponent.KEY.get(user);
        huntAbility.setCooldown(20 * 60 * 60);
        huntAbility.sync();
        Role targetRole = gwc.getRole(target);
        if (targetRole != null && BttRoles.factionOf(targetRole) == BttRoles.Faction.PRINCIPAL) {
            broadcast(user, Text.translatable("noellesroles.btt.action.hunter.success").withColor(colorOf(user)));
            // 能杀死人而非枪击：用狙击魔法死因（doc 死因表），避开处决链
            GameFunctions.killPlayer(target, true, user, BttDeathReasons.SNIPE_MAGIC);
        } else {
            user.sendMessage(Text.translatable("noellesroles.btt.action.hunter.miss").withColor(colorOf(user)), true);
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
            // C-099：新舞蛇人（原主犯）获得该技能的初始冷却 1 分钟（docx 2026-09-07「新舞蛇人初始冷却」+「所有技能都有初始冷却」）
            AbilityPlayerComponent newCharmer = AbilityPlayerComponent.KEY.get(target);
            newCharmer.setCooldown(BttRoleDefs.CD_1MIN);
            newCharmer.sync();
            // 新舞蛇人（原主犯）中毒（doc）
            BttPlayerComponent.KEY.get(target).applyPermanentDrunk(user.getUuid()); BttPlayerComponent.KEY.get(target).sync(); // docx 2026-09-12：新舞蛇人永久醉酒（原永久中毒） // 永久中毒（docx 2026-09-09）
            broadcast(user, Text.translatable("noellesroles.btt.action.snake_charmer.swap",
                    target.getName().getString()).withColor(colorOf(user)));
        } else {
            user.sendMessage(Text.translatable("noellesroles.btt.action.hunter.not_principal").withColor(colorOf(user)), true);
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

    // ===== 律师：<起诉> 场上所有凶手（凶手席位；不含叛徒/黑死病）——指名全部即全部死亡；CD 1 分钟（C-103） =====

    /**
     * docx：「精准 <起诉> 场上的所有凶手（不包括加入凶手阵营的其他身份），如果正确则全部死亡，冷却 60 秒。」
     * 用户 2026-09-11 裁定：**一次点齐所有凶手**（仅限凶手席位；18 人局 = 3 个），不另做多选 UI——点几次凑齐后单包提交。
     * 判据：指名集合（去重后）== **在场凶手席位玩家**集合；否则不成立（仍计冷却）。
     * 2026-09-11 用户裁定：**席位已死亡者同样要点名**（席位按开局分配、不随死亡减少），故集合不做存活过滤，
     * 处斩只对仍存活者执行。
     * 死因 = `noellesroles:snipe_magic`（狙击魔法；2026-09-11 用户指定）。
     */
    private static void lawyer(ServerPlayerEntity user, GameWorldComponent gwc, java.util.List<java.util.UUID> picks) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        java.util.Set<java.util.UUID> accused = new java.util.LinkedHashSet<>(picks);
        if (accused.size() != picks.size()) return; // 重复指名（客户端不应发出）→ 忽略，不扣冷却
        java.util.Set<java.util.UUID> killers = new java.util.LinkedHashSet<>();
        for (ServerPlayerEntity p : user.getServerWorld().getPlayers()) {
            if (BttRoles.isKillerSeat(gwc.getRole(p))) killers.add(p.getUuid()); // 含已死亡席位（用户 2026-09-11）
        }
        setCd(ability, GameConstants.getInTicks(1, 0)); // C-099：docx 冷却 60 秒
        if (killers.isEmpty() || !accused.equals(killers)) {
            user.sendMessage(Text.translatable("noellesroles.btt.action.lawyer.rejected").withColor(BttRoles.LAWYER.color()), true);
            return;
        }
        int executed = 0;
        for (java.util.UUID uuid : killers) {
            if (!(user.getServerWorld().getPlayerByUuid(uuid) instanceof ServerPlayerEntity victim)) continue;
            if (!GameFunctions.isPlayerAliveAndSurvival(victim)) continue; // 已死者只入名册，不再处斩
            victim.sendMessage(Text.translatable("noellesroles.btt.action.lawyer.accused")
                    .withColor(BttRoles.LAWYER.color()), true);
            GameFunctions.killPlayer(victim, true, user, BttDeathReasons.SNIPE_MAGIC);
            executed++;
        }
        user.sendMessage(Text.translatable("noellesroles.btt.action.lawyer.success", executed)
                .withColor(BttRoles.LAWYER.color()), true);
    }

    private static void setCd(AbilityPlayerComponent ability, int ticks) {
        ability.setCooldown(ticks);
        ability.sync();
    }

    /**
     * C-098：动作栏技能反馈（含发给被施放者的通知）一律用**施放者的身份色**——身份色为唯一事实源，勿自造颜色。
     * 结算/成败等语义一律写在文案里（原红/绿分色废止）。
     */
    private static int colorOf(ServerPlayerEntity user) {
        Role role = GameWorldComponent.KEY.get(user.getWorld()).getRole(user);
        return role == null ? 0xFFFFFF : role.color();
    }

    private static void broadcast(ServerPlayerEntity any, Text text) {
        for (ServerPlayerEntity p : any.getServerWorld().getPlayers()) {
            p.sendMessage(text, true);
        }
    }
}
