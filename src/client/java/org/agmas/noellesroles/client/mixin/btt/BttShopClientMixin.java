package org.agmas.noellesroles.client.mixin.btt;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedHandledScreen;
import dev.doctor4t.wathe.client.gui.screen.ingame.LimitedInventoryScreen;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.btt.BttRoles;
import org.agmas.noellesroles.btt.BttShopGate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * BTT 商店展示（C-047，NR 四店同模式）：init 内对 {@code GameConstants.SHOP_ENTRIES}
 * 的读取改道为 {@link BttShopGate#BTT_ENTRIES}（BTT 局内）——条目、索引、布局全部由
 * 自有表驱动，与 BttShopBuyMixin 服务端同序；不再需要逐 widget 隐藏。
 * 另保留"仅主犯/从犯可见"门控（原 C-033 行为）。
 */
@Mixin(LimitedInventoryScreen.class)
public abstract class BttShopClientMixin extends LimitedHandledScreen<PlayerScreenHandler> {

    @Shadow @Final public ClientPlayerEntity player;

    public BttShopClientMixin(PlayerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    // BTT：商店仅凶手阵营可见（处子=innocent 不显示；卧底=mimic 非 killer 阵营不显示）
    @WrapOperation(method = "init", at = @At(value = "INVOKE",
            target = "Ldev/doctor4t/wathe/cca/GameWorldComponent;canUseKillerFeatures(Lnet/minecraft/entity/player/PlayerEntity;)Z"))
    private static boolean bttKillerOnlyShop(GameWorldComponent instance, PlayerEntity p, Operation<Boolean> original) {
        if (!BttIdentity.isBttMode(p.getWorld())) return original.call(instance, p);
        var role = instance.getRole(p);
        var f = role == null ? null : BttRoles.factionOf(role);
        return f == BttRoles.Faction.PRINCIPAL || f == BttRoles.Faction.ACCOMPLICE;
    }

    // BTT：条目源改道自有表（索引与 BttShopBuyMixin 同序）
    @ModifyExpressionValue(method = "init",
            at = @At(value = "FIELD", target = "Ldev/doctor4t/wathe/game/GameConstants;SHOP_ENTRIES:Ljava/util/List;"))
    private List<ShopEntry> bttShopEntries(List<ShopEntry> original) {
        if (player == null || !BttIdentity.isBttMode(player.getWorld())) return original;
        return BttShopGate.BTT_ENTRIES;
    }
}
