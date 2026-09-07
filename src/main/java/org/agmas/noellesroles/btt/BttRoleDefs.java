package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import org.agmas.noellesroles.AbilityPlayerComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashMap;
import java.util.Map;

/**
 * BT-ARCH-001：BTT 身份声明式定义表（唯一事实源）。
 * 新增身份 = 在此追加一个 {@code def(...)} 条目；禁止回到 if-chain（CLEAN-005）。
 * 冷却载体约定（CLEAN-004）：优先 {@link ItemCooldownManager}（UI 可见），回合级计数走 {@link BttState}。
 */
public final class BttRoleDefs {
    private BttRoleDefs() {}

    /** 老兵刀使用次数上限（doc：只能使用三次） */
    static final int VETERAN_KNIFE_USES = 3;
    private static final Map<dev.doctor4t.wathe.api.Role, BttRoleDef> DEFS = new HashMap<>();

    public static void init() {
        // ===== 初始物品 =====
        def(BttRoles.GODFATHER).kit(knife());
        def(BttRoles.ACTOR).kit(knife());
        def(BttRoles.WITCH).kit(p -> {
            knife().give(p);
            BttState.setInt(p.getUuid(), "witchUses", 1); // 刀限一次
        });
        def(BttRoles.CLEANER).kit(knife());
        def(BttRoles.SWORDSMAN).kit(knife()); // 剑客（docx 改名）：[剑] 飞剑 GAP，暂以刀代
        def(BttRoles.VETERAN).kit(p -> {
            knife().give(p);
            BttState.setInt(p.getUuid(), "veteranUses", VETERAN_KNIFE_USES);
        });
        revolverKit(BttRoles.VIGILANTE);
        revolverKit(BttRoles.RAILWAY_POLICE);
        revolverKit(BttRoles.HUNTER);
        // 猎人 UI 初始 CD=0（覆盖 NR generalCooldownTicks，否则开局选人件被灰）
        BttRoleDef hunter = def(BttRoles.HUNTER);
        hunter.kit(p -> {
            p.giveItemStack(new ItemStack(WatheItems.REVOLVER));
            initialAbilityCd(p, 0);
        });
        revolverKit(BttRoles.BANDIT);
        revolverKit(BttRoles.NIGHT_WATCHMAN);
        // 魔女：初始[枪]+[撬棍]（docx；外人枪不扔枪/1 分钟 CD 走 BttExecutionMixin 外人分支）
        def(BttRoles.MAJO).kit(p -> {
            p.giveItemStack(new ItemStack(WatheItems.REVOLVER));
            p.giveItemStack(new ItemStack(WatheItems.CROWBAR));
        });
        // 救世主：初始[撬棍]；<预知> 初始冷却 2 分钟（docx）
        def(BttRoles.MESSIAH).kit(p -> {
            p.giveItemStack(new ItemStack(WatheItems.CROWBAR));
            initialAbilityCd(p, GameConstants.getInTicks(2, 0));
        });
        def(BttRoles.PSYCHOPATH).kit(p -> p.giveItemStack(new ItemStack(WatheItems.BAT)));
        def(BttRoles.DETECTIVE).kit(p -> initialAbilityCd(p, GameConstants.getInTicks(1, 0))); // <调查> G 键技能
        def(BttRoles.RIGGER).kit(p -> initialAbilityCd(p, GameConstants.getInTicks(1, 0))); // <拘束> G 键技能
        def(BttRoles.PHARMACIST).kit(p -> initialAbilityCd(p, GameConstants.getInTicks(1, 0))); // <给糖> G 键技能
        def(BttRoles.THIEF).kit(p -> p.giveItemStack(new ItemStack(WatheItems.KEY)));      // 万能钥匙

        // ===== BT-P2-UI 五身份（选人 UI；冷却载体=NR AbilityPlayerComponent 自动同步） =====
        // 预言家：无道具；初始 CD 60s
        def(BttRoles.PROPHET).kit(p -> initialAbilityCd(p, GameConstants.getInTicks(1, 0)));
        // 刺客：初始[刀]；初始 CD 60s
        def(BttRoles.ASSASSIN).kit(p -> {
            knife().give(p);
            initialAbilityCd(p, GameConstants.getInTicks(1, 0));
        });
        // 魔术师：初始[刀]；无 CD（每次换位耗 100 狂气）
        def(BttRoles.MAGICIAN).kit(knife());
        // 小说家：无道具无初始 CD（猜错才 30s）
        def(BttRoles.NOVELIST).kit(p -> {});
        // 舞蛇人：无道具；初始 CD 60s
        def(BttRoles.SNAKE_CHARMER).kit(p -> initialAbilityCd(p, GameConstants.getInTicks(1, 0)));

        // ===== P2A-002 补全 =====
        // 女仆：赠予手持的食物/饮料（双倍取餐在 BttMaidPlatterMixin）
                // 邮差：搁置四星（用户裁定 2026-09-05——手持物右键消费整次交互，破坏枪/刀对玩家使用）

        // ===== 击杀钩子（BttKillHookMixin 派发；全局杀人历史/祭品协议在 mixin 内先行） =====

        // 老兵：刀 3 次（递减+移除）
        def(BttRoles.VETERAN).onKill((shooter, victim, reason, gwc) -> {
            if (reason != GameConstants.DeathReasons.KNIFE) return;
            int uses = BttState.getInt(shooter.getUuid(), "veteranUses") - 1;
            BttState.setInt(shooter.getUuid(), "veteranUses", uses);
            if (uses <= 0) removeOne(shooter, WatheItems.KNIFE);
        });

        // 巫觋：刀限一次（击杀后移除）
        def(BttRoles.WITCH).onKill((shooter, victim, reason, gwc) -> {
            if (reason != GameConstants.DeathReasons.KNIFE) return;
            BttState.setInt(shooter.getUuid(), "witchUses", 0);
            removeOne(shooter, WatheItems.KNIFE);
        });

        // 强盗：条件 CD——受害者=乘客→60s，否则→wathe 原生
        def(BttRoles.BANDIT).onKill((shooter, victim, reason, gwc) -> {
            if (reason != GameConstants.DeathReasons.GUN) return;
            int cd = gwc.isInnocent(victim)
                    ? GameConstants.getInTicks(1, 0)
                    : GameConstants.ITEM_COOLDOWNS.getOrDefault(WatheItems.REVOLVER, 200);
            shooter.getItemCooldownManager().set(WatheItems.REVOLVER, cd);
        });

        // 精神病人：击杀后球棒 60s CD（doc：杀死一个人后进入冷却；护盾=AllowPlayerDeath 否决，C-039）
        def(BttRoles.PSYCHOPATH).onKill((shooter, victim, reason, gwc) -> {
            if (reason != GameConstants.DeathReasons.BAT) return;
            shooter.getItemCooldownManager().set(WatheItems.BAT, GameConstants.getInTicks(1, 0));
        });

        // ===== tick 钩子 =====

        // 义警：理智锁满（docx 2026-09-07 更新：义警=无理智限制；乘警改为无体力限制，注册旗标 maxSprintTime=-1 已覆盖）
        def(BttRoles.VIGILANTE).onTick((player, world, gwc) ->
                PlayerMoodComponent.KEY.get(player).setMood(1.0f));

        // 司机：存活 → 倒计时额外 -1 tick/tick（×2 速率；审计修复后挂 DRIVER）
        def(BttRoles.DRIVER).onTick((player, world, gwc) -> {
            if (GameFunctions.isPlayerAliveAndSurvival(player)) {
                dev.doctor4t.wathe.cca.GameTimeComponent.KEY.get(world).addTime(-1);
            }
        });

        // 小丑：疯魔中锁手持球棒；疯魔结束回收球棒（doc 疯魔模式限定）
        def(BttRoles.JESTER).onTick((player, world, gwc) -> {
            PlayerPsychoComponent psycho = PlayerPsychoComponent.KEY.get(player);
            if (psycho.getPsychoTicks() > 0) {
                for (int i = 0; i < player.getInventory().size(); i++) {
                    if (player.getInventory().getStack(i).isOf(WatheItems.BAT)) {
                        if (player.getInventory().selectedSlot != i) {
                            player.getInventory().selectedSlot = i;
                        }
                        break;
                    }
                }
            } else if (hasItem(player, WatheItems.BAT)) {
                removeOne(player, WatheItems.BAT);
            }
        });

        // ===== 实体交互钩子（UseEntityCallback 派发） =====

        // 卖糖人<给糖>：解毒（醒酒依赖 BT-SYS-DRUNK，TODO），CD 60s
        
        // 绳艺师<拘束>：Slowness 255 · 15s，CD 60s
        
        // 侦探<调查>：望远镜点击→有没有杀过人，CD 60s（身边者口径以点击近似，已登记）
        
        // 失忆患者：尸体→死者身份→获对应初始物品（每具一次）
        
        // 窃贼<搜刮>：尸体消失+计数（过半独胜判定在 GameMode tick；透视 10s TODO=BT-THIEF-ESP）
            }

