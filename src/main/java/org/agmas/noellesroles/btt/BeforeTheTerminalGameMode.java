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
        // 乘客侧（执法/平民/狂人）初始总数——审判落幕牺牲条件（docx 2026-09-07）
        BttState.initialPassengerSide = (int) seats.values().stream()
                .map(BttRoles::factionOf)
                .filter(f -> f == BttRoles.Faction.ENFORCER || f == BttRoles.Faction.CIVILIAN
                        || f == BttRoles.Faction.MAD)
                .count();
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

    private static java.util.function.Predicate<ServerPlayerEntity> isWinnerByKiller(GameWorldComponent gwc) {
        return p -> {
            var f = BttRoles.factionOf(gwc.getRole(p));
            return f == BttRoles.Faction.PRINCIPAL || f == BttRoles.Faction.ACCOMPLICE;
        };
    }

    private static java.util.function.Predicate<ServerPlayerEntity> isWinnerByInnocent(GameWorldComponent gwc) {
        return p -> {
            var f = BttRoles.factionOf(gwc.getRole(p));
            return f == BttRoles.Faction.ENFORCER || f == BttRoles.Faction.CIVILIAN || f == BttRoles.Faction.MAD;
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
        boolean anySeats = false;
        for (ServerPlayerEntity player : players) {
            Role role = gameWorld.getRole(player);
            if (role == null) continue;
            anySeats = true;
            if (GameFunctions.isPlayerAliveAndSurvival(player)) {
                var faction = org.agmas.noellesroles.btt.BttRoles.factionOf(role);
                // C-037 三分类：狂人中立=MAD（乘客阵营，计入乘客侧）；独行不阻塞任何结局
                if (faction == BttRoles.Faction.PRINCIPAL) alivePrincipals++;
                else if (faction == BttRoles.Faction.ACCOMPLICE) aliveAccomplices++;
                else if (faction == BttRoles.Faction.OUTSIDER_NEUTRAL) aliveOutsiderNeutrals++;
                else if (faction == BttRoles.Faction.ENFORCER || faction == BttRoles.Faction.CIVILIAN
                        || faction == BttRoles.Faction.MAD) alivePassengers++;
            }
        }
        if (!anySeats) return; // 防御：尚无座位（不应发生）

        boolean stationReached = !GameTimeComponent.KEY.get(world).hasTime();

        // 独胜判定：窃贼（BttWatheVultureThiefMixin）与小说家（BttGuessReceiver）均在行为点直接
        // lastEnding+winners+setRoundEndData+stopGame，不在此 tick 判定（2026-09-07 用户指令）
        BttEndings.Ending ending = BttEndings.decide(alivePrincipals, aliveAccomplices,
                alivePassengers, aliveOutsiderNeutrals, stationReached,
                BttState.initialPassengerSide - alivePassengers, BttState.initialPassengerSide);

        // fork 口径：isWinner 服务端算好写入 game_state.winners（覆盖全部结局；客户端只分组不再判阵营）
        // C-037：按 BTT 阵营判定（接管键的 NR 原生 innocent 旗标不可靠，如 jester）——
        // 乘客侧=执法/平民/狂人；凶手侧=主犯/从犯；独行/外人中立不随主结局胜负
        java.util.function.Predicate<ServerPlayerEntity> isWinner = switch (ending) {
            case TRIAL_COMPLETE, JOURNEY_END -> p -> {
                var f = BttRoles.factionOf(gameWorld.getRole(p));
                return f == BttRoles.Faction.ENFORCER || f == BttRoles.Faction.CIVILIAN || f == BttRoles.Faction.MAD;
            };
            case BLOOD_EXPRESS, NAKU_KORO -> p -> {
                var f = BttRoles.factionOf(gameWorld.getRole(p));
                return f == BttRoles.Faction.PRINCIPAL || f == BttRoles.Faction.ACCOMPLICE;
            };
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
        java.util.function.Predicate<ServerPlayerEntity> flipWinner = ws == GameFunctions.WinStatus.KILLERS
                ? (java.util.function.Predicate<ServerPlayerEntity>) isWinnerByKiller(gameWorld)
                : isWinnerByInnocent(gameWorld);
        String finalWinners = ws == GameFunctions.WinStatus.NONE
                ? winners
                : players.stream().filter(flipWinner)
                        .map(p -> p.getUuid().toString()).collect(java.util.stream.Collectors.joining(","));
if (ending != BttEndings.Ending.NONE && gameWorld.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE) {
            LOGGER.info("[BTT] ending decided: {} (aliveP={} alivePr={} aliveAc={} aliveON={} station={})",
                    ending, alivePassengers, alivePrincipals, aliveAccomplices, aliveOutsiderNeutrals, stationReached);
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
