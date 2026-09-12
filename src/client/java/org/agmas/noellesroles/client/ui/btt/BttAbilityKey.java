package org.agmas.noellesroles.client.ui.btt;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.agmas.noellesroles.btt.BttRoleDef;
import org.agmas.noellesroles.btt.BttRoleDefs;
import org.agmas.noellesroles.btt.BttRoles;

/**
 * BTT 技能键（默认 G，走 NR abilityBind 分流；由 NoellesrolesClient 调 handlePress）。
 * 触发模型（GAME_DESIGN §4.5）：
 * - **身边者技能** → G 键直接选中准星所指玩家（≤6 格），不发屏：绳艺师/药剂师/酒保/笑匠/魅魔/教授/饕餮/小恶魔；
 * - **任意人技能** → 打开背包选人屏（BttGuessScreenMixin 铺选人件）：预言家/小说家/猎人/侦探/救世主/舞蛇人；
 * - **无目标技能** → G 键直发：花匠/工程师/建筑师/纵火犯/演员/梦游病；失忆患者/食人族 → G 键注视尸体。
 * （静态判断方法放本普通类：mixin 类禁止非 private static 成员，曾致 LimitedInventoryScreen 转换崩溃。）
 */
public final class BttAbilityKey {
    private BttAbilityKey() {}

    /** 身边者技能作用半径（与 BttGuessReceiver 服务端校验一致） */
    private static final double NEARBY_RANGE = 6.0;

    /** 由 NoellesrolesClient 的 abilityBind.wasPressed() 调用（BTT 模式优先分流） */
    public static void handlePress(MinecraftClient client) {
        openSelection(client);
    }

