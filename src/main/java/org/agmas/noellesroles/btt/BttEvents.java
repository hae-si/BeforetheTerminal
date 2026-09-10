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
import net.minecraft.entity.player.PlayerEntity;
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
import java.util.UUID;

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
        registerPharmacistPoison();
        BttGuessReceiver.register();
        BttShopGate.init();
        registerMaidGive();
        registerPsychopathShield();
        guardHmlPool();
    }

    // ===== 精神病人护盾（doc：拿出球棒获得护盾，直到杀死一个人——球棒冷却期间无盾） =====

    private static void registerPsychopathShield() {
        // 花匠护盾（C-062）：花匠存活且场上有花 → 否决死亡并移除一株花
        AllowPlayerDeath.EVENT.register((victim, killer, reason) -> {
            if (!(victim.getWorld() instanceof net.minecraft.server.world.ServerWorld sw)) return true;
            if (!BttIdentity.isBttMode(sw)) return true;
            GameWorldComponent gwc0 = GameWorldComponent.KEY.get(sw);
            if (!gwc0.isRole(victim, org.agmas.noellesroles.btt.BttRoles.GARDENER)) return true;
            if (BttFlowers.count(sw) <= 0) return true;
            BttFlowers.removeOne(sw);
            victim.sendMessage(net.minecraft.text.Text.literal("一株小花替你枯萎了……")
                    .formatted(net.minecraft.util.Formatting.GREEN), true);
            return false;
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
        // 精神病人：球棒冷却期间不可再用球棒击杀（否则 CD 形同虚设）
        AllowPlayerDeath.EVENT.register((victim, killer, reason) -> {
            if (reason != GameConstants.DeathReasons.BAT) return true;
            if (!(killer instanceof PlayerEntity k)) return true;
            if (!BttIdentity.isBttMode(victim.getWorld())) return true;
            GameWorldComponent gwc = GameWorldComponent.KEY.get(victim.getWorld());
            if (!gwc.isRole(k, BttRoles.PSYCHOPATH)) return true;
            if (k.getItemCooldownManager().isCoolingDown(WatheItems.BAT)) return false; // 冷却中不可杀
            return true;
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
            // 护盾公式：2 + 先前被误杀人数（本局累计）
            psycho.setArmour(2 + org.agmas.noellesroles.btt.BttGameWorldComponent.KEY.get(victim.getWorld()).misfireCount);
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
            if (!gwc.isRole(victim, BttRoles.STAR)) return true;
            // 暴乱存活 → 明星枪免失效（处决明星也应死亡）
            for (PlayerEntity p : victim.getWorld().getPlayers()) {
                if (gwc.isRole(p, BttRoles.RIOT) && GameFunctions.isPlayerAliveAndSurvival(p)) return true;
            }
            return false; // 明星枪免
        });
    }

    // ===== 药剂师：识别下毒餐盘（wathe CanSeePoison 原生事件） =====

    private static void registerPharmacistPoison() {
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
                    BttPlayerComponent.KEY.get(player).decrementDrunk(); // 醉酒计时（BT-SYS-DRUNK）
                    BttRoleDef d = BttRoleDefs.get(gwc.getRole(player));
                    if (d != null) d.dispatchTick(player, serverWorld, gwc);
                }

                // 恐怖分子炸弹状态机（任何持有者；参照 NRS Bomber）
                for (var p : world.getPlayers()) {
                    BttPlayerComponent bc = BttPlayerComponent.KEY.get(p);
                    if (bc.bombTransferCd > 0) bc.bombTransferCd--;
                    if (!bc.bombPlaced) continue;
                    if (!GameFunctions.isPlayerAliveAndSurvival(p)) { // 持有者非炸弹死亡 → 炸弹消失
                        bc.bombPlaced = false;
                        bc.bombBeeping = false;
                        BttBomb.clear(p); // 收回 [炸弹箱]（尸体不掉炸弹）
                        bc.sync();
                        continue;
                    }
                    if (!bc.bombBeeping) {
                        if (bc.bombTimer > 0) bc.bombTimer--;
                        else {
                            bc.bombBeeping = true;
                            bc.beepTimer = BttBomb.COUNTDOWN_TICKS;
                            BttBomb.reveal(p); // doc「5 秒后可见」：倒计时开始发 [炸弹箱] 到持有者身上
                            bc.sync(); // 客户端 G 键只在倒计时阶段可传出（BttAbilityKey）
                        }
                    } else if (bc.beepTimer > 0) {
                        if (bc.beepTimer % 6 == 0) {
                            world.playSound(null, p.getBlockPos(), net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
                                    net.minecraft.sound.SoundCategory.PLAYERS, 2.0F, 1.0F);
                        }
                        int sec = (bc.beepTimer + 19) / 20;
                        if (sec != bc.bombLastSec) {
                            bc.bombLastSec = sec;
                            p.sendMessage(net.minecraft.text.Text.literal("炸弹倒计时：" + sec + " 秒")
                                    .formatted(net.minecraft.util.Formatting.RED, net.minecraft.util.Formatting.BOLD), true);
                        }
                        bc.beepTimer--;
                    } else {
                        bc.bombPlaced = false;
                        bc.bombBeeping = false;
                        BttBomb.clear(p);
                        bc.sync();
                        world.playSound(null, p.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
                                net.minecraft.sound.SoundCategory.PLAYERS, 3.0F, 1.0F);
                        world.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION,
                                p.getX(), p.getY() + 0.5, p.getZ(), 2, 0, 0, 0, 0);
                        ServerPlayerEntity bomber = null;
                        if (!bc.bombSource.isEmpty() && world.getPlayerByUuid(java.util.UUID.fromString(bc.bombSource)) instanceof ServerPlayerEntity s) {
                            bomber = s;
                        }
                        GameFunctions.killPlayer(p, true, bomber, BttDeathReasons.BOMB);
                    }
                }

                // 记者：<跟踪> 持续透视（标记目标；未标记时透视离自己最远的人）
                java.util.Set<UUID> glow = new java.util.HashSet<>();
                for (var p : world.getPlayers()) {
                    if (!GameFunctions.isPlayerAliveAndSurvival(p) || !gwc.isRole(p, BttRoles.JOURNALIST)) continue;
                    BttPlayerComponent c = BttPlayerComponent.KEY.get(p);
                    UUID marked = null;
                    if (!c.markedTarget.isEmpty()) {
                        try {
                            marked = UUID.fromString(c.markedTarget);
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    if (marked == null) {
                        ServerPlayerEntity far = null;
                        double best = -1;
                        for (var o : world.getPlayers()) {
                            if (o == p || !GameFunctions.isPlayerAliveAndSurvival(o)) continue;
                            double dist = p.squaredDistanceTo(o);
                            if (dist > best) {
                                best = dist;
                                far = o;
                            }
                        }
                        if (far != null) marked = far.getUuid();
                    }
                    if (marked != null) glow.add(marked);
                }
                // 工程师：<扫描> 透视全车 10 秒（200t 逐 tick 递减；沿用记者同款 setGlowing 通道）
                for (var p : world.getPlayers()) {
                    BttPlayerComponent c = BttPlayerComponent.KEY.get(p);
                    if (c.engineerScanTicks <= 0) continue;
                    if (!GameFunctions.isPlayerAliveAndSurvival(p)) {
                        c.engineerScanTicks = 0;
                        continue;
                    }
                    for (var o : world.getPlayers()) {
                        if (o != p && GameFunctions.isPlayerAliveAndSurvival(o)) glow.add(o.getUuid());
                    }
                    c.engineerScanTicks--;
                }
                for (var p : world.getPlayers()) {
                    boolean should = glow.contains(p.getUuid()) && GameFunctions.isPlayerAliveAndSurvival(p);
                    if (p.isGlowing() != should) p.setGlowing(should);
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
