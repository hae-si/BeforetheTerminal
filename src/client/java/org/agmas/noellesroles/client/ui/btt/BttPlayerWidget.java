package org.agmas.noellesroles.client.ui.btt;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.network.PlayerListEntry;
import org.agmas.noellesroles.AbilityPlayerComponent;
import org.agmas.noellesroles.client.ui.select.SelectPlayerWidget;

import java.util.UUID;

/**
 * BTT 选人件（R3 起继承 {@link SelectPlayerWidget}，与 NR Guesser 共用渲染/状态）。
 * instant 模式（点头像即发包）由 {@link SelectPlayerWidget#instantMode} 控制。
 * 律师（C-103）走**多指名**：每次点击追加一人，凑齐「存活凶手席位数」后单包提交。
 */
public class BttPlayerWidget extends SelectPlayerWidget {
    public BttPlayerWidget(LimitedInventoryScreen screen, int x, int y, UUID targetUUID, PlayerListEntry targetPlayerEntry) {
        super(screen, x, y, targetUUID, targetPlayerEntry, a -> {
            if (AbilityPlayerComponent.KEY.get(screen.player).cooldown > 0) return;
            // C-128：通用 E 键选择音（客户端本机播放，只有自己听得到）
            screen.player.playSound(org.agmas.noellesroles.btt.BttSounds.UI_KEY_E, 1.0F, 1.0F);
            if (BttAbilityKey.isMultiPick(GameWorldComponent.KEY.get(screen.player.getWorld()).getRole(screen.player))) {
                // 再点一次 = 取消该指名（点了才会满，误点可撤回；swapper 的两段式没有这个余地）
                if (BttLawyerPick.isPicked(targetUUID)) {
                    BttLawyerPick.picks().remove(targetUUID);
                } else {
                    BttLawyerPick.picks().add(targetUUID);
                }
                int need = BttLawyerPick.needed(screen.player);
                if (need > 0 && BttLawyerPick.picks().size() >= need) {
                    ClientPlayNetworking.send(new org.agmas.noellesroles.btt.BttLawyerC2SPacket(
                            java.util.List.copyOf(BttLawyerPick.picks())));
                    BttLawyerPick.reset();
                    screen.close();
                }
                return;
            }
            if (BttAbilityKey.isMorphRole(GameWorldComponent.KEY.get(screen.player.getWorld()).getRole(screen.player))) {
                // 演员 <易容>：直接发 NR 原生 morph 包（无冷却）
                ClientPlayNetworking.send(new org.agmas.noellesroles.packet.MorphC2SPacket(targetUUID));
                screen.close();
                return;
            }
            if (instantMode) {
                ClientPlayNetworking.send(new org.agmas.noellesroles.btt.BttGuessC2SPacket(targetUUID, ""));
                screen.close();
                return;
            }
            SelectPlayerWidget.selectedPlayer = targetUUID;
        });
    }

    @Override
    protected void renderWidget(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderWidget(context, mouseX, mouseY, delta);
        // 律师：已指名的头像加高亮，便于对照读秒/进度
        if (BttLawyerPick.isPicked(this.targetUUID)) {
            drawHighlight(context, this.getX(), this.getY());
        }
    }
}
