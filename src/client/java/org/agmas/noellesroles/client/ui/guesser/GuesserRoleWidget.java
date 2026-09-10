package org.agmas.noellesroles.client.ui.guesser;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.client.ui.select.SelectPlayerWidget;
import org.agmas.noellesroles.client.ui.select.SelectRoleWidget;
import org.agmas.noellesroles.packet.GuessC2SPacket;

/** 刺客角色输入件（R3 起继承 {@link SelectRoleWidget}）：凶手侧过滤候选，回车发 NR Guess 包。 */
public class GuesserRoleWidget extends SelectRoleWidget {
    public GuesserRoleWidget(LimitedInventoryScreen screen, TextRenderer textRenderer, int x, int y) {
        super(screen, textRenderer, x, y);
    }

    @Override
    protected void submit() {
        ClientPlayNetworking.send(new GuessC2SPacket(SelectPlayerWidget.selectedPlayer, getText()));
    }

    @Override
    protected boolean filter(Role m) {
        GameWorldComponent gwc = GameWorldComponent.KEY.get(MinecraftClient.getInstance().player.getWorld());
        if (!gwc.isInnocent(MinecraftClient.getInstance().player)) {
            if (Noellesroles.KILLER_SIDED_NEUTRALS.contains(m)) return false;
            if (m.canUseKiller()) return false;
        }
        return true;
    }
}
