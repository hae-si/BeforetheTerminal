package org.agmas.noellesroles.client.ui.btt;

import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.PlayerListEntry;
import org.agmas.noellesroles.AbilityPlayerComponent;
import org.agmas.noellesroles.client.ui.select.SelectPlayerWidget;

import java.util.UUID;

/**
 * BTT 选人件（R3 起继承 {@link SelectPlayerWidget}，与 NR Guesser 共用渲染/状态）。
 * instant 模式（点头像即发包）由 {@link SelectPlayerWidget#instantMode} 控制。
 */
public class BttPlayerWidget extends SelectPlayerWidget {
    public BttPlayerWidget(LimitedInventoryScreen screen, int x, int y, UUID targetUUID, PlayerListEntry targetPlayerEntry) {
        super(screen, x, y, targetUUID, targetPlayerEntry, a -> {
            if (AbilityPlayerComponent.KEY.get(screen.player).cooldown > 0) return;
            if (instantMode) {
                ClientPlayNetworking.send(new org.agmas.noellesroles.btt.BttGuessC2SPacket(targetUUID, ""));
                screen.close();
                return;
            }
            SelectPlayerWidget.selectedPlayer = targetUUID;
        });
    }
}