    private static void openSelection(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (!org.agmas.noellesroles.btt.BttIdentity.isBttMode(client.world)) return;
        var gwc = dev.doctor4t.wathe.cca.GameWorldComponent.KEY.get(client.world);
        if (!gwc.isRunning()) return;
        // C-113：炸弹相关不再走 G 键（传递只保留物品右键）
        BttRoleDef def = BttRoleDefs.get(BttRoles.effectiveRole(gwc, client.player)); // C-110：借来的技能身份优先
        if (def == null) return;
        // 失忆患者：G 键 + 注视尸体（自带射线 4 格——NR targetBody 在暗处/2 格距离下不可用）
        if (def.role == BttRoles.AMNESIAC || def.role == BttRoles.CANNIBAL) {
            var hit = net.minecraft.entity.projectile.ProjectileUtil.getCollision(client.player,
                    e -> e instanceof dev.doctor4t.wathe.entity.PlayerBodyEntity, 4.0);
            if (!(hit instanceof net.minecraft.util.hit.EntityHitResult ehr)) return;
            var body = (dev.doctor4t.wathe.entity.PlayerBodyEntity) ehr.getEntity();
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new org.agmas.noellesroles.btt.BttCorpseActionC2SPacket(body.getUuid(), 1));
            return;
        }
        // 花匠/工程师/建筑师/锁匠/纵火犯：<栽培>/<扫描>/<修复>/<上锁>/<浇汽油> 无需目标——
        // G 键直发（target=自己占位；门由服务端射线/最近者判定）
        if (def.role == BttRoles.GARDENER || def.role == BttRoles.ENGINEER || def.role == BttRoles.ATTENDANT
                || def.role == BttRoles.ARCHITECT || def.role == BttRoles.LOCKSMITH
                || def.role == BttRoles.ARSONIST || def.role == BttRoles.ACTOR) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new org.agmas.noellesroles.btt.BttGuessC2SPacket(client.player.getUuid(), ""));
            return;
        }
        // 偷渡客：<隐蔽> 走 NR 原生 ability 包（服务端 PHANTOM 分支；BTT 冷却 2 分钟、隐身 30 秒，C-107）
        if (def.role == BttRoles.STOWAWAY) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new org.agmas.noellesroles.packet.AbilityC2SPacket());
            return;
        }
        // 梦游病：<入梦> 灵魂出窍 ⇄ 回归——G 键直发（C-087）
        if (def.role == BttRoles.MEYUUBYOU) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new org.agmas.noellesroles.btt.BttGuessC2SPacket(client.player.getUuid(), ""));
            return;
        }
        // 身边者技能：G 键对准准星所指玩家施放（不发选人屏）
        if (isNearbyRole(def.role)) {
            sendAimed(client);
            return;
        }
        // C-113：任意人技能是 **E 键技能**——G 键不再打开选人屏（用户裁定：E 键技能不需要按 G 打开）
    }

    /** 身边者技能（G 键对准准星所指玩家）：绳艺师/药剂师/酒保/笑匠/魅魔/保镖/寄生者/教授/饕餮/小恶魔（恐怖分子 C-084 起走物品右键） */
    public static boolean isNearbyRole(dev.doctor4t.wathe.api.Role role) {
        return role == BttRoles.PHARMACIST
                || role == BttRoles.BARTENDER || role == BttRoles.SUCCUBUS || role == BttRoles.COMEDIAN
                || role == BttRoles.BODYGUARD || role == BttRoles.LEECH || role == BttRoles.CULT_LEADER
                || role == BttRoles.PROFESSOR || role == BttRoles.KIDNAPPER || role == BttRoles.IMP;
    }

    /** HUD 技能提示身份：身边者技能 + **无目标直发技能**（这些都在 G 键分支里，但此前漏进本表 → 提示恒不显示，C-090） */
    public static boolean hasTip(dev.doctor4t.wathe.api.Role role) {
        // C-113：恐怖分子的炸弹已改纯物品右键（无 G 键技能）→ 不再出提示；E 键技能亦不出提示（见下）
        return isNearbyRole(role) || role == BttRoles.MEYUUBYOU
                || role == BttRoles.GARDENER || role == BttRoles.AMNESIAC
                || role == BttRoles.ARCHITECT || role == BttRoles.LOCKSMITH || role == BttRoles.ENGINEER
                || role == BttRoles.ATTENDANT
                || role == BttRoles.ARSONIST || role == BttRoles.ACTOR || role == BttRoles.CANNIBAL;
        // C-113：E 键技能（任意人：律师/小恶魔等）不出右下角提示、G 键也不开选人屏（见 isAnyRole）
    }

    /** 任意人技能（背包菜单选人）：预言家/小说家/猎人/侦探/救世主/舞蛇人/刺客/记者 + 律师/绳艺师/小恶魔（C-103/C-111/C-125） */
    public static boolean isAnyRole(dev.doctor4t.wathe.api.Role role) {
        return role == BttRoles.PROPHET || role == BttRoles.NOVELIST || role == BttRoles.HUNTER
                || role == BttRoles.DETECTIVE || role == BttRoles.MESSIAH || role == BttRoles.SNAKE_CHARMER
                || role == BttRoles.ASSASSIN || role == BttRoles.JOURNALIST || role == BttRoles.RIGGER
                || role == BttRoles.LAWYER || role == BttRoles.ACTOR;
    }

    /** 点头像即发 **NR 原生**包（演员 <易容>；`MorphC2SPacket`，NR 原生无冷却、持续 35 秒，C-106） */
    public static boolean isMorphRole(dev.doctor4t.wathe.api.Role role) {
        return role == BttRoles.ACTOR;
    }

    /** 多指名技能（律师 <起诉>：点 N 次凑齐后单包提交；绳艺师 <拘束>：固定 2 人 —— C-125） */
    public static boolean isMultiPick(dev.doctor4t.wathe.api.Role role) {
        return role == BttRoles.LAWYER || role == BttRoles.RIGGER;
    }

    /** G 键身边者技能：取**准星所指玩家**（≤6 格）直接发包；未对准他人则不施放 */
    private static void sendAimed(MinecraftClient client) {
        var player = client.player;
        if (player == null) return;
        var hit = net.minecraft.entity.projectile.ProjectileUtil.getCollision(player,
                e -> e instanceof net.minecraft.entity.player.PlayerEntity && e != player, (float) NEARBY_RANGE);
        if (hit instanceof net.minecraft.util.hit.EntityHitResult ehr
                && ehr.getEntity() instanceof net.minecraft.entity.player.PlayerEntity target) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new org.agmas.noellesroles.btt.BttGuessC2SPacket(target.getUuid(), ""));
        }
    }

    /** 当前客户端身份是否应显示选人 UI */
    public static boolean hasSelectionUi() {
        var p = MinecraftClient.getInstance().player;
        if (p == null || MinecraftClient.getInstance().world == null) return false;
        if (!org.agmas.noellesroles.btt.BttIdentity.isBttMode(MinecraftClient.getInstance().world)) return false;
        var gwc = GameWorldComponent.KEY.get(MinecraftClient.getInstance().world);
        if (!gwc.isRunning()) return false;
        BttRoleDef def = BttRoleDefs.get(BttRoles.effectiveRole(gwc, p)); // C-110：借来的技能身份优先
        return def != null && isUiRole(def.role);
    }

    /** 兼容旧名：选人屏仅服务任意人技能（刺客/魔术师走 NR 原生 UI，不在列） */
    public static boolean isUiRole(dev.doctor4t.wathe.api.Role role) {
        return isAnyRole(role);
    }

    /** 任意人技能中点击即发动（无需输入角色文本）：猎人/侦探 */
    public static boolean isInstant(dev.doctor4t.wathe.api.Role role) {
        // 小恶魔：点头像即印记
        return role == BttRoles.HUNTER || role == BttRoles.DETECTIVE;
    }
}
