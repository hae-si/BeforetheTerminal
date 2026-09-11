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
    /** 技能冷却/初始冷却统一值 = 1 分钟（C-099；doc「大多数冷却时长已统一为一分钟」；供 `mixin/btt` 复用） */
    public static final int CD_1MIN = GameConstants.getInTicks(1, 0);
    /** doc 特别说明的 30 秒档（走私犯/炼金术士/恐怖分子/虐待狂/派对主/花匠/小说家猜错） */
    public static final int CD_30S = GameConstants.getInTicks(0, 30);
    private static final Map<dev.doctor4t.wathe.api.Role, BttRoleDef> DEFS = new HashMap<>();

    public static void init() {
        // ===== 初始物品 =====
        def(BttRoles.GODFATHER).kit(knife());
        // 食人族：无道具；<习得技能>（平民尸体）CD 1 分钟（docx）→ 初始 CD 同值（C-110）
        def(BttRoles.CANNIBAL).kit(p -> initialAbilityCd(p, CD_1MIN));
        // 哲人：无道具；<领悟> 仅限一次（docx），初始 CD 按 C-099 统一 1 分钟（C-110）
        def(BttRoles.PHILOSOPHER).kit(p -> initialAbilityCd(p, CD_1MIN));
        // 饕餮：初始[撬棍]（docx）+ <绑架> 身边者（C-109）；冷却 1 分钟（docx）→ 初始 CD 同值
        def(BttRoles.KIDNAPPER).kit(p -> {
            p.giveItemStack(new ItemStack(WatheItems.CROWBAR));
            initialAbilityCd(p, CD_1MIN);
        });
        // 偷渡客：初始[刀]；<隐蔽> 冷却 2 分钟 → 初始 CD 同值（C-107/C-099；<假尸> 已按用户 2026-09-11 回退）
        def(BttRoles.STOWAWAY).kit(p -> {
            knife().give(p);
            initialAbilityCd(p, GameConstants.getInTicks(2, 0));
        });
        def(BttRoles.ACTOR).kit(p -> knife().give(p)); // 演员：初始[刀]；<装死> 开关 + <易容> 均**无冷却**（C-106）
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
        // 猎人：C-099 起给初始 CD 1 分钟（doc「所有技能都有初始冷却」；此前为 0 以免开局选人件被灰）
        BttRoleDef hunter = def(BttRoles.HUNTER);
        hunter.kit(p -> {
            p.giveItemStack(new ItemStack(WatheItems.REVOLVER));
            initialAbilityCd(p, CD_1MIN);
        });
        revolverKit(BttRoles.BANDIT);
        // 魔女：初始[枪]+[撬棍]（docx；外人枪不扔枪/1 分钟 CD 走 BttExecutionMixin 外人分支）
        def(BttRoles.MAJO).kit(p -> {
            p.giveItemStack(new ItemStack(WatheItems.REVOLVER));
            p.giveItemStack(new ItemStack(WatheItems.CROWBAR));
        });
        // 救世主：初始[撬棍]；<预知> 冷却 1 分钟、**包括初始冷却**（docx 2026-09-11）
        def(BttRoles.MESSIAH).kit(p -> {
            p.giveItemStack(new ItemStack(WatheItems.CROWBAR));
            initialAbilityCd(p, CD_1MIN);
        });
        // ===== 醉酒投放者（BT-SYS-DRUNK，C-060） =====
        def(BttRoles.BARTENDER).kit(p -> initialAbilityCd(p, CD_1MIN)); // 酒保：<灌酒> 1 分钟（docx）
        def(BttRoles.JOURNALIST).kit(p -> initialAbilityCd(p, CD_1MIN)); // 记者：<跟踪> 1 分钟（docx 2026-09-11：30s→1min）
        def(BttRoles.MINSTREL).kit(p -> initialAbilityCd(p, CD_1MIN));  // 吟游诗人：<歌唱> 1 分钟（docx 2026-09-11：2min→1min）
        // 教授：<使用药剂> 身边者（给予 1 层护盾，免疫下一次致命伤；C-104）；无道具；CD 1 分钟（C-099：docx 未特别说明）
        def(BttRoles.PROFESSOR).kit(p -> initialAbilityCd(p, CD_1MIN));
        // 花匠：初始[撬棍]（docx）+ <栽培> G 键直发（C-090）；冷却 30 秒（docx）
        // 此前整条 def 漏登记 → 客户端 `BttRoleDefs.get()` 返回 null，G 键被 `def == null` 静默拦截（技能不可达）
        def(BttRoles.GARDENER).kit(p -> {
            p.giveItemStack(new ItemStack(WatheItems.CROWBAR));
            initialAbilityCd(p, CD_30S);
        });
        def(BttRoles.SMUGGLER).kit(p -> { // 走私犯：初始[刀]（<灌酒> 直接灌）；CD 30 秒（docx）
            knife().give(p);
            initialAbilityCd(p, CD_30S);
        });
        def(BttRoles.ABUSER).kit(p -> { // 虐待狂：初始[刀]（<缄默> 身边者，C-086）；CD 30 秒（docx）
            knife().give(p);
            initialAbilityCd(p, CD_30S);
        });
        // 派对主：C-099 补齐 def（此前整条漏登记 → 与 C-090 花匠同因，`def == null` 静默吞掉但技能）；初始[刀] + CD 30 秒
        def(BttRoles.PARTYHOST).kit(p -> {
            knife().give(p);
            initialAbilityCd(p, CD_30S);
        });
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
        def(BttRoles.DETECTIVE).kit(p -> initialAbilityCd(p, CD_1MIN)); // <调查> 1 分钟
        def(BttRoles.RIGGER).kit(p -> initialAbilityCd(p, CD_1MIN)); // <拘束> 1 分钟
        def(BttRoles.PHARMACIST).kit(p -> initialAbilityCd(p, CD_1MIN)); // <喂药> 1 分钟
        def(BttRoles.ENGINEER).kit(p -> initialAbilityCd(p, CD_1MIN)); // <扫描> 1 分钟
        def(BttRoles.ARCHITECT).kit(p -> initialAbilityCd(p, CD_1MIN)); // <修复> G 键直发；1 分钟（docx 2026-09-11：2min→1min）
        // 梦游病：<入梦> 灵魂出窍（C-087）；无道具；C-099 起初始 CD 1 分钟（doc「冷却一分钟」+「所有技能都有初始冷却」）
        def(BttRoles.MEYUUBYOU)
                .kit(p -> initialAbilityCd(p, CD_1MIN))
                .onTick((player, world, gwc) -> BttSpirit.tick(player, gwc));
        // 窃贼：初始[万能钥匙]；<搜刮> docx 未给冷却 → C-099 按统一值 1 分钟（初始 CD 同值）
        def(BttRoles.THIEF).kit(p -> {
            p.giveItemStack(new ItemStack(WatheItems.KEY));
            initialAbilityCd(p, CD_1MIN);
        });
        // 纵火犯：初始[万能钥匙] + <浇汽油> G 键直发（C-092 抄 NRS 病原体；初始 CD 10s，动态冷却见 BttArsonist）
        def(BttRoles.ARSONIST).kit(p -> {
            p.giveItemStack(new ItemStack(WatheItems.KEY));
            initialAbilityCd(p, BttArsonist.INITIAL_CD_TICKS);
        });

        // ===== BT-P2-UI 五身份（选人 UI；冷却载体=NR AbilityPlayerComponent 自动同步） =====
        // 预言家：无道具；初始 CD 60s
        def(BttRoles.PROPHET).kit(p -> initialAbilityCd(p, CD_1MIN));
        // 刺客：初始[刀]；初始 CD 60s
        def(BttRoles.ASSASSIN).kit(p -> {
            knife().give(p);
            initialAbilityCd(p, CD_1MIN);
        });
        // 魔术师：初始[刀]；<交换> 用后 1 分钟（NR 原生 setCooldown）+ C-099 初始 CD 1 分钟
        def(BttRoles.MAGICIAN).kit(p -> {
            knife().give(p);
            initialAbilityCd(p, CD_1MIN);
        });
        // 小说家：无道具；猜错进入 30s 冷却，C-099 起另给初始 CD 1 分钟
        def(BttRoles.NOVELIST).kit(p -> initialAbilityCd(p, CD_1MIN));
        // 舞蛇人：无道具；初始 CD 60s
        def(BttRoles.SNAKE_CHARMER).kit(p -> initialAbilityCd(p, CD_1MIN));
        // 小恶魔（取代冒牌货）：初始[刀]；<印记> 冷却 1 分钟（「更换选择冷却一分钟」）→ 初始 CD 同值
        def(BttRoles.IMP).kit(p -> {
            knife().give(p);
            initialAbilityCd(p, CD_1MIN);
        });
        // 失忆患者：注视尸体取回遗物（BttCorpseActionC2SPacket）；C-099 补齐 def（同因：漏登记致 G 键不可达）
        def(BttRoles.AMNESIAC);

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

    /**
     * C-084：BTT 自有物品 kit（冷却写在物品自身，不走 AbilityPlayerComponent）。
     * C-099：发放时即套用该物品的冷却（doc「所有技能都有初始冷却」）——匕首 1 分钟 / 毒针·炸弹 30 秒。
     */
    private static BttRoleDef.Kit item(Item item) {
        return p -> {
            p.giveItemStack(new ItemStack(item));
            Integer cd = GameConstants.ITEM_COOLDOWNS.get(item);
            if (cd != null) p.getItemCooldownManager().set(item, cd);
        };
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
