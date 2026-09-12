package org.agmas.noellesroles.client.mixin.swapper;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedHandledScreen;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.client.ui.SwapperPlayerWidget;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;
import java.util.List;


@Mixin(LimitedInventoryScreen.class)
public abstract class SwapperScreenMixin extends LimitedHandledScreen<PlayerScreenHandler>{
    @Shadow @Final public ClientPlayerEntity player;

    public SwapperScreenMixin(PlayerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }


    @Inject(method = "render", at = @At("HEAD"))
    void renderSwapperText(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // C-126：BTT 局内魔术师改用 BTT 多指名选人屏（E 键，与律师/绳艺师同款）→ 关掉 NR 原生两段式提示
        if (org.agmas.noellesroles.btt.BttIdentity.isBttMode(player.getWorld())) return;
        GameWorldComponent gameWorldComponent = (GameWorldComponent) GameWorldComponent.KEY.get(player.getWorld());
        if (gameWorldComponent.isRole(player,Noellesroles.SWAPPER)) {
            int y = (height- 32) / 2;
            int x = width / 2;
            if (SwapperPlayerWidget.playerChoiceOne == null) {
                Text name = Text.translatable("hud.swapper.first_player_selection");
                context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, name, x - (MinecraftClient.getInstance().textRenderer.getWidth(name)/2), y + 40, Color.CYAN.getRGB());
            } else {
                Text name = Text.translatable("hud.swapper.second_player_selection");
                context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, name, x - (MinecraftClient.getInstance().textRenderer.getWidth(name)/2), y + 40, Color.RED.getRGB());
            }
        }
    }
    @Inject(method = "init", at = @At("HEAD"))
    void renderSwapperHeads(CallbackInfo ci) {
        SwapperPlayerWidget.playerChoiceOne = null;
        // C-126：BTT 局内不铺 NR 原生魔术师头像（改由 BttGuessScreenMixin 的多指名件接管）
        if (org.agmas.noellesroles.btt.BttIdentity.isBttMode(player.getWorld())) return;
        GameWorldComponent gameWorldComponent = (GameWorldComponent) GameWorldComponent.KEY.get(player.getWorld());
        if (gameWorldComponent.isRole(player,Noellesroles.SWAPPER)) {
            List<AbstractClientPlayerEntity> entries = MinecraftClient.getInstance().world.getPlayers();
            if (!entries.contains(player)) entries.add(player);
            int apart = 36;
            int x = width / 2 - (entries.size()) * apart / 2 + 9;
            int shouldBeY = (height - 32) / 2;
            int y = shouldBeY + 80;

            for(int i = 0; i < entries.size(); ++i) {
                SwapperPlayerWidget child = new SwapperPlayerWidget(((LimitedInventoryScreen)(Object)this), x + apart * i, y, entries.get(i), i);
                addDrawableChild(child);
            }
        }
    }

}
