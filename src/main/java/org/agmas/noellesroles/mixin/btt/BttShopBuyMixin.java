package org.agmas.noellesroles.mixin.btt;

import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.index.WatheSounds;
import dev.doctor4t.wathe.util.ShopEntry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.btt.BttPlayerComponent;
import org.agmas.noellesroles.btt.BttShopGate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BTT 商店购买（C-047，NR 四店同模式）：tryBuy HEAD 接管——BTT 局内按
 * {@link BttShopGate#BTT_ENTRIES} 处理（与客户端展示同序），处理后 cancel，
 * 原版 SHOP_ENTRIES 与 NR 四店逻辑一概不触。语义复制原版 tryBuy（含开发环境余额垫付）。
 */
@Mixin(PlayerShopComponent.class)
public abstract class BttShopBuyMixin {

    @Shadow public int balance;

    @Shadow @Final private PlayerEntity player;

    @Shadow public abstract void sync();

    @Inject(method = "tryBuy", at = @At("HEAD"), cancellable = true)
    private void bttTryBuy(int index, CallbackInfo ci) {
        if (!BttIdentity.isBttMode(player.getWorld())) return;
        ci.cancel();
        // 醉酒：商店失效；凶手用狂气被拒并因此得知自己醉酒（doc：不自知的唯一例外）
        if (BttPlayerComponent.KEY.get(player).isDrunk()) {
            player.sendMessage(Text.literal("你喝醉了，无法使用商店。").formatted(Formatting.BLUE), true);
            return;
        }
        if (index < 0 || index >= BttShopGate.BTT_ENTRIES.size()) return;
        ShopEntry entry = BttShopGate.BTT_ENTRIES.get(index);
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment() && this.balance < entry.price())
            this.balance = entry.price() * 10;
        if (this.balance >= entry.price()
                && !this.player.getItemCooldownManager().isCoolingDown(entry.stack().getItem())
                && entry.onBuy(this.player)) {
            this.balance -= entry.price();
            if (this.player instanceof ServerPlayerEntity player) {
                player.networkHandler.sendPacket(new PlaySoundS2CPacket(Registries.SOUND_EVENT.getEntry(WatheSounds.UI_SHOP_BUY), SoundCategory.PLAYERS, player.getX(), player.getY(), player.getZ(), 1.0f, 0.9f + this.player.getRandom().nextFloat() * 0.2f, this.player.getRandom().nextLong()));
            }
        } else {
            this.player.sendMessage(Text.literal("Purchase Failed").formatted(Formatting.DARK_RED), true);
            if (this.player instanceof ServerPlayerEntity player) {
                player.networkHandler.sendPacket(new PlaySoundS2CPacket(Registries.SOUND_EVENT.getEntry(WatheSounds.UI_SHOP_BUY_FAIL), SoundCategory.PLAYERS, player.getX(), player.getY(), player.getZ(), 1.0f, 0.9f + this.player.getRandom().nextFloat() * 0.2f, this.player.getRandom().nextLong()));
            }
        }
        this.sync();
    }
}
