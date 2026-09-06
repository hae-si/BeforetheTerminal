package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.AllowPlayerDeath;
import dev.doctor4t.wathe.api.event.CanSeePoison;
import dev.doctor4t.wathe.cca.GameTimeComponent;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * BTT 事件接线（BT-ARCH-001 后仅做**派发与全局系统**，身份行为一律在 {@link BttRoleDefs} 声明）：
 * <ul>
 *   <li>kit 派发（ModdedRoleAssigned → defs）</li>
 *   <li>全局死亡监听：小丑疯魔（AllowPlayerDeath 否决）、明星枪杀免疫</li>
 *   <li>全局系统：卖糖人识毒（CanSeePoison）、祭品池分配、HML 池隔离、wathe 配置垫片</li>
 *   <li>tick/use 派发（defs）</li>
 * </ul>
 */
public final class BttEvents {
    private BttEvents() {}

    /** 祭品得分队（深绿名+辉光轮廓） */
    private static final String SACRIFICE_TEAM = "btt_sacrifice";
    private static final Random RANDOM = new Random();

    public static void init() {
        ensureWatheClientConfig();
        registerKitDispatch();
        registerJesterPsycho();
        registerStarImmunity();
        registerCandySeller();
        BttGuessReceiver.register();
        BttShopGate.init();
        BttForceRoleCommand.register();
        guardHmlPool();
    }

    // ===== kit 派发（身份初始物品/状态 → BttRoleDefs） =====

    private static void registerKitDispatch() {
        ModdedRoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                BttRoleDef d = BttRoleDefs.get(role);
                if (d != null) d.dispatchKit(serverPlayer);
            }
        });
    }

    // ===== 小丑疯魔（Phase 1 全局监听：AllowPlayerDeath 否决协议，非 per-role 钩子） =====

    private static void registerJesterPsycho() {
        AllowPlayerDeath.EVENT.register((victim, killer, reason) -> {
            if (!BttIdentity.isBttMode(victim.getWorld())) return true;
            if (reason != GameConstants.DeathReasons.GUN) return true;
            GameWorldComponent gwc = GameWorldComponent.KEY.get(victim.getWorld());
            if (!gwc.isRole(victim, BttRoles.JESTER)) return true;
            if (killer == null || !gwc.isInnocent(killer)) return true;
            dev.doctor4t.wathe.cca.PlayerPsychoComponent psycho = dev.doctor4t.wathe.cca.PlayerPsychoComponent.KEY.get(victim);
            if (psycho.getPsychoTicks() > 0) return true;
            psycho.startPsycho();
            psycho.setPsychoTicks(GameConstants.getInTicks(2, 0));
            psycho.setArmour(2);
            victim.giveItemStack(new ItemStack(WatheItems.BAT));
            for (int i = 0; i < victim.getInventory().size(); i++) {
                if (victim.getInventory().getStack(i).isOf(WatheItems.BAT)) {
                    victim.getInventory().selectedSlot = i;
                    break;
                }
            }
            return false;
        });
    }

    // ===== 明星：被枪杀不死亡（全局否决） =====

    private static void registerStarImmunity() {
        AllowPlayerDeath.EVENT.register((victim, killer, reason) -> {
            if (!BttIdentity.isBttMode(victim.getWorld())) return true;
            if (reason != GameConstants.DeathReasons.GUN) return true;
            GameWorldComponent gwc = GameWorldComponent.KEY.get(victim.getWorld());
            return !gwc.isRole(victim, BttRoles.STAR);
        });
    }

    // ===== 卖糖人：识别下毒餐盘（wathe CanSeePoison 原生事件） =====

    private static void registerCandySeller() {
        CanSeePoison.EVENT.register(player -> {
            if (!BttIdentity.isBttMode(player.getWorld())) return false;
            return GameWorldComponent.KEY.get(player.getWorld()).isRole(player, BttRoles.CANDY_SELLER);
        });
    }

    // ===== use 派发：全量回退（2026-09-05 用户裁定——枪/刀对玩家右键失效 bisect；
    // 失忆/窃贼尸体交互与女仆赠予待以 G 键/UI 重做） =====

    // ===== tick 派发（END_SERVER_TICK → defs）+ 全局祭品池 =====

    static void registerTickHandlers() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var world : server.getWorlds()) {
                if (!(world instanceof ServerWorld serverWorld)) continue;
                GameWorldComponent gwc = GameWorldComponent.KEY.get(world);
                if (!(gwc.getGameMode() instanceof BeforeTheTerminalGameMode)) continue;
                if (!gwc.isRunning()) continue;

                for (var player : world.getPlayers()) {
                    BttRoleDef d = BttRoleDefs.get(gwc.getRole(player));
                    if (d != null) d.dispatchTick(player, serverWorld, gwc);
                }

                assignSacrificesIfNeeded(serverWorld, gwc);
            }
        });
    }

    // ===== 祭品（连环杀手/恶魔共用）：每 10 平民或中立出 1 祭品，深绿显示 =====

    private static void assignSacrificesIfNeeded(ServerWorld world, GameWorldComponent gwc) {
        if (BttState.sacrificesAssigned()) return;
        BttState.markSacrificesAssigned();

        List<ServerPlayerEntity> candidates = new ArrayList<>();
        for (ServerPlayerEntity p : world.getPlayers()) {
            Role role = gwc.getRole(p);
            if (role == null) continue;
            BttRoles.Faction f = BttRoles.factionOf(role);
            if (f == BttRoles.Faction.CIVILIAN || f == BttRoles.Faction.NEUTRAL) candidates.add(p);
        }
        int need = candidates.size() / 10;
        if (need == 0) return;

        Collections.shuffle(candidates, RANDOM);
        Scoreboard sb = world.getScoreboard();
        Team team = sb.getTeam(SACRIFICE_TEAM);
        if (team == null) {
            team = sb.addTeam(SACRIFICE_TEAM);
            team.setColor(net.minecraft.util.Formatting.DARK_GREEN);
        }
        for (int i = 0; i < need; i++) {
            ServerPlayerEntity t = candidates.get(i);
            BttState.setInt(t.getUuid(), "sacrifice", 1);
            sb.addScoreHolderToTeam(t.getGameProfile().getName(), team);
            t.setGlowing(true);
        }
    }

    // ===== HML 池隔离 =====

    private static void guardHmlPool() {
        org.agmas.harpymodloader.config.HarpyModLoaderConfig.HANDLER.load();
        boolean changed = false;
        for (Identifier id : BttRoles.newRoleIds()) {
            if (!org.agmas.harpymodloader.config.HarpyModLoaderConfig.HANDLER.instance().disabled.contains(id.toString())) {
                org.agmas.harpymodloader.config.HarpyModLoaderConfig.HANDLER.instance().disabled.add(id.toString());
                changed = true;
            }
        }
        if (changed) org.agmas.harpymodloader.config.HarpyModLoaderConfig.HANDLER.save();
    }

    // ===== wathe 配置垫片 =====

    private static void ensureWatheClientConfig() {
        try {
            var cfg = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("wathe.json");
            if (java.nio.file.Files.notExists(cfg)) {
                java.nio.file.Files.writeString(cfg, "{\"ultraPerfMode\": false, \"disableScreenShake\": false}");
                BeforeTheTerminalGameMode.LOGGER.info("[BTT] pre-created config/wathe.json");
            }
        } catch (Exception e) {
            BeforeTheTerminalGameMode.LOGGER.warn("[BTT] could not pre-create wathe config: {}", e.toString());
        }
    }
}
