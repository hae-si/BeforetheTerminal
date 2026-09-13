package org.agmas.noellesroles.client.mixin.btt;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.btt.BttRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BTT 本能透视挂点（官方 {@link WatheClient#getInstinctHighlight} HEAD，btt.* 段先于 NR InstinctMixin 执行）：
 * ① 教团互相透视（被动，无需按键）：观察者与目标均为教团（救世主/信徒）→ 返回教团色 0xFF00FF；
 * ② 小女孩免疫：观察者具备凶手功能 → 返回 -1（skip，照抄 NRS 生存大师模式）；
 * ③ 独行中立 = **绿色** 0x4EDD35（2026-09-07 用户实测反馈：NR InstinctMixin 会把
 *    KILLER_SIDED_NEUTRALS 成员染成 role.color()、其余"非无辜非凶手"染灰绿——独行接管键
 *    vulture/infected 正中前者；本回调先执行并 cancel，压过 NR 映射，用原版无辜高理智绿）。
 * ④ 花匠自我透视（C-091）：花匠**被动**透视自己全部小花（红树胎生苗/铃兰掉落物，金描边、免按键；
 *    与①教团同款"先于 isInstinctEnabled 门控"的 cancel），且**只**看花——其余实体一律 -1，避免顺带泄漏玩家配色。
 * ⑤ 纵火犯（C-092）：按本能键 → 透视全部**已被浇汽油者**（职业色）；
 * ⑥ 窃贼（C-092）：按本能键 → 透视全部**尸体**（职业色）。
 *    ⑤⑥ 都直读原始键位——官方 1.3.2 的 `isInstinctEnabled()` 要求凶手阵营/旁观，中立身份过不了该门。
 * ⑧ **C-143（作者 2026-09-11 §7）**：记者/工程师/窃贼搜刮后/民俗学家这四类**本机标记透视**
 *    也从"自绘方框"改走**本通道**（原版发光：`hasOutline` + `WorldRenderer` 的 `getTeamColorValue` 改写）——
 *    纯本机、穿墙、无需按键；`BttEntityHighlightRenderer`（方框自绘）随之删除。
 * 其余配色一律沿用原版（2026-09-06 裁定：勿自造配色）。魔女尾声"活人雷达"为过时设定，已移除（2026-09-07）。
 */
@Mixin(WatheClient.class)
public abstract class BttShoujoInstinctMixin {

    /** 原版无辜（高理智）绿——与 NR InstinctMixin 的 5168437 同值 */
    private static final int INSTINCT_GREEN = 0x4EDD35;
    /** 原版掉落物高亮金（WatheClient 对 ItemEntity 返回的 14392576 = 0xDB9D00） */
    private static final int INSTINCT_ITEM_GOLD = 0xDB9D00;
    /** 记者描边色（金，同 C-089 方框口径） */
    private static final int JOURNALIST_GOLD = 0xFFD945;
    /** 工程师描边色（青，同 C-089 方框口径） */
    private static final int ENGINEER_CYAN = 0x4AD1F0;

    @Inject(method = "getInstinctHighlight", at = @At("HEAD"), cancellable = true)
    private static void btt$instinctOverrides(Entity target, CallbackInfoReturnable<Integer> cir) {
        // 尸体（窃贼）/花（花匠）都是非玩家目标，必须在"非玩家即早退"之前判断
        if (!(target instanceof PlayerEntity p) || p.isSpectator()) {
            if (btt$thiefCorpses(target, cir)) return;
            if (btt$morticianSight(target, cir)) return;
            btt$gardenerFlowers(target, cir);
            return;
        }
        var viewer = MinecraftClient.getInstance().player;
        if (viewer == null || viewer == p) return;
        if (!BttIdentity.isBttMode(viewer.getWorld())) return;
        GameWorldComponent gwc = GameWorldComponent.KEY.get(viewer.getWorld());
        // ⑧ C-143：本机标记透视（被动、无需本能键）——记者/工程师/窃贼搜刮后/民俗学家
        if (btt$markerGlow(viewer, p, gwc, cir)) return;
        // ① 教团互相透视（被动，无需按键、无凶手门控——救世主/信徒均非 canUseKiller）：观察者与目标均为教团
        boolean viewerCult = gwc.getRole(viewer) == BttRoles.MESSIAH
                || org.agmas.noellesroles.btt.BttPlayerComponent.KEY.get(viewer).isCult();
        boolean targetCult = gwc.getRole(p) == BttRoles.MESSIAH
                || org.agmas.noellesroles.btt.BttPlayerComponent.KEY.get(p).isCult();
        if (viewerCult && targetCult) {
            cir.setReturnValue(0xFFFF00FF);
            cir.cancel();
            return;
        }
        // ⑤ 纵火犯（C-092）：本能键 → 透视全部**已被浇汽油者**（职业色）；其余配色不变
        if (gwc.getRole(viewer) == BttRoles.ARSONIST
                && GameFunctions.isPlayerAliveAndSurvival(viewer)
                && btt$instinctKeyPressed()
                && org.agmas.noellesroles.btt.BttPlayerComponent.KEY.get(p).isDoused()) {
            cir.setReturnValue(BttRoles.ARSONIST.color());
            cir.cancel();
            return;
        }
        if (!WatheClient.isInstinctEnabled()) return;
        if (!gwc.canUseKillerFeatures(viewer)) return;
        // ⑦ 疯子（C-129b，docx）：疯子透视**真正的主犯凶手**为绿色；主犯凶手透视**疯子**为黄色
        Role viewerRole = gwc.getRole(viewer);
        Role targetRole = gwc.getRole(p);
        if (viewerRole == BttRoles.LUNATIC && targetRole != null
                && BttRoles.factionOf(targetRole) == BttRoles.Faction.PRINCIPAL) {
            cir.setReturnValue(INSTINCT_GREEN);
            cir.cancel();
            return;
        }
        if (targetRole == BttRoles.LUNATIC && viewerRole != null
                && BttRoles.factionOf(viewerRole) == BttRoles.Faction.PRINCIPAL) {
            cir.setReturnValue(0xFFFF00);
            cir.cancel();
            return;
        }
        // ④ 罂粟农（C-063）：存活时凶手本能看所有人都是绿——覆盖下方全部配色
        for (var op : viewer.getWorld().getPlayers()) {
            if (op == viewer || !op.isAlive()) continue;
            if (gwc.getRole(op) == BttRoles.POPPY_GROWER) {
                cir.setReturnValue(INSTINCT_GREEN);
                cir.cancel();
                return;
            }
        }
        Role role = gwc.getRole(p);
        if (role == BttRoles.SHOUJO) {
            cir.setReturnValue(-1);
            cir.cancel();
        } else if (BttRoles.factionOf(role) == BttRoles.Faction.LONE
                || BttRoles.factionOf(role) == BttRoles.Faction.OUTSIDER
                || role == BttRoles.TRAITOR) {
            // 中立（独行/外人/叛徒）统一为原版无辜绿，**压过** NR InstinctMixin 的 role.color()
            // （NR 的 KILLER_SIDED_NEUTRALS 会把窃贼/小丑等染成职业色；必须 cancel）
            cir.setReturnValue(INSTINCT_GREEN);
            cir.cancel();
        } else if (BttRoles.isPassengerCamp(role) && role != BttRoles.BLACKDEATH) {
            // 去掉原版"低理智=蓝"：最低档（<DEPRESSIVE）改用中档青，不再出现蓝色
            float mood = dev.doctor4t.wathe.cca.PlayerMoodComponent.KEY.get(p).getMood();
            if (mood < dev.doctor4t.wathe.game.GameConstants.DEPRESSIVE_MOOD_THRESHOLD) {
                cir.setReturnValue(0x1FAFAF);
                cir.cancel();
            }
        }
    }

    /** C-092：窃贼按本能键透视尸体（职业色）；非尸体一律不接管 */
    private static boolean btt$thiefCorpses(Entity target, CallbackInfoReturnable<Integer> cir) {
        if (!(target instanceof dev.doctor4t.wathe.entity.PlayerBodyEntity)) return false;
        var viewer = MinecraftClient.getInstance().player;
        if (viewer == null) return false;
        if (!BttIdentity.isBttMode(viewer.getWorld())) return false;
        GameWorldComponent gwc = GameWorldComponent.KEY.get(viewer.getWorld());
        if (gwc.getRole(viewer) != BttRoles.THIEF) return false;
        if (!GameFunctions.isPlayerAliveAndSurvival(viewer)) return false;
        if (!btt$instinctKeyPressed()) return false;
        cir.setReturnValue(BttRoles.THIEF.color());
        cir.cancel();
        return true;
    }

    /** 本能键原始按下状态（1.3.2 的 isInstinctEnabled() 含凶手阵营门控，中立身份只能直读键位） */
    private static boolean btt$instinctKeyPressed() {
        return WatheClient.instinctKeybind != null && WatheClient.instinctKeybind.isPressed();
    }

    /**
     * C-143：本机标记透视（原 `BttEntityHighlightRenderer` 的方框自绘改为**原版发光通道**）。
     * 覆盖四类：记者 &lt;跟踪&gt; 的目标、工程师 &lt;扫描&gt; 的全员、窃贼 &lt;搜刮&gt; 后的全员、民俗学家看到的"使用者"。
     * 全部**被动**（不按本能键）且只对**本机**生效（服务端不写旗标 → 不会有 C-089 那种"所有人共享发光"的泄漏）。
     */
    private static boolean btt$markerGlow(PlayerEntity viewer, PlayerEntity target, GameWorldComponent gwc,
                                          CallbackInfoReturnable<Integer> cir) {
        if (!GameFunctions.isPlayerAliveAndSurvival(viewer)) return false;
        org.agmas.noellesroles.btt.BttPlayerComponent own =
                org.agmas.noellesroles.btt.BttPlayerComponent.KEY.get(viewer);
        String uuid = target.getUuidAsString();
        // 记者：只描自己显式标记的那名玩家（无标记 / 标记失效 → 不描；策划已删"自动盯最远者"）
        if (gwc.isRole(viewer, BttRoles.JOURNALIST) && !own.markedTarget.isEmpty()
                && own.markedTarget.equals(uuid)) {
            return btt$set(cir, JOURNALIST_GOLD);
        }
        // 民俗学家：被动透视刚才使用「任何人」类技能的人（C-131）
        if (own.folkTicks > 0 && !own.folkTarget.isEmpty() && own.folkTarget.equals(uuid)) {
            return btt$set(cir, BttRoles.FOLKLORIST.color());
        }
        // 工程师 <扫描> / 窃贼 <搜刮> 后：短时全车透视
        if (own.engineerScanTicks > 0) {
            return btt$set(cir, ENGINEER_CYAN);
        }
        if (own.thiefRevealTicks > 0) {
            return btt$set(cir, BttRoles.THIEF.color());
        }
        return false;
    }

    /** 统一"接管本次高亮"：写色 + cancel，返回 true */
    private static boolean btt$set(CallbackInfoReturnable<Integer> cir, int color) {
        cir.setReturnValue(color);
        cir.cancel();
        return true;
    }

    /** C-091：花匠被动透视全部小花（金描边）；非花实体一律 skip，返回 true = 已接管本次判定 */
    /**
     * 入殓师（C-131，docx：可以透视尸体、掉落的道具和贴的便条）——**被动、无需按键**：
     * 对尸体（{@code PlayerBodyEntity}）、掉落物（{@code ItemEntity}）、贴出的便条（{@code NoteEntity}）
     * 返回入殓师职业色；其余非玩家实体 skip（-1），避免顺带泄漏别的配色。
     */
    private static boolean btt$morticianSight(Entity target, CallbackInfoReturnable<Integer> cir) {
        var viewer = MinecraftClient.getInstance().player;
        if (viewer == null) return false;
        if (!BttIdentity.isBttMode(viewer.getWorld())) return false;
        GameWorldComponent gwc = GameWorldComponent.KEY.get(viewer.getWorld());
        if (gwc.getRole(viewer) != BttRoles.MORTICIAN) return false;
        if (!GameFunctions.isPlayerAliveAndSurvival(viewer)) return false;
        boolean sight = target instanceof dev.doctor4t.wathe.entity.PlayerBodyEntity
                || target instanceof ItemEntity
                || target instanceof dev.doctor4t.wathe.entity.NoteEntity;
        cir.setReturnValue(sight ? BttRoles.MORTICIAN.color() : -1);
        cir.cancel();
        return true;
    }

    private static boolean btt$gardenerFlowers(Entity target, CallbackInfoReturnable<Integer> cir) {
        var viewer = MinecraftClient.getInstance().player;
        if (viewer == null) return false;
        if (!BttIdentity.isBttMode(viewer.getWorld())) return false;
        GameWorldComponent gwc = GameWorldComponent.KEY.get(viewer.getWorld());
        if (gwc.getRole(viewer) != BttRoles.GARDENER) return false;
        if (!GameFunctions.isPlayerAliveAndSurvival(viewer)) return false;
        cir.setReturnValue(target instanceof ItemEntity item
                && org.agmas.noellesroles.btt.BttFlowers.isFlower(item.getStack()) ? INSTINCT_ITEM_GOLD : -1);
        cir.cancel();
        return true;
    }
}
