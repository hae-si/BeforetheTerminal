package org.agmas.noellesroles.item;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.block_entity.BeveragePlateBlockEntity;
import dev.doctor4t.wathe.cca.PlayerPoisonComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.btt.BttRoles;

import java.util.List;

/**
 * 毒针（C-084，doc 炼金术士初始 [毒针]）：右键身边者注入毒药，冷却 30 秒。
 * 口径：固定 40 秒中毒（wathe {@link PlayerPoisonComponent} 原生判定，死因 wathe `poison`）；
 * 目标已中毒则**加速**（剩余时间 - 40 秒，参照 NRS 毒师口径，最少 1 tick）。
 * 餐盘/酒杯注入（doc「给身边者或餐盘注入毒药」；ROADMAP BT-NEEDLE-TRAY，C-101）：
 * 右键餐盘/酒杯块 → `BeveragePlateBlockEntity.setPoisoner`，毒标记由取餐者带走并随进食生效（wathe 原生链）。
 */
public class PoisonNeedleItem extends Item {
    /** 一次注入的中毒时长（40 秒） */
    private static final int BASE_POISON_TICKS = GameConstants.getInTicks(0, 40);
    /** 身边者口径（与 BTT 身边者技能同格数上限；实际由原版交互距离限制） */
    private static final double RANGE = 3.0;

    public PoisonNeedleItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.noellesroles.poison_needle.tooltip"));
        super.appendTooltip(stack, context, tooltip, type);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (!(entity instanceof PlayerEntity target) || target == user) return ActionResult.PASS;
        World world = user.getWorld();
        if (world.isClient()) return ActionResult.PASS;
        if (!(user instanceof ServerPlayerEntity alchemist) || !(target instanceof ServerPlayerEntity victim)) {
            return ActionResult.PASS;
        }
        if (!BttIdentity.isBttMode(world)) return ActionResult.PASS;
        GameWorldComponent gwc = GameWorldComponent.KEY.get(world);
        if (!gwc.isRunning()) return ActionResult.PASS;
        if (!gwc.isRole(alchemist, BttRoles.ALCHEMIST)) return ActionResult.PASS;
        if (!GameFunctions.isPlayerAliveAndSurvival(victim)) return ActionResult.PASS;
        if (alchemist.distanceTo(victim) > RANGE) return ActionResult.PASS;
        if (alchemist.getItemCooldownManager().isCoolingDown(this)) return ActionResult.PASS;

        PlayerPoisonComponent poison = PlayerPoisonComponent.KEY.get(victim);
        int ticks = poison.poisonTicks > 0
                ? Math.max(1, poison.poisonTicks - BASE_POISON_TICKS)
                : BASE_POISON_TICKS;
        poison.setPoisonTicks(ticks, alchemist.getUuid());
        applyCooldown(alchemist);
        alchemist.sendMessage(Text.translatable("noellesroles.btt.action.alchemist.inject", victim.getName().getString())
                .withColor(BttRoles.ALCHEMIST.color()), true);
        return ActionResult.SUCCESS;
    }

    /**
     * 餐盘/酒杯注入（doc「给身边者或餐盘注入毒药」；C-101，ROADMAP BT-NEEDLE-TRAY）。
     * 与 wathe {@code FoodPlatterBlock.onUse} 的 POISON_VIAL 分支同口径：`getPoisoner() == null` 才可下毒
     * （已有毒不覆盖、不扣冷却）；毒标记由取餐者带走（wathe `WatheDataComponentTypes.POISONER` → `PlayerEntityMixin` 进食生效）。
     */
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (world.isClient()) return ActionResult.PASS;
        if (context.getHand() != Hand.MAIN_HAND) return ActionResult.PASS; // 与 wathe 毒瓶同口径：只看主手
        if (!(world.getBlockEntity(context.getBlockPos()) instanceof BeveragePlateBlockEntity plate)) return ActionResult.PASS;
        if (!(context.getPlayer() instanceof ServerPlayerEntity alchemist)) return ActionResult.PASS;
        if (!BttIdentity.isBttMode(world)) return ActionResult.PASS;
        GameWorldComponent gwc = GameWorldComponent.KEY.get(world);
        if (!gwc.isRunning()) return ActionResult.PASS;
        if (!gwc.isRole(alchemist, BttRoles.ALCHEMIST)) return ActionResult.PASS;
        if (plate.getPoisoner() != null) {
            alchemist.sendMessage(Text.translatable("noellesroles.btt.action.alchemist.tray_used")
                    .withColor(BttRoles.ALCHEMIST.color()), true);
            return ActionResult.SUCCESS;
        }
        if (alchemist.getItemCooldownManager().isCoolingDown(this)) return ActionResult.PASS;
        plate.setPoisoner(alchemist.getUuidAsString());
        applyCooldown(alchemist);
        alchemist.sendMessage(Text.translatable("noellesroles.btt.action.alchemist.tray_poisoned")
                .withColor(BttRoles.ALCHEMIST.color()), true);
        return ActionResult.SUCCESS;
    }

    /** 物品层冷却 = 该物品在 `GameConstants.ITEM_COOLDOWNS` 的登记值（缺省 30 秒；C-099 口径） */
    private void applyCooldown(ServerPlayerEntity alchemist) {
        alchemist.getItemCooldownManager().set(this,
                GameConstants.ITEM_COOLDOWNS.getOrDefault(this, GameConstants.getInTicks(0, 30)));
    }
}