    // ===== 查询/派发 =====

    public static BttRoleDef get(dev.doctor4t.wathe.api.Role role) {
        return role == null ? null : DEFS.get(role);
    }

    public static int defCount() {
        return DEFS.size();
    }

    private static BttRoleDef def(dev.doctor4t.wathe.api.Role role) {
        return DEFS.computeIfAbsent(role, BttRoleDef::of);
    }

    private static BttRoleDef.Kit knife() {
        return p -> p.giveItemStack(new ItemStack(WatheItems.KNIFE));
    }

    /** UI 身份初始冷却（AbilityPlayerComponent 自动同步；BttPlayerWidget 显示倒计时） */
    private static void initialAbilityCd(ServerPlayerEntity p, int ticks) {
        AbilityPlayerComponent a = AbilityPlayerComponent.KEY.get(p);
        a.setCooldown(ticks);
        a.sync();
    }

    private static void revolverKit(dev.doctor4t.wathe.api.Role role) {
        def(role).kit(p -> p.giveItemStack(new ItemStack(WatheItems.REVOLVER)));
    }

    // ===== 恶魔凝视（推迟测试：机制保留，2026-09-05 用户裁定往后推） =====

    /** 视野（约 ±60° 锥角）+ 视线（方块遮挡检测） */
    private static boolean inView(ServerWorld world, LivingEntity viewer, LivingEntity target) {
        Vec3d eyes = viewer.getEyePos();
        Vec3d targetEyes = target.getEyePos();
        Vec3d dir = targetEyes.subtract(eyes);
        if (dir.lengthSquared() < 1.0E-4) return true;
        double dot = viewer.getRotationVec(1.0f).normalize().dotProduct(dir.normalize());
        if (dot < 0.5) return false;
        BlockHitResult hit = world.raycast(new RaycastContext(eyes, targetEyes,
                RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, viewer));
        return hit.getType() == HitResult.Type.MISS;
    }

    // ===== 通用 =====

    static boolean hasItem(ServerPlayerEntity player, Item item) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (player.getInventory().getStack(i).isOf(item)) return true;
        }
        return false;
    }

    static void removeOne(ServerPlayerEntity player, Item item) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(item)) {
                stack.setCount(0);
                player.getInventory().markDirty();
                return;
            }
        }
    }
}
