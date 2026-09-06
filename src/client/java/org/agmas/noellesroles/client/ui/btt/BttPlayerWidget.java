package org.agmas.noellesroles.client.ui.btt;

import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import org.agmas.noellesroles.AbilityPlayerComponent;
import org.agmas.noellesroles.packet.GuessC2SPacket;

import java.awt.*;
import java.util.UUID;

/**
 * BT-P2-UI 玩家选择件（NR GuesserPlayerWidget 同构克隆，改发 BttGuessC2SPacket）。
 * instantMode=true（魔术师）：点击即发空猜测并关屏（换位无需选角色文本）。
 */
public class BttPlayerWidget extends ButtonWidget {
    public final LimitedInventoryScreen screen;
    public final UUID targetUUID;
    public final PlayerListEntry targetPlayerEntry;
    public static UUID selectedPlayer;
    /** 由 BttGuessScreenMixin 按身份设置（魔术师=true） */
    public static boolean instantMode = false;

    public BttPlayerWidget(LimitedInventoryScreen screen, int x, int y, UUID targetUUID, PlayerListEntry targetPlayerEntry) {
        super(x, y, 16, 16, Text.literal(""), (a) -> {
            AbilityPlayerComponent playerComponent = AbilityPlayerComponent.KEY.get(screen.player);
            if (playerComponent.cooldown > 0) return;
            if (instantMode) {
                ClientPlay.send(targetUUID);
                screen.close();
                return;
            }
            selectedPlayer = targetUUID;
        }, DEFAULT_NARRATION_SUPPLIER);
        this.screen = screen;
        this.targetPlayerEntry = targetPlayerEntry;
        this.targetUUID = targetUUID;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderWidget(context, mouseX, mouseY, delta);
        int cd = (AbilityPlayerComponent.KEY.get(MinecraftClient.getInstance().player)).cooldown;
        if (cd == 0) {
            context.drawGuiTexture(ShopEntry.Type.TOOL.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
            PlayerSkinDrawer.draw(context, targetPlayerEntry.getSkinTextures().texture(), this.getX(), this.getY(), 16);
            if (this.isHovered()) {
                this.drawHighlight(context, this.getX(), this.getY());
                String name = targetPlayerEntry.getProfile().getName();
                context.drawTooltip(MinecraftClient.getInstance().textRenderer, Text.of(name),
                        this.getX() - 4 - MinecraftClient.getInstance().textRenderer.getWidth(Text.literal(name)) / 2, this.getY() - 9);
            }
        } else {
            context.setShaderColor(0.25f, 0.25f, 0.25f, 0.5f);
            context.drawGuiTexture(ShopEntry.Type.TOOL.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
            PlayerSkinDrawer.draw(context, targetPlayerEntry.getSkinTextures().texture(), this.getX(), this.getY(), 16);
            if (this.isHovered()) {
                this.drawHighlight(context, this.getX(), this.getY());
                String name = targetPlayerEntry.getProfile().getName();
                context.drawTooltip(MinecraftClient.getInstance().textRenderer, Text.of(name),
                        this.getX() - 4 - MinecraftClient.getInstance().textRenderer.getWidth(Text.literal(name)) / 2, this.getY() - 9);
            }
            context.setShaderColor(1f, 1f, 1f, 1f);
            context.drawText(MinecraftClient.getInstance().textRenderer, cd / 20 + "", this.getX(), this.getY(), Color.RED.getRGB(), true);
        }
    }

    private void drawHighlight(DrawContext context, int x, int y) {
        int color = -1862287543;
        context.fillGradient(RenderLayer.getGuiOverlay(), x, y, x + 16, y + 14, color, color, 0);
        context.fillGradient(RenderLayer.getGuiOverlay(), x, y + 14, x + 15, y + 15, color, color, 0);
        context.fillGradient(RenderLayer.getGuiOverlay(), x, y + 15, x + 14, y + 16, color, color, 0);
    }

    /** 包内小桥：避免直接 import NR 的 GuessC2SPacket（语义不同） */
    private static final class ClientPlay {
        static void send(UUID target) {
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new org.agmas.noellesroles.btt.BttGuessC2SPacket(target, ""));
        }
    }
}
