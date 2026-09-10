package org.agmas.noellesroles.client.ui.select;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.WatheRoles;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.agmas.harpymodloader.Harpymodloader;

import java.awt.Color;
import java.util.ArrayList;

/**
 * 角色输入件公共基类（R3 去重）：NR {@code GuesserRoleWidget} 与 BTT {@code BttRoleWidget} 共用。
 * 提供：输入框 + {@link ChatInputSuggestor}、{@link #stopClosing} 共享状态、回车提交、建议迭代。
 * 子类实现 {@link #submit()}（发包）与 {@link #filter(Role)}（候选过滤）。
 */
public abstract class SelectRoleWidget extends TextFieldWidget {
    public final LimitedInventoryScreen screen;
    public final ChatInputSuggestor suggestor;

    /** 文本框聚焦时按 E 不关闭背包屏（{@code SelectScreenDoNotClose} 读取） */
    public static boolean stopClosing = false;

    protected SelectRoleWidget(LimitedInventoryScreen screen, TextRenderer textRenderer, int x, int y) {
        super(textRenderer, x, y, 200, 16, Text.literal(""));
        this.screen = screen;
        this.suggestor = new ChatInputSuggestor(MinecraftClient.getInstance(), screen, this, textRenderer,
                true, true, -1, 10, false, Color.TRANSLUCENT);
        this.suggestor.refresh();
    }

    /** 回车提交（子类发包） */
    protected abstract void submit();

    /** 建议候选过滤；返回 false 跳过该角色 */
    protected abstract boolean filter(Role role);

    /** 是否渲染下拉建议（NR true；BTT 原不渲染） */
    protected boolean showSuggestor() {
        return true;
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
            submit();
            screen.close();
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
        if (SelectPlayerWidget.selectedPlayer != null) {
            ArrayList<String> suggestions = new ArrayList<>();
            WatheRoles.ROLES.forEach(m -> {
                if (Harpymodloader.SPECIAL_ROLES.contains(m)) return;
                if (!filter(m)) return;
                if (m.identifier().getPath().startsWith(getText()) || getText().isEmpty())
                    suggestions.add(m.identifier().getPath());
            });
            if (!suggestions.isEmpty()) {
                setSuggestion(suggestions.getFirst().substring(getText().length()));
            } else {
                setSuggestion("");
            }
            super.renderWidget(context, mouseX, mouseY, delta);
            if (showSuggestor()) suggestor.render(context, mouseX, mouseY);
        }
    }
}
