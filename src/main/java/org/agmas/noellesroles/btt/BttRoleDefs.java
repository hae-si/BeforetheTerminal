package org.agmas.noellesroles.btt;

import org.agmas.noellesroles.AbilityPlayerComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.agmas.noellesroles.ModItems;

import java.util.HashMap;
import java.util.Map;

/**
 * BT-ARCH-001：BTT 身份声明式定义表（唯一事实源）。
 * 新增身份 = 在此追加一个 {@code def(...)} 条目；禁止回到 if-chain（CLEAN-005）。
 * 冷却载体约定（CLEAN-004）：优先 {@link ItemCooldownManager}（UI 可见），回合级计数走 {@link BttPlayerComponent}。
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
            BttPlayerComponent.KEY.get(p).witchUses = 1; // 刀限一次
        });
        // 清道夫：初始[匕首]（C-084；即时·无声·1 分钟冷却写在物品上，不再是 wathe 刀 + mixin）
        def(BttRoles.CLEANER).kit(item(ModItems.DAGGER));
        // 炼金术士：初始[毒针]（C-084）
        def(BttRoles.ALCHEMIST).kit(item(ModItems.POISON_NEEDLE));
        // 恐怖分子：初始[炸弹箱]（C-084；安放从 G 键技能改为物品右键，物品 30 秒冷却）
        def(BttRoles.TERRORIST).kit(item(ModItems.BOMB));
        def(BttRoles.SWORDSMAN).kit(knife()); // 剑客（docx 改名）：[剑] 飞剑 GAP，暂以刀代
        def(BttRoles.VETERAN).kit(p -> {
            knife().give(p);
            BttPlayerComponent.KEY.get(p).veteranUses = VETERAN_KNIFE_USES;
        });
        revolverKit(BttRoles.VIGILANTE);
        revolverKit(BttRoles.RAILWAY_POLICE);
        revolverKit(BttRoles.LAWYER);
        revolverKit(BttRoles.HUNTER);
        // 猎人 UI 初始 CD=0（覆盖 NR generalCooldownTicks，否则开局选人件被灰）
        BttRoleDef hunter = def(BttRoles.HUNTER);
        hunter.kit(p -> {
            p.giveItemStack(new ItemStack(WatheItems.REVOLVER));
            initialAbilityCd(p, 0);
        });
        revolverKit(BttRoles.BANDIT);
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
        // ===== 醉酒投放者（BT-SYS-DRUNK，C-060） =====
        def(BttRoles.BARTENDER); // 酒保：无初始道具（<灌酒> G 键选人）
        def(BttRoles.JOURNALIST); // 记者：无初始道具（<跟踪> E 屏选人 + 持续透视）
        def(BttRoles.MINSTREL);  // 吟游诗人：无初始道具（<歌唱> G 键直发）
        // 花匠：初始[撬棍]（docx）+ <栽培> G 键直发（C-090）
        // 此前整条 def 漏登记 → 客户端 `BttRoleDefs.get()` 返回 null，G 键被 `def == null` 静默拦截（技能不可达）
        def(BttRoles.GARDENER).kit(p -> p.giveItemStack(new ItemStack(WatheItems.CROWBAR)));
        def(BttRoles.SMUGGLER).kit(knife()); // 走私犯：初始[刀]（<灌酒> 简化为直接灌）
        def(BttRoles.ABUSER).kit(knife()); // 虐待狂：初始[刀]（<缄默> 身边者，C-086）
        // ===== docx 2026-09-09 新增（C-063） =====
        def(BttRoles.POPPY_GROWER); // 罂粟农：被动天赋（本能全绿），无 kit
        def(BttRoles.AGENT).kit(knife()); // 特工：初始[刀]（<查看> G 键直发）
        def(BttRoles.RIOT).kit(knife()); // 暴乱：初始[刀]
        def(BttRoles.VORTOX).kit(knife()); // 涡流：初始[刀]
        // 涡流：存活时所有乘客持续醉酒（每 tick 施加 2t 维持量）
        def(BttRoles.VORTOX).onTick((player, world, gwc) -> {
            if (!GameFunctions.isPlayerAliveAndSurvival(player)) return;
            for (var p : world.getPlayers()) {
                if (!GameFunctions.isPlayerAliveAndSurvival(p)) continue;
                var f = BttRoles.factionOf(gwc.getRole(p));
                if (f == BttRoles.Faction.ENFORCER || f == BttRoles.Faction.CIVILIAN || f == BttRoles.Faction.MAD) {
                    if (p != player) BttPlayerComponent.KEY.get(p).applyDrunk(2);
                }
            }
        });
        // ===== 叛徒系（C-063：狂人席位+凶手阵营） =====
        def(BttRoles.TRAITOR).kit(knife()); // 叛徒：[刀]+商店
        def(BttRoles.EX_TRAITOR).kit(knife()); // 前任叛徒：继承（D16）后 [刀]
        // ===== 骑士/游侠（docx 2026-09-09：弓删改枪） =====
        def(BttRoles.CABALLERO).kit(p -> p.giveItemStack(new ItemStack(WatheItems.REVOLVER)));
        def(BttRoles.RANGER).kit(p -> p.giveItemStack(new ItemStack(WatheItems.REVOLVER)));
        def(BttRoles.PSYCHOPATH).kit(p -> p.giveItemStack(new ItemStack(WatheItems.BAT)));
        def(BttRoles.DETECTIVE).kit(p -> initialAbilityCd(p, GameConstants.getInTicks(1, 0))); // <调查> G 键技能
        def(BttRoles.RIGGER).kit(p -> initialAbilityCd(p, GameConstants.getInTicks(1, 0))); // <拘束> G 键技能
        def(BttRoles.PHARMACIST).kit(p -> initialAbilityCd(p, GameConstants.getInTicks(1, 0))); // <给糖> G 键技能
        def(BttRoles.ENGINEER).kit(p -> initialAbilityCd(p, GameConstants.getInTicks(1, 0))); // <扫描> G 键直发
        def(BttRoles.ARCHITECT).kit(p -> initialAbilityCd(p, GameConstants.getInTicks(2, 0))); // <修复> G 键直发；初始 CD 2 分钟（docx）
        // 梦游病：<入梦> 灵魂出窍（C-087）；无道具、初始无 CD（doc 只规定用后 1 分钟），异常状态校验走 onTick
        def(BttRoles.MEYUUBYOU)
                .kit(p -> initialAbilityCd(p, 0))
                .onTick((player, world, gwc) -> BttSpirit.tick(player, gwc));
        def(BttRoles.THIEF).kit(p -> p.giveItemStack(new ItemStack(WatheItems.KEY)));      // 万能钥匙

        // ===== BT-P2-UI 五身份（选人 UI；冷却载体=NR AbilityPlayerComponent 自动同步） =====
        // 预言家：无道具；初始 CD 60s
        def(BttRoles.PROPHET).kit(p -> initialAbilityCd(p, GameConstants.getInTicks(1, 0)));
        // 刺客：初始[刀]；初始 CD 60s
        def(BttRoles.ASSASSIN).kit(p -> {
            knife().give(p);
            initialAbilityCd(p, GameConstants.getInTicks(1, 0));
        });
        // 魔术师：初始[刀]；无 CD（耗 100 狂气为旧设定，已废弃）
        def(BttRoles.MAGICIAN).kit(knife());
        // 小说家：无道具无初始 CD（猜错才 30s）
        def(BttRoles.NOVELIST).kit(p -> {});
        // 舞蛇人：无道具；初始 CD 60s
        def(BttRoles.SNAKE_CHARMER).kit(p -> initialAbilityCd(p, GameConstants.getInTicks(1, 0)));

        // ===== P2A-002 补全 =====
        // 女仆：赠予手持的食物/饮料（双倍取餐在 BttMaidPlatterMixin）
        // 邮差：仅注册（无行为；与其余仅注册身份同）

        // ===== 击杀钩子（BttKillHookMixin 派发；全局杀人历史/祭品协议在 mixin 内先行） =====

        // 老兵：刀 3 次（递减+移除）
        def(BttRoles.VETERAN).onKill((shooter, victim, reason, gwc) -> {
            if (reason != GameConstants.DeathReasons.KNIFE) return;
            BttPlayerComponent comp = BttPlayerComponent.KEY.get(shooter);
            comp.veteranUses--;
            if (comp.veteranUses <= 0) removeOne(shooter, WatheItems.KNIFE);
        });

        // 巫觋：刀限一次（击杀后移除）
        def(BttRoles.WITCH).onKill((shooter, victim, reason, gwc) -> {
            if (reason != GameConstants.DeathReasons.KNIFE) return;
            BttPlayerComponent.KEY.get(shooter).witchUses = 0;
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

    /** C-084：BTT 自有物品 kit（冷却写在物品自身，不走 AbilityPlayerComponent） */
    private static BttRoleDef.Kit item(Item item) {
        return p -> p.giveItemStack(new ItemStack(item));
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
