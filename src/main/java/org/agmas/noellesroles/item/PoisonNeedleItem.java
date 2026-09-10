package org.agmas.noellesroles.item;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerPoisonComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.btt.BttRoles;

import java.util.List;

/**
 * 毒针（C-084，doc 炼金术士初始 [毒针]）：右键身边者注入毒药，冷却 30 秒。
 * 口径：固定 40 秒中毒（wathe {@link PlayerPoisonComponent} 原生判定，死因 wathe `poison`）；
 * 目标已中毒则**加速**（剩余时间 - 40 秒，参照 NRS 毒师口径，最少 1 tick）。
 * doc「或餐盘」= 餐盘注入未实装 → ROADMAP BT-ITEM-SET / BT-SYS-SHOP。
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
        alchemist.getItemCooldownManager().set(this,
                GameConstants.ITEM_COOLDOWNS.getOrDefault(this, GameConstants.getInTicks(0, 30)));
        alchemist.sendMessage(Text.literal("毒针已刺入 " + victim.getName().getString() + " 的皮肤。")
                .formatted(Formatting.DARK_GREEN), true);
        return ActionResult.SUCCESS;
    }
}
