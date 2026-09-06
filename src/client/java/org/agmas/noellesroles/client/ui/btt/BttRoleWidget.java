package org.agmas.noellesroles.client.ui.btt;

import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.agmas.harpymodloader.Harpymodloader;

import java.awt.*;
import java.util.ArrayList;

/**
 * BT-P2-UI 角色文本件（NR GuesserRoleWidget 同构克隆）：输入角色 id 自动补全，回车发送 BttGuessC2SPacket。
 * 候选 = 全部注册 Role（剔除 HML SPECIAL_ROLES）；与 NR 的差别：不做凶手侧过滤（BTT 预言家/刺客可猜任何身份）。
 */
public class BttRoleWidget extends TextFieldWidget {
    public final LimitedInventoryScreen screen;
    public final ChatInputSuggestor suggestor;
    public static boolean stopClosing = false;

    public BttRoleWidget(LimitedInventoryScreen screen, TextRenderer textRenderer, int x, int y) {
        super(textRenderer, x, y, 200, 16, Text.literal(""));
        this.screen = screen;
        suggestor = new ChatInputSuggestor(MinecraftClient.getInstance(), screen, this, textRenderer, true, true, -1, 10, false, Color.TRANSLUCENT);
        suggestor.refresh();
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        boolean original = super.charTyped(chr, modifiers);
        suggestor.refresh();
        return original;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            if (BttPlayerWidget.selectedPlayer != null) {
                ClientPlayNetworking.send(new org.agmas.noellesroles.btt.BttGuessC2SPacket(BttPlayerWidget.selectedPlayer, getText()));
                screen.close();
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void eraseCharacters(int characterOffset) {
        super.eraseCharacters(characterOffset);
        suggestor.refresh();
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        stopClosing = isSelected();
        if (BttPlayerWidget.selectedPlayer != null) {
            ArrayList<String> suggestions = new ArrayList<>();
            WatheRoles.ROLES.forEach((m) -> {
                if (Harpymodloader.SPECIAL_ROLES.contains(m)) return;
                if (m.identifier().getPath().startsWith(getText()) || getText().isEmpty()) {
                    suggestions.add(m.identifier().getPath());
                }
            });
            if (!suggestions.isEmpty()) {
                setSuggestion(suggestions.getFirst().substring(getText().length()));
            } else {
                setSuggestion("");
            }
        }
        super.renderWidget(context, mouseX, mouseY, delta);
    }
}
