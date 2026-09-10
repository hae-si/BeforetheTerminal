package org.agmas.noellesroles.client.ui.guesser;

import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.network.PlayerListEntry;
import org.agmas.noellesroles.AbilityPlayerComponent;
import org.agmas.noellesroles.client.ui.select.SelectPlayerWidget;

import java.util.UUID;

/** 刺客选人件：点头像选中目标（R3 起继承 {@link SelectPlayerWidget}，仅提供点击行为）。 */
public class GuesserPlayerWidget extends SelectPlayerWidget {
    public GuesserPlayerWidget(LimitedInventoryScreen screen, int x, int y, UUID targetUUID, PlayerListEntry targetPlayerEntry) {
        super(screen, x, y, targetUUID, targetPlayerEntry, a -> {
            if (AbilityPlayerComponent.KEY.get(screen.player).cooldown > 0) return;
            SelectPlayerWidget.selectedPlayer = targetUUID;
        });
    }
}
