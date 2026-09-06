package org.agmas.noellesroles.client.mixin.btt;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedHandledScreen;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.btt.BttRoleDef;
import org.agmas.noellesroles.btt.BttRoleDefs;
import org.agmas.noellesroles.btt.BttRoles;
import org.agmas.noellesroles.client.ui.btt.BttPlayerWidget;
import org.agmas.noellesroles.client.ui.btt.BttRoleWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BT-P2-UI 选人界面注入：BTT 局内、拥有选人类身份时在 wathe 背包屏铺选人件。
 * instant 组（点头像即发）：魔术师/猎人/侦探/绳艺师/卖糖人；
 * 猜测组（选人→输角色→回车）：预言家/刺客/小说家/舞蛇人。
 * G 键（BttAbilityKey）= 本界面的快捷入口。
 */
@Mixin(LimitedInventoryScreen.class)
public abstract class BttGuessScreenMixin extends LimitedHandledScreen<PlayerScreenHandler> {
    @Shadow @Final public ClientPlayerEntity player;

    public BttGuessScreenMixin(PlayerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    private static boolean isInstant(dev.doctor4t.wathe.api.Role role) {
        return role == BttRoles.MAGICIAN || role == BttRoles.HUNTER
                || role == BttRoles.DETECTIVE || role == BttRoles.RIGGER || role == BttRoles.CANDY_SELLER;
    }

    @Inject(method = "init", at = @At("HEAD"))
    void bttAddGuessWidgets(CallbackInfo ci) {
        if (player == null || MinecraftClient.getInstance().world == null) return;
        if (!BttIdentity.isBttMode(player.getWorld())) return;
        GameWorldComponent gwc = GameWorldComponent.KEY.get(player.getWorld());
        if (!gwc.isRunning()) return;
        BttRoleDef def = BttRoleDefs.get(gwc.getRole(player));
        if (def == null || !org.agmas.noellesroles.client.ui.btt.BttAbilityKey.isUiRole(def.role)) return;
        boolean instant = org.agmas.noellesroles.client.ui.btt.BttAbilityKey.isInstant(def.role);

        BttPlayerWidget.selectedPlayer = null;
        BttPlayerWidget.instantMode = instant;
        BttRoleWidget.stopClosing = false;

        List<UUID> entries = new ArrayList<>(player.networkHandler.getPlayerUuids());
        entries.remove(player.getUuid());
        int apart = 36;
        LimitedInventoryScreen self = (LimitedInventoryScreen) (Object) this;
        int x = self.width / 2 - entries.size() * apart / 2 + 9;
        int y = (self.height - 32) / 2 + 105;

        for (int i = 0; i < entries.size(); i++) {
            BttPlayerWidget child = new BttPlayerWidget(self, x + apart * i, y, entries.get(i),
                    player.networkHandler.getPlayerListEntry(entries.get(i)));
            addDrawableChild(child);
            child.visible = false;
        }
        if (!instant) {
            BttRoleWidget child = new BttRoleWidget(self, textRenderer, (width / 2) - 100, y);
            addDrawableChild(child);
            child.setVisible(false);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    void bttSwapWidgetVisibility(CallbackInfo ci) {
        boolean ours = false;
        for (Element child : children()) {
            if (child instanceof BttPlayerWidget) {
                ours = true;
                ((BttPlayerWidget) child).visible = BttPlayerWidget.selectedPlayer == null;
            }
            if (child instanceof BttRoleWidget grw) {
                BttRoleWidget.stopClosing = BttPlayerWidget.selectedPlayer != null;
                grw.visible = BttPlayerWidget.selectedPlayer != null;
            }
        }
        if (ours && BttPlayerWidget.instantMode) {
            for (Element child : children()) {
                if (child instanceof BttPlayerWidget gpw) gpw.visible = true;
            }
        }
    }
}
