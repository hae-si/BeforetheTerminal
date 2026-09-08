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
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
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
    private static final String SACRIFICE_TEAM = "sacrifice"; // 保留：祭品系统 dormant（恶魔/连环杀手已移除）
    private static final Random RANDOM = new Random();

    public static void init() {
        ensureWatheClientConfig();
        registerKitDispatch();
        registerJesterPsycho();
        registerStarImmunity();
        registerCandySeller();
        BttGuessReceiver.register();
        BttShopGate.init();
        registerMaidGive();
        registerPsychopathShield();
        guardHmlPool();
    }

    // ===== 精神病人护盾（doc：拿出球棒获得护盾，直到杀死一个人——球棒冷却期间无盾） =====

    private static void registerPsychopathShield() {
        // 宿敌护盾（C-061）：凶手方宿敌 archenemyShields>0 → 否决死亡并消耗一层
        AllowPlayerDeath.EVENT.register((victim, killer, reason) -> {
            if (org.agmas.noellesroles.btt.BttRelationships.archenemyShields(victim.getUuid()) > 0
                    && org.agmas.noellesroles.btt.BttRelationships.consumeArchenemyShield(victim.getUuid())) {
                victim.sendMessage(net.minecraft.text.Text.literal("宿敌的护盾抵挡了致命一击。")
                        .formatted(net.minecraft.util.Formatting.GOLD), true);
                return false;
            }
            return true;
        });
        AllowPlayerDeath.EVENT.register((victim, killer, reason) -> {
            if (!BttIdentity.isBttMode(victim.getWorld())) return true;
            GameWorldComponent gwc = GameWorldComponent.KEY.get(victim.getWorld());
            if (!gwc.isRunning()) return true;
            if (!gwc.isRole(victim, BttRoles.PSYCHOPATH)) return true;
            if (!victim.getMainHandStack().isOf(WatheItems.BAT)) return true;
            if (victim.getItemCooldownManager().isCoolingDown(WatheItems.BAT)) return true;
            return false; // 持棒（未冷却）→ 免死
        });
    }

    // ===== 女仆赠予（doc：将拿取的食物/饮料赠予他人；手持食物右键玩家；仅当目标有对应需求） =====

    private static void registerMaidGive() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient() || hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity user)) return ActionResult.PASS;
            if (!BttIdentity.isBttMode(world)) return ActionResult.PASS;
            GameWorldComponent gwc = GameWorldComponent.KEY.get(world);
            if (!gwc.isRunning()) return ActionResult.PASS;
            if (!gwc.isRole(user, BttRoles.MAID)) return ActionResult.PASS;
            if (entity == null || entity == user) return ActionResult.PASS;
            ItemStack held = user.getMainHandStack();
            if (held.isEmpty() || !isFoodOrDrink(held)) return ActionResult.PASS;
            if (!(entity instanceof ServerPlayerEntity target)) return ActionResult.PASS;
            // NRS 口径（用户指令）：仅当目标有对应需求（任务）时才能赠予
            var mood = dev.doctor4t.wathe.cca.PlayerMoodComponent.KEY.get(target);
            var need = isDrink(held)
                    ? dev.doctor4t.wathe.cca.PlayerMoodComponent.Task.DRINK
                    : dev.doctor4t.wathe.cca.PlayerMoodComponent.Task.EAT;
            if (!mood.tasks.containsKey(need)) return ActionResult.PASS;
            if (!target.getInventory().insertStack(held.copy())) return ActionResult.PASS;
            user.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
            user.sendMessage(net.minecraft.text.Text.literal("赠予 " + target.getName().getString()
                    + " 一份食物。").formatted(net.minecraft.util.Formatting.LIGHT_PURPLE), true);
            return ActionResult.SUCCESS;
        });
    }

    /** 女仆赠予范围：食物/饮料（1.21 数据组件 FOOD 或 wathe 鸡尾酒） */
    private static boolean isFoodOrDrink(ItemStack stack) {
        return isDrink(stack) || stack.contains(net.minecraft.component.DataComponentTypes.FOOD);
    }

    private static boolean isDrink(ItemStack stack) {
        return stack.isOf(WatheItems.OLD_FASHIONED) || stack.isOf(WatheItems.MARTINI)
                || stack.isOf(WatheItems.MOJITO) || stack.isOf(WatheItems.COSMOPOLITAN)
                || stack.isOf(WatheItems.CHAMPAGNE);
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
            return GameWorldComponent.KEY.get(player.getWorld()).isRole(player, BttRoles.PHARMACIST);
        });
    }

    // ===== use 派发：全量回退（2026-09-05 用户裁定——枪/刀对玩家右键失效 bisect；
    // 失忆/窃贼尸体交互与女仆赠予待以 G 键/UI 重做） =====

    // ===== tick 派发（END_SERVER_TICK → defs） =====

    static void registerTickHandlers() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (var world : server.getWorlds()) {
                if (!(world instanceof ServerWorld serverWorld)) continue;
                GameWorldComponent gwc = GameWorldComponent.KEY.get(world);
                if (!(gwc.getGameMode() instanceof BeforeTheTerminalGameMode)) continue;
                if (!gwc.isRunning()) continue;

                for (var player : world.getPlayers()) {
                    BttState.decrementDrunk(player.getUuid()); // 醉酒计时（BT-SYS-DRUNK）
                    BttRoleDef d = BttRoleDefs.get(gwc.getRole(player));
                    if (d != null) d.dispatchTick(player, serverWorld, gwc);
                }
            }
        });
    }

    // ===== HML 池隔离 =====

    private static void guardHmlPool() {
        org.agmas.harpymodloader.config.HarpyModLoaderConfig.HANDLER.load();
        boolean changed = false;
        // C-043：GUESSER Role 恢复（刺客接管）但禁入 HML murder 池（HML 走 modifier 版 guesser）
        if (!org.agmas.harpymodloader.config.HarpyModLoaderConfig.HANDLER.instance().disabled.contains("noellesroles:guesser")) {
            org.agmas.harpymodloader.config.HarpyModLoaderConfig.HANDLER.instance().disabled.add("noellesroles:guesser");
            changed = true;
        }
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
