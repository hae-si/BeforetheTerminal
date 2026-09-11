package org.agmas.noellesroles.client.ui.select;

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

import java.awt.Color;
import java.util.UUID;

/**
 * 选人网格件公共基类（R3 去重）：NR {@code GuesserPlayerWidget} 与 BTT {@code BttPlayerWidget} 共用。
 * 提供：字段、{@link #selectedPlayer}/{@link #instantMode} 共享状态、皮肤+槽位绘制、hover 提示、冷却压暗与读秒。
 * 子类仅通过构造器传入 {@link PressAction}（点击行为）。
 */
public abstract class SelectPlayerWidget extends ButtonWidget {
    public final LimitedInventoryScreen screen;
    public final UUID targetUUID;
    public final PlayerListEntry targetPlayerEntry;

    /** 当前选中目标（无=null）；同一屏只会实例化其中一套选人件，故共享 */
    public static UUID selectedPlayer;
    /** BTT instant 模式（点头像即发包）；NR 默认 false */
    public static boolean instantMode = false;

    protected SelectPlayerWidget(LimitedInventoryScreen screen, int x, int y, UUID targetUUID,
                                 PlayerListEntry targetPlayerEntry, PressAction action) {
        super(x, y, 16, 16, Text.literal(""), action, DEFAULT_NARRATION_SUPPLIER);
        this.screen = screen;
        this.targetUUID = targetUUID;
        this.targetPlayerEntry = targetPlayerEntry;
    }

    protected static int cooldown() {
        return AbilityPlayerComponent.KEY.get(MinecraftClient.getInstance().player).cooldown;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderWidget(context, mouseX, mouseY, delta);
        int cd = cooldown();
        if (cd == 0) {
            drawSlot(context);
            if (this.isHovered()) {
                drawHighlight(context, this.getX(), this.getY());
                drawNameTooltip(context);
            }
        } else {
            context.setShaderColor(0.25f, 0.25f, 0.25f, 0.5f);
            drawSlot(context);
            if (this.isHovered()) {
                drawHighlight(context, this.getX(), this.getY());
                drawNameTooltip(context);
            }
            context.setShaderColor(1f, 1f, 1f, 1f);
            context.drawText(MinecraftClient.getInstance().textRenderer, cd / 20 + "", this.getX(), this.getY(), Color.RED.getRGB(), true);
        }
    }

    private void drawSlot(DrawContext context) {
        context.drawGuiTexture(ShopEntry.Type.TOOL.getTexture(), this.getX() - 7, this.getY() - 7, 30, 30);
        PlayerSkinDrawer.draw(context, targetPlayerEntry.getSkinTextures().texture(), this.getX(), this.getY(), 16);
    }

    private void drawNameTooltip(DrawContext context) {
        String name = targetPlayerEntry.getProfile().getName();
        context.drawTooltip(MinecraftClient.getInstance().textRenderer, Text.of(name),
                this.getX() - 4 - MinecraftClient.getInstance().textRenderer.getWidth(Text.literal(name)) / 2, this.getY() - 9);
    }

    protected static void drawHighlight(DrawContext context, int x, int y) {
        int color = -1862287543;
        context.fillGradient(RenderLayer.getGuiOverlay(), x, y, x + 16, y + 14, color, color, 0);
        context.fillGradient(RenderLayer.getGuiOverlay(), x, y + 14, x + 15, y + 15, color, color, 0);
        context.fillGradient(RenderLayer.getGuiOverlay(), x, y + 15, x + 14, y + 16, color, color, 0);
    }
}
