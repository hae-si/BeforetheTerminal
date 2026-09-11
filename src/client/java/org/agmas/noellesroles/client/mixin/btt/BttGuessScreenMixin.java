package org.agmas.noellesroles.client.mixin.btt;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedHandledScreen;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
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
import org.agmas.noellesroles.client.ui.btt.BttAbilityKey;
import org.agmas.noellesroles.client.ui.btt.BttPlayerWidget;
import org.agmas.noellesroles.client.ui.btt.BttRoleWidget;
import org.agmas.noellesroles.client.ui.select.SelectPlayerWidget;
import org.agmas.noellesroles.client.ui.select.SelectRoleWidget;
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
 * 仅服务**任意人技能**（身边者技能走 BttAbilityKey G 键直接选中最近者，不在此）。
 * instant 组（点头像即发）：猎人/侦探；猜测组（选人→输角色→回车）：预言家/小说家/救世主/舞蛇人。
 * 刺客/魔术师走 NR 原生 UI，不在列。
 * G 键（BttAbilityKey）= 本界面的快捷入口。
 */
@Mixin(LimitedInventoryScreen.class)
public abstract class BttGuessScreenMixin extends LimitedHandledScreen<PlayerScreenHandler> {
    @Shadow @Final public ClientPlayerEntity player;

    public BttGuessScreenMixin(PlayerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
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
        boolean multiPick = org.agmas.noellesroles.client.ui.btt.BttAbilityKey.isMultiPick(def.role);
        boolean morph = org.agmas.noellesroles.client.ui.btt.BttAbilityKey.isMorphRole(def.role);
        if (morph) instant = true; // 演员 <易容>：点头像即发 morph 包（无冷却）

        SelectPlayerWidget.selectedPlayer = null;
        SelectPlayerWidget.instantMode = instant;
        SelectRoleWidget.stopClosing = false;
        if (multiPick) org.agmas.noellesroles.client.ui.btt.BttLawyerPick.reset();

        List<UUID> entries = new ArrayList<>(player.networkHandler.getPlayerUuids());
        entries.remove(player.getUuid());
        if (multiPick) {
            // 律师：名单 = 在场玩家（**含已死亡席位**——用户 2026-09-11 裁定"已死的凶手也要点出"）
            entries.removeIf(uuid -> MinecraftClient.getInstance().world.getPlayerByUuid(uuid) == null);
        }
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
        if (!instant && !multiPick) {
            BttRoleWidget child = new BttRoleWidget(self, textRenderer, (width / 2) - 100, y);
            addDrawableChild(child);
            child.setVisible(false);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    void bttSwapWidgetVisibility(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        boolean ours = false;
        for (Element child : children()) {
            if (child instanceof BttPlayerWidget) {
                ours = true;
                ((BttPlayerWidget) child).visible = SelectPlayerWidget.selectedPlayer == null;
            }
            if (child instanceof BttRoleWidget grw) {
                SelectRoleWidget.stopClosing = SelectPlayerWidget.selectedPlayer != null;
                grw.visible = SelectPlayerWidget.selectedPlayer != null;
            }
        }
        if (ours && SelectPlayerWidget.instantMode) {
            for (Element child : children()) {
                if (child instanceof BttPlayerWidget gpw) gpw.visible = true;
            }
        }
        // E 键技能说明（参照 NR voodoo 的 renderVoodooText）——**色 = 身份色**（C-098；此前恒白）
        BttRoleDef def = BttRoleDefs.get(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
        if (def != null && BttAbilityKey.isAnyRole(def.role)) {
            Text desc = Text.translatable("noellesroles.btt.hud." + def.role.identifier().getPath());
            context.drawTextWithShadow(textRenderer, desc,
                    width / 2 - textRenderer.getWidth(desc) / 2, (height - 32) / 2 + 40, def.role.color());
        }
        // 律师 <起诉>：指名进度（C-103；色 = 身份色，C-098）
        if (def != null && BttAbilityKey.isMultiPick(def.role)) {
            Text progress = Text.translatable("noellesroles.btt.lawyer.progress",
                    org.agmas.noellesroles.client.ui.btt.BttLawyerPick.picks().size(),
                    org.agmas.noellesroles.client.ui.btt.BttLawyerPick.needed(player));
            context.drawTextWithShadow(textRenderer, progress,
                    width / 2 - textRenderer.getWidth(progress) / 2, (height - 32) / 2 + 64, def.role.color());
        }
    }
}
