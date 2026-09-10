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
 * - **身边者技能** → G 键直接选中最近玩家（≤6 格），不发屏：绳艺师/药剂师/酒保/走私犯；
 * - **任意人技能** → 打开背包选人屏（BttGuessScreenMixin 铺选人件）：预言家/小说家/猎人/侦探/救世主/舞蛇人；
 * - **无目标技能** → G 键直发：吟游诗人/花匠/特工/工程师/建筑师；失忆患者 → G 键注视尸体。
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
        // 炸弹持有者：G 键对准他人传递炸弹（优先于身份技能）
        var pc = org.agmas.noellesroles.btt.BttPlayerComponent.KEY.get(client.player);
        if (pc.bombPlaced) {
            if (pc.bombBeeping) {
                var hit = net.minecraft.entity.projectile.ProjectileUtil.getCollision(client.player,
                        e -> e instanceof net.minecraft.entity.player.PlayerEntity && e != client.player, (float) NEARBY_RANGE);
                if (hit instanceof net.minecraft.util.hit.EntityHitResult ehr
                        && ehr.getEntity() instanceof net.minecraft.entity.player.PlayerEntity target) {
                    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                            new org.agmas.noellesroles.btt.BttGuessC2SPacket(target.getUuid(), ""));
                }
            }
            return;
        }
        BttRoleDef def = BttRoleDefs.get(gwc.getRole(client.player));
        if (def == null) return;
        // 失忆患者：G 键 + 注视尸体（自带射线 4 格——NR targetBody 在暗处/2 格距离下不可用）
        if (def.role == BttRoles.AMNESIAC) {
            var hit = net.minecraft.entity.projectile.ProjectileUtil.getCollision(client.player,
                    e -> e instanceof dev.doctor4t.wathe.entity.PlayerBodyEntity, 4.0);
            if (!(hit instanceof net.minecraft.util.hit.EntityHitResult ehr)) return;
            var body = (dev.doctor4t.wathe.entity.PlayerBodyEntity) ehr.getEntity();
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new org.agmas.noellesroles.btt.BttCorpseActionC2SPacket(body.getUuid(), 1));
            return;
        }
        // 吟游诗人/花匠/工程师/建筑师：<歌唱>/<栽培>/<扫描>/<修复> 无需目标——G 键直发（target=自己占位；门由服务端射线判定）
        if (def.role == BttRoles.MINSTREL || def.role == BttRoles.GARDENER || def.role == BttRoles.ENGINEER
                || def.role == BttRoles.ARCHITECT) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new org.agmas.noellesroles.btt.BttGuessC2SPacket(client.player.getUuid(), ""));
            return;
        }
        // 特工：<查看> 本局身份列表——G 键直发（client 渲染由接收器 Action Bar 回执）
        if (def.role == BttRoles.AGENT) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new org.agmas.noellesroles.btt.BttGuessC2SPacket(client.player.getUuid(), ""));
            return;
        }
        // 身边者技能：G 键对准准星所指玩家施放（不发选人屏）
        if (isNearbyRole(def.role)) {
            sendAimed(client);
            return;
        }
        // 任意人技能：打开背包选人屏
        if (!isAnyRole(def.role)) return;
        if (client.currentScreen != null) return;
        client.setScreen(new LimitedInventoryScreen(client.player));
    }

    /** 身边者技能（G 键对准准星所指玩家）：绳艺师/药剂师/酒保/派对主/恐怖分子 */
    public static boolean isNearbyRole(dev.doctor4t.wathe.api.Role role) {
        return role == BttRoles.RIGGER || role == BttRoles.PHARMACIST
                || role == BttRoles.BARTENDER || role == BttRoles.PARTYHOST
                || role == BttRoles.TERRORIST;
    }

    /** 任意人技能（背包菜单选人）：预言家/小说家/猎人/侦探/救世主/舞蛇人/刺客/走私犯/冒牌货/记者 */
    public static boolean isAnyRole(dev.doctor4t.wathe.api.Role role) {
        return role == BttRoles.PROPHET || role == BttRoles.NOVELIST || role == BttRoles.HUNTER
                || role == BttRoles.DETECTIVE || role == BttRoles.MESSIAH || role == BttRoles.SNAKE_CHARMER
                || role == BttRoles.ASSASSIN || role == BttRoles.SMUGGLER || role == BttRoles.IMPOSTOR
                || role == BttRoles.JOURNALIST;
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
        BttRoleDef def = BttRoleDefs.get(gwc.getRole(p));
        return def != null && isUiRole(def.role);
    }

    /** 兼容旧名：选人屏仅服务任意人技能（刺客/魔术师走 NR 原生 UI，不在列） */
    public static boolean isUiRole(dev.doctor4t.wathe.api.Role role) {
        return isAnyRole(role);
    }

    /** 任意人技能中点击即发动（无需输入角色文本）：猎人/侦探 */
    public static boolean isInstant(dev.doctor4t.wathe.api.Role role) {
        return role == BttRoles.HUNTER || role == BttRoles.DETECTIVE;
    }
}
