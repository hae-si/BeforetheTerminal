package org.agmas.noellesroles.client.ui.btt;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.btt.BttRoleDef;
import org.agmas.noellesroles.btt.BttRoleDefs;
import org.agmas.noellesroles.btt.BttRoles;
import org.lwjgl.glfw.GLFW;

/**
 * BT-P2-UI G 键技能（用户指令：技能一律 G 键 + 背包选人，不做道具）。
 * BTT 自建键位（默认 G，与 NR abilityBind 同键但独立实例——wasPressed 消费互斥，不能共用）。
 * 按下 → BTT 局内且身份属选人类 → 打开背包屏（BttGuessScreenMixin 自动铺选人件）。
 * （静态判断方法放本普通类：mixin 类禁止非 private static 成员，曾致 LimitedInventoryScreen 转换崩溃。）
 */
public final class BttAbilityKey {
    private BttAbilityKey() {}

    private static KeyBinding abilityKey;

    public static void register() {
        abilityKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.btt.ability", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.btt.keybinds"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (abilityKey.wasPressed()) {
                openSelection(client);
            }
        });
    }

    private static void openSelection(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        if (!org.agmas.noellesroles.btt.BttIdentity.isBttMode(client.world)) return;
        var gwc = dev.doctor4t.wathe.cca.GameWorldComponent.KEY.get(client.world);
        if (!gwc.isRunning()) return;
        BttRoleDef def = BttRoleDefs.get(gwc.getRole(client.player));
        if (def == null) return;
        // 尸体交互类（复用 NR 秃鹫模式：G 键 + 注视尸体 targetBody）
        if (def.role == BttRoles.THIEF || def.role == BttRoles.AMNESIAC) {
            var body = NoellesrolesClient.targetBody;
            if (body == null) return;
            int action = def.role == BttRoles.THIEF ? 0 : 1;
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new org.agmas.noellesroles.btt.BttCorpseActionC2SPacket(body.getUuid(), action));
            return;
        }
        if (!isUiRole(def.role)) return;
        if (client.currentScreen != null) return;
        client.setScreen(new LimitedInventoryScreen(client.player));
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

    public static boolean isUiRole(dev.doctor4t.wathe.api.Role role) {
        return role == BttRoles.PROPHET || role == BttRoles.ASSASSIN || role == BttRoles.NOVELIST
                || role == BttRoles.MAGICIAN || role == BttRoles.SNAKE_CHARMER
                || role == BttRoles.HUNTER || role == BttRoles.DETECTIVE
                || role == BttRoles.RIGGER || role == BttRoles.CANDY_SELLER;
    }

    public static boolean isInstant(dev.doctor4t.wathe.api.Role role) {
        return role == BttRoles.MAGICIAN || role == BttRoles.HUNTER
                || role == BttRoles.DETECTIVE || role == BttRoles.RIGGER || role == BttRoles.CANDY_SELLER;
    }
}
