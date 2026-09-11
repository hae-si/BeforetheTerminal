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
import org.agmas.noellesroles.btt.BttRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 女仆：餐盘取餐（C-032 定案后回接——C-032 证明此前摘除属误伤）。
 * doc"可拿取双倍的食物和饮料"；口径 = NRS `mixin/waiter/WaiterPlatterMixin`（2026-09-11 用户裁定"本局累计 ≤2 显然不是设计意图，看 NRS 的 waiter"）：
 * 普通玩家同类只能持 1 份，女仆**可同时持 2 份餐盘/托盘物品**；持满 2 份即拒绝，**吃完/送出后可再取**（无本局累计上限）。
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
        // NRS 口径：女仆**同时**最多持 2 份"餐盘类"物品（持满即拒绝；吃完/赠出后可再取，无本局累计上限）
        if (heldFromPlatter(player, platter) >= 2) {
            cir.setReturnValue(ActionResult.SUCCESS);
            return;
        }
        ItemStack randomItem = platter.get(world.random.nextInt(platter.size())).copy();
        randomItem.setCount(1);
        randomItem.set(DataComponentTypes.MAX_STACK_SIZE, 1);
        String poisoner = blockEntity.getPoisoner();
        if (poisoner != null) {
            randomItem.set(WatheDataComponentTypes.POISONER, poisoner);
            blockEntity.setPoisoner(null);
        }
        player.playSoundToPlayer(SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 1f, 1f);
        player.setStackInHand(Hand.MAIN_HAND, randomItem);
        // 无数值需累加：上限 = 当前持有份数（C-102）
        cir.setReturnValue(ActionResult.SUCCESS);
    }

    /** 女仆当前持有的"餐盘类"物品份数（同 NRS `matchCount`：按餐盘上出现过的种类匹配、逐格计 1 份） */
    private static int heldFromPlatter(PlayerEntity player, List<ItemStack> platter) {
        java.util.Set<net.minecraft.item.Item> types = new java.util.HashSet<>();
        for (ItemStack platterItem : platter) types.add(platterItem.getItem());
        int n = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack invItem = player.getInventory().getStack(i);
            if (!invItem.isEmpty() && types.contains(invItem.getItem())) n++;
        }
        return n;
    }
}
