package org.agmas.noellesroles.mixin.framing;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.agmas.noellesroles.ModItems;
import org.agmas.noellesroles.Noellesroles;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerShopComponent.class)
public abstract class FramingRolesShopComponentMixin {
    @Shadow public int balance;

    @Shadow @Final private PlayerEntity player;

    @Shadow public abstract void sync();

    @Inject(method = "tryBuy", at = @At("HEAD"), cancellable = true)
    void framingRolesBuy(int index, CallbackInfo ci) {
        if (org.agmas.noellesroles.btt.BttIdentity.isBttMode(player.getWorld())) { return; } // C-028/C-047：BTT 局跳过 NR 店逻辑（不 cancel，放行后续处理器）
        GameWorldComponent gameWorldComponent = GameWorldComponent.KEY.get(player.getWorld());
        if (gameWorldComponent.isRole(player,Noellesroles.MIMIC) || gameWorldComponent.isRole(player,Noellesroles.EXECUTIONER) || gameWorldComponent.isRole(player,Noellesroles.JESTER)) {

            ShopEntry entry = Noellesroles.FRAMING_ROLES_SHOP.get(index);

            if (balance >= entry.price()) {
                this.balance -= entry.price();
                sync();
                entry.onBuy(player);
                PlayerEntity var6 = this.player;
                if (var6 instanceof ServerPlayerEntity) {
                    ServerPlayerEntity player = (ServerPlayerEntity) var6;
                    player.networkHandler.sendPacket(new PlaySoundS2CPacket(Registries.SOUND_EVENT.getEntry(WatheSounds.UI_SHOP_BUY), SoundCategory.PLAYERS, player.getX(), player.getY(), player.getZ(), 1.0F, 0.9F + this.player.getRandom().nextFloat() * 0.2F, player.getRandom().nextLong()));
                }
            } else {
                this.player.sendMessage(Text.literal("Purchase Failed").formatted(Formatting.DARK_RED), true);
                PlayerEntity var4 = this.player;
                if (var4 instanceof ServerPlayerEntity) {
                    ServerPlayerEntity player = (ServerPlayerEntity) var4;
                    player.networkHandler.sendPacket(new PlaySoundS2CPacket(Registries.SOUND_EVENT.getEntry(WatheSounds.UI_SHOP_BUY_FAIL), SoundCategory.PLAYERS, player.getX(), player.getY(), player.getZ(), 1.0F, 0.9F + this.player.getRandom().nextFloat() * 0.2F, player.getRandom().nextLong()));
                }
            }
            ci.cancel();
        }
    }

}
