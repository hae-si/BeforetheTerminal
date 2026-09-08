package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.api.GameMode;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.cca.GameRoundEndComponent;
import dev.doctor4t.wathe.cca.GameTimeComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.gui.RoleAnnouncementTexts;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.util.AnnounceWelcomePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.Harpymodloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 《终点站抵达之前》(Before the Terminal) 剧本模式主循环。
 * 席位解锁（2026-09-05）：6–18 人按 doc 公式从全目录分配（已实装身份优先，BARTENDER 排除）；
 * 汽笛开局经 BttHornStartMixin 指向本模式（原硬编码 MURDER）。
 * DEMO-002/003/008：席位分配 + 身份宣告（凶手/乘客计数）+ doc 结局判定。
 */
public class BeforeTheTerminalGameMode extends GameMode {
    public static final Logger LOGGER = LoggerFactory.getLogger("noellesroles/btt");

    public static final Identifier ID = Identifier.of("noellesroles", "before_the_terminal");
    /** doc：到站倒计时初始 8 分钟（2026-09-04 策划修订：10→8） */
    public static final int DEFAULT_START_MINUTES = 8;
    /** doc：最少 6 人；Demo 固定 6 人（RM D5） */
    public static final int MIN_PLAYERS = 6;

    public BeforeTheTerminalGameMode() {
        super(ID, DEFAULT_START_MINUTES, MIN_PLAYERS);
    }

    @Override
    public void initializeGame(ServerWorld world, GameWorldComponent gameWorld, List<ServerPlayerEntity> players) {
        // 席位解锁（2026-09-05）：6–18 人按 doc 公式分配；越界拒绝
        if (players.size() < BttIdentity.MIN_PLAYERS || players.size() > BttIdentity.MAX_PLAYERS) {
            for (ServerPlayerEntity player : players) {
                player.sendMessage(Text.translatable("noellesroles.start_error.player_range",
                        BttIdentity.MIN_PLAYERS, BttIdentity.MAX_PLAYERS, players.size()).formatted(Formatting.RED), true);
            }
            LOGGER.warn("[BTT] start refused: {} ready players, requires {}-{}.", players.size(), BttIdentity.MIN_PLAYERS, BttIdentity.MAX_PLAYERS);
            GameFunctions.stopGame(world);
            return;
        }

        gameWorld.clearRoleMap();
        // 防御：开局强制清空背包（发 kit 前；修复"偶尔残留上一局物品"）
        for (ServerPlayerEntity p : players) {
            p.getInventory().clear();
        }
        Map<UUID, Role> seats = BttIdentity.assignSeats(players.stream().map(ServerPlayerEntity::getUuid).toList());
        if (seats == null) {
            GameFunctions.stopGame(world);
            return;
        }
        // 关系系统（C-061）：双子在 addRole 前统一身份
        BttRelationships.assign(world, seats, (uuid, text) -> {
            ServerPlayerEntity p = (ServerPlayerEntity) world.getPlayerByUuid(uuid);
            if (p != null) p.sendMessage(text, true);
        });
        for (ServerPlayerEntity player : players) {
            Role role = seats.get(player.getUuid());
            gameWorld.addRole(player, role);
            // 复用 NR“发初始道具”链路（列车长钥匙等），BTT 自身道具由 BttEvents 发放
            org.agmas.harpymodloader.events.ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, role);
        }
        gameWorld.sync();

        BttGameWorldComponent btt = BttGameWorldComponent.KEY.get(world);
        btt.active = true;
        btt.lastEnding = "NONE"; // 跨局残留清理（参照 SRE finalizeGame“回合状态全清”原则）
        btt.winners = "";
        btt.sync();
        LOGGER.info("[BTT] round initialized: {} players seated.", players.size());

