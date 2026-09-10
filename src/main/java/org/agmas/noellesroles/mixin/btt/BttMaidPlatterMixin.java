package org.agmas.noellesroles.mixin.btt;

import dev.doctor4t.wathe.block.FoodPlatterBlock;
import dev.doctor4t.wathe.block_entity.BeveragePlateBlockEntity;
import dev.doctor4t.wathe.index.WatheDataComponentTypes;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.btt.BttPlayerComponent;
import org.agmas.noellesroles.btt.BttRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 女仆：餐盘取餐（C-032 定案后回接——C-032 证明此前摘除属误伤）。
 * doc"可拿取双倍的食物和饮料"；用户口径（2026-09-05）：本局共取 2 份后停止。
 * 注入点=onUse 空手取餐分支首行 getStoredItems；女仆路径复刻原版给予（含毒标记转移）后短路。
 */
@Mixin(FoodPlatterBlock.class)
public abstract class BttMaidPlatterMixin {

    @Inject(method = "onUse", at = @At(value = "INVOKE",
            target = "Ldev/doctor4t/wathe/block_entity/BeveragePlateBlockEntity;getStoredItems()Ljava/util/List;", ordinal = 0),
            cancellable = true)
    private void bttMaidPickup(BlockState state, World world, BlockPos pos, PlayerEntity player,
                               BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        if (world.isClient) return;
        if (!BttIdentity.isBttMode(world)) return;
        if (!(world.getBlockEntity(pos) instanceof BeveragePlateBlockEntity blockEntity)) return;
        if (!dev.doctor4t.wathe.cca.GameWorldComponent.KEY.get(world).isRole(player, BttRoles.MAID)) return;
        if (!player.getStackInHand(Hand.MAIN_HAND).isEmpty()) return;

        List<ItemStack> platter = blockEntity.getStoredItems();
        if (platter.isEmpty()) {
            cir.setReturnValue(ActionResult.SUCCESS);
            return;
        }
        if (BttPlayerComponent.KEY.get(player).maidPickups >= 2) {
            cir.setReturnValue(ActionResult.SUCCESS);
            return;
        }
        // 并发双倍：同款持有 <2 份才可取
        List<ItemStack> eligible = new java.util.ArrayList<>();
        for (ItemStack platterItem : platter) {
            if (countOf(player, platterItem.getItem()) < 2) eligible.add(platterItem);
        }
        if (eligible.isEmpty()) {
            cir.setReturnValue(ActionResult.SUCCESS);
            return;
        }
        ItemStack randomItem = eligible.get(world.random.nextInt(eligible.size())).copy();
        randomItem.setCount(1);
        randomItem.set(DataComponentTypes.MAX_STACK_SIZE, 1);
        String poisoner = blockEntity.getPoisoner();
        if (poisoner != null) {
            randomItem.set(WatheDataComponentTypes.POISONER, poisoner);
            blockEntity.setPoisoner(null);
        }
        player.playSoundToPlayer(SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 1f, 1f);
        player.setStackInHand(Hand.MAIN_HAND, randomItem);
        BttPlayerComponent.KEY.get(player).maidPickups++;
        cir.setReturnValue(ActionResult.SUCCESS);
    }

    private static int countOf(PlayerEntity player, net.minecraft.item.Item item) {
        int n = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (player.getInventory().getStack(i).isOf(item)) n += player.getInventory().getStack(i).getCount();
        }
        return n;
    }
}