        // 身份宣告：wathe 原版迎新覆盖层（身份名+凶手数+乘客数）。游戏内聊天框不可见，
        // 不再输出聊天行；中立/外人数不展示（策划 2026-09-04）。
        int killers = 0;
        int passengers = 0;
        for (Role r : seats.values()) {
            if (r.canUseKiller()) killers++;
            else if (r.isInnocent()) passengers++;
        }
        for (ServerPlayerEntity player : players) {
            Role role = seats.get(player.getUuid());
            int index = announcementIndex(role);
            ServerPlayNetworking.send(player, new AnnounceWelcomePayload(index, killers, passengers));
        }
    }

    private static void startEpilogueBroadcast(String type, java.util.List<ServerPlayerEntity> players) {
        String key = switch (type) {
            case "MAJO" -> "majo";
            case "CULT" -> "cult";
            case "KIDNAPPER" -> "kidnapper";
            case "GARDENER" -> "gardener";
            default -> "survival";
        };
        for (ServerPlayerEntity p : players) {
            p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleS2CPacket(
                    Text.translatable("noellesroles.epilogue." + key + ".title").formatted(Formatting.DARK_PURPLE, Formatting.BOLD)));
            p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.SubtitleS2CPacket(
                    Text.translatable("noellesroles.epilogue." + key + ".line").formatted(Formatting.LIGHT_PURPLE)));
            p.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket(10, 70, 10));
        }
    }

    private static java.util.function.Predicate<ServerPlayerEntity> isWinnerByKiller(GameWorldComponent gwc) {        return p -> {
            Role r = gwc.getRole(p);
            var f = BttRoles.factionOf(r);
            return f == BttRoles.Faction.PRINCIPAL || f == BttRoles.Faction.ACCOMPLICE || r == BttRoles.BLACKDEATH;
        };
    }

    private static java.util.function.Predicate<ServerPlayerEntity> isWinnerByInnocent(GameWorldComponent gwc) {
        return p -> {
            Role r = gwc.getRole(p);
            var f = BttRoles.factionOf(r);
            return (f == BttRoles.Faction.ENFORCER || f == BttRoles.Faction.CIVILIAN || f == BttRoles.Faction.MAD)
                    && r != BttRoles.BLACKDEATH;
        };
    }

    private static int announcementIndex(Role role) {
        var text = Harpymodloader.autogeneratedAnnouncements.get(role);
        if (text == null) {
            if (role == WatheRoles.VIGILANTE) text = RoleAnnouncementTexts.VIGILANTE;
            else if (role == WatheRoles.KILLER) text = RoleAnnouncementTexts.KILLER;
            else text = RoleAnnouncementTexts.CIVILIAN;
        }
        int index = RoleAnnouncementTexts.ROLE_ANNOUNCEMENT_TEXTS.indexOf(text);
        return Math.max(index, 0);
    }

    @Override
    public void tickServerGameLoop(ServerWorld world, GameWorldComponent gameWorld) {
        List<ServerPlayerEntity> players = world.getPlayers();
        // 所有人离开 → 终止回合（避免无人局永久挂起）
        if (players.isEmpty()) {
            LOGGER.info("[BTT] all players left; stopping round.");
            GameFunctions.stopGame(world);
            return;
        }

        int alivePrincipals = 0;
        int aliveAccomplices = 0;
        int alivePassengers = 0;
        int aliveOutsiderNeutrals = 0; // 仅外人中立（魔女/救世主/饕餮/花匠）——结局阻塞项
        int aliveLone = 0;
        int aliveCult = 0; // 救世主+信徒（教团阵营）：不参与常规结局计数，视为"外人在场"
        boolean majoAlive = false;
        boolean messiahAlive = false;
        boolean kidnapperAlive = false;
        boolean gardenerAlive = false;
        boolean anySeats = false;
        for (ServerPlayerEntity player : players) {
            Role role = gameWorld.getRole(player);
            if (role == null) continue;
            anySeats = true;
            if (GameFunctions.isPlayerAliveAndSurvival(player)) {
                // 教团成员（救世主/信徒）：阵营变为教团——从常规结局计数中移除（实现选择，待作者复核）
                if (role == BttRoles.MESSIAH || BttState.getInt(player.getUuid(), "cult") == 1) {
                    aliveCult++;
                    if (role == BttRoles.MESSIAH) messiahAlive = true;
                    continue;
                }
                var faction = org.agmas.noellesroles.btt.BttRoles.factionOf(role);
                // C-037 三分类 + docx 2026-09-07：黑死病=狂人中立席位但阵营归属**凶手**（额外的凶手，
                // 胜负与其他凶手一致）——计入凶手侧、不计入乘客侧
                if (role == BttRoles.BLACKDEATH) alivePrincipals++;
                else if (faction == BttRoles.Faction.PRINCIPAL) alivePrincipals++;
                else if (faction == BttRoles.Faction.ACCOMPLICE) aliveAccomplices++;
                else if (faction == BttRoles.Faction.OUTSIDER) aliveOutsiderNeutrals++;
                else if (faction == BttRoles.Faction.LONE) aliveLone++;
                else if (faction == BttRoles.Faction.ENFORCER || faction == BttRoles.Faction.CIVILIAN
                        || faction == BttRoles.Faction.MAD) alivePassengers++;
                if (role == BttRoles.MAJO && faction == BttRoles.Faction.OUTSIDER) majoAlive = true;
                if (role == BttRoles.KIDNAPPER) kidnapperAlive = true;
                if (role == BttRoles.GARDENER) gardenerAlive = true;
            }
        }
        if (!anySeats) return; // 防御：尚无座位（不应发生）

        GameTimeComponent gameTime = GameTimeComponent.KEY.get(world);

        // ===== 独胜即时判定（docx 胜利条件，C-059）：杀光所有人=魔女立即胜利；只剩教团=教团立即胜利 =====
        // （与"无外人时乘客团灭/凶手团灭直接赢"同构；尾声存活胜利（v3）为其补充路径，见下）
        boolean majoWin = majoAlive && alivePrincipals == 0 && aliveAccomplices == 0
                && alivePassengers == 0 && aliveOutsiderNeutrals == 1; // 场上仅剩魔女（独行可不杀）
        boolean cultWin = aliveCult > 0 && alivePrincipals == 0 && aliveAccomplices == 0
                && alivePassengers == 0 && aliveOutsiderNeutrals == 0 && aliveLone == 0; // 只剩教团阵营

        // ===== 尾声（BT-SYS-EPILOGUE v3，C-058：作者澄清） =====
        // ①凶手数>乘客数 → 倒计时**减至两分钟**；②倒计时 ≤2min → **必然进入尾声**（期间 BGM【GAP】）。
        // 主持人翁按 魔女/救世主>饕餮/花匠>凶手(生还尾声) 动态认领：当前主持人翁**阵营全灭**且有其他凶手/外人存在时，
        // **链式切换**到下一位（重新广播标题）。倒计时归零结算：主持人翁=外人且存活 → 各自胜利；
        // 生还尾声 → 旅途结束/乘客胜利（正常 decide）。尾声期间常规结局判定暂停。
        int murderers = alivePrincipals + aliveAccomplices;
        // 压表（2026-09-07 澄清）：**凶手 + 外人 > 乘客**（外人=外人中立+教团成员；黑死病计入凶手侧）。
        // time>1200 守卫防尾声进行中回拨。
        int outsidersTotal = aliveOutsiderNeutrals + aliveCult;
        if (gameTime.getTime() > 1200 && murderers + outsidersTotal > alivePassengers) {
            gameTime.setTime(1200); // 倒计时减至两分钟
        }
        BttEndings.Ending ending = BttEndings.Ending.NONE;
        if (majoWin) {
            ending = BttEndings.Ending.MAJO_WIN;
        } else if (cultWin) {
            ending = BttEndings.Ending.CULT_WIN;
        } else if (gameTime.getTime() <= 1200) {
            // 主持人翁动态认领（存活者中按优先级）；当前主持人翁阵营全灭 → desired 自动落到下一位 → 链式切换
            String desired = majoAlive ? "MAJO" : messiahAlive ? "CULT"
                    : kidnapperAlive ? "KIDNAPPER" : gardenerAlive ? "GARDENER" : "SURVIVAL";
            if (!desired.equals(BttState.epilogueType)) {
                BttState.epilogueType = desired;
                startEpilogueBroadcast(desired, players);
            }
            if (gameTime.getTime() <= 0) {
                // 倒计时归零结算：主持人翁=外人且存活 → 各自胜利；生还尾声 → 正常到站判定（旅途结束/乘客胜利）
                ending = switch (desired) {
                    case "MAJO" -> BttEndings.Ending.MAJO_WIN;
                    case "CULT" -> BttEndings.Ending.CULT_WIN;
                    case "KIDNAPPER" -> BttEndings.Ending.KIDNAPPER_WIN;
                    case "GARDENER" -> BttEndings.Ending.GARDENER_WIN;
                    default -> BttEndings.decide(alivePrincipals, aliveAccomplices,
                            alivePassengers, aliveOutsiderNeutrals, true);
                };
            }
        } else {
            BttState.epilogueType = "";
            ending = BttEndings.decide(alivePrincipals, aliveAccomplices,
                    alivePassengers, aliveOutsiderNeutrals, false);
        }

        // fork 口径：isWinner 服务端算好写入 game_state.winners（覆盖全部结局；客户端只分组不再判阵营）
        // C-037：按 BTT 阵营判定（接管键的 NR 原生 innocent 旗标不可靠，如 jester）——
        // 乘客侧=执法/平民/狂人（黑死病除外）；凶手侧=主犯/从犯/黑死病（docx：额外的凶手）；独行/外人中立不随主结局胜负
        java.util.function.Predicate<ServerPlayerEntity> isWinner = switch (ending) {
            case TRIAL_COMPLETE, JOURNEY_END -> p -> {
                Role r = gameWorld.getRole(p);
                var f = BttRoles.factionOf(r);
                return (f == BttRoles.Faction.ENFORCER || f == BttRoles.Faction.CIVILIAN || f == BttRoles.Faction.MAD)
                        && r != BttRoles.BLACKDEATH;
            };
            case BLOOD_EXPRESS, NAKU_KORO -> p -> {
                Role r = gameWorld.getRole(p);
                var f = BttRoles.factionOf(r);
                return f == BttRoles.Faction.PRINCIPAL || f == BttRoles.Faction.ACCOMPLICE || r == BttRoles.BLACKDEATH;
            };
            case MAJO_WIN -> p -> gameWorld.getRole(p) == BttRoles.MAJO;
            case CULT_WIN -> p -> gameWorld.getRole(p) == BttRoles.MESSIAH
                    || BttState.getInt(p.getUuid(), "cult") == 1;
            case KIDNAPPER_WIN -> p -> gameWorld.getRole(p) == BttRoles.KIDNAPPER;
            case GARDENER_WIN -> p -> gameWorld.getRole(p) == BttRoles.GARDENER;
            default -> p -> false;
        };
        String winners = players.stream().filter(isWinner)
                .map(p -> p.getUuid().toString()).collect(java.util.stream.Collectors.joining(","));

        // 异端分子：对调乘客与凶手的胜负结果（即使已死亡）——翻转为 doc"特殊的乘客/凶手胜利结局，
        // 伴有特殊胜利宣言"：结局改写为 HERETIC_KILLER / HERETIC_PASSENGER（宣言键 noellesroles.special.heretic.*）
        GameFunctions.WinStatus ws = BttEndings.winStatusOf(ending);
        boolean heretic = players.stream()
                .anyMatch(p -> gameWorld.getRole(p) == BttRoles.HERETIC);
        if (heretic && ws != GameFunctions.WinStatus.NONE) {
            ws = BttEndings.flip(ws);
            ending = ws == GameFunctions.WinStatus.KILLERS
                    ? BttEndings.Ending.HERETIC_KILLER
                    : BttEndings.Ending.HERETIC_PASSENGER;
        }
        // 恋人（C-061）：双方存活到最后且阵营不同 → 并入胜者组（"各自原本胜利条件亦生效，但不是恋人胜利"——标题保持原结局）
        java.util.List<UUID> loverWinners = new java.util.ArrayList<>();
        for (ServerPlayerEntity p : players) {
            if (!BttRelationships.isLover(p.getUuid()) || !GameFunctions.isPlayerAliveAndSurvival(p)) continue;
            UUID partner = BttRelationships.partnerOf(p.getUuid());
            ServerPlayerEntity partnerPlayer = partner == null ? null : (ServerPlayerEntity) world.getPlayerByUuid(partner);
            if (partnerPlayer == null || !GameFunctions.isPlayerAliveAndSurvival(partnerPlayer)) continue;
            var f1 = BttRoles.factionOf(gameWorld.getRole(p));
            var f2 = BttRoles.factionOf(gameWorld.getRole(partnerPlayer));
            if (f1 != f2) loverWinners.add(p.getUuid());
        }
        java.util.function.Predicate<ServerPlayerEntity> flipWinner = ws == GameFunctions.WinStatus.KILLERS
                ? (java.util.function.Predicate<ServerPlayerEntity>) isWinnerByKiller(gameWorld)
                : isWinnerByInnocent(gameWorld);
        String finalWinners = ws == GameFunctions.WinStatus.NONE
                ? winners
                : players.stream().filter(flipWinner)
                        .map(p -> p.getUuid().toString()).collect(java.util.stream.Collectors.joining(","));
        // 恋人并胜（C-061）：追加进胜者组
        if (!loverWinners.isEmpty()) {
            finalWinners = finalWinners.isEmpty() ? loverWinners.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(","))
                    : finalWinners + "," + loverWinners.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(","));
        }
        // 花匠小花推进（阶段/攻击；{花匠尾声}期间仅花匠 20m 内成花攻击）
        ServerPlayerEntity gardener = null;
        for (ServerPlayerEntity p : players) {
            if (gameWorld.getRole(p) == BttRoles.GARDENER && GameFunctions.isPlayerAliveAndSurvival(p)) {
                gardener = p;
                break;
            }
        }
        BttFlowers.tick(world, gardener, "GARDENER".equals(BttState.epilogueType));

        if (ending != BttEndings.Ending.NONE && gameWorld.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE) {
            LOGGER.info("[BTT] ending decided: {} (aliveP={} alivePr={} aliveAc={} aliveON={} station={})",
                    ending, alivePassengers, alivePrincipals, aliveAccomplices, aliveOutsiderNeutrals,
                    gameTime.getTime() <= 0);
            // doc 结局写入同步组件：客户端 BttEndTextMixin 直接改写 wathe 结束覆盖层文本（不在聊天框输出）
            BttGameWorldComponent btt = BttGameWorldComponent.KEY.get(world);
            btt.lastEnding = ending.name();
            btt.winners = finalWinners;
            btt.sync();
            GameRoundEndComponent.KEY.get(world).setRoundEndData(new ArrayList<>(players), ws);
            GameFunctions.stopGame(world);
        }
    }

    @Override
    public void finalizeGame(ServerWorld world, GameWorldComponent gameWorld) {
        BttFlowers.clear(); // 小花清场（C-062）

        // wathe GameFunctions.finalizeGame 已完成：清角色/重置玩家/清尸体/INACTIVE。
        BttGameWorldComponent btt = BttGameWorldComponent.KEY.get(world);
        btt.active = false;
        // winners 保留：结局覆盖层(finalize 后仍显示 200t)需持续读取；下一局 initializeGame 覆盖。
        btt.sync();

        // BTT 回合状态清理：祭品辉光/队伍 + 回合级状态表
        for (ServerPlayerEntity p : world.getPlayers()) {
            p.setGlowing(false);
        }
        var scoreboard = world.getScoreboard();
        var team = scoreboard.getTeam("sacrifice");
        if (team != null) {
            for (String name : List.copyOf(team.getPlayerList())) {
                scoreboard.removeScoreHolderFromTeam(name, team);
            }
            scoreboard.removeTeam(team);
        }
        BttState.resetRound();
    }
}
