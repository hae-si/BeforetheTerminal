package org.agmas.noellesroles.item;

import dev.doctor4t.wathe.cca.GameWorldComponent;
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
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.agmas.noellesroles.btt.BttIdentity;

import java.util.List;

/**
 * 匕首（C-084，doc 清道夫初始 [匕首]）：右键准星所指玩家 → **即时、无声**刀杀，命中后 1 分钟冷却。
 * 与 wathe 刀的区别：无蓄力（useOnEntity 直判）、无刺杀音效、冷却写在**本物品**上（不是 wathe 刀）。
 * 冷却/范围口径 = doc「1 分钟冷却、无蓄力、无声」；射击判定交给原版交互距离（约 3 格），另加硬校验。
 */
public class DaggerItem extends Item {
    /** 匕首作用半径（与 wathe 刀一致：3 格） */
    private static final double RANGE = 3.0;

    public DaggerItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.noellesroles.dagger.tooltip"));
        super.appendTooltip(stack, context, tooltip, type);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (!(entity instanceof PlayerEntity target) || target == user) return ActionResult.PASS;
        World world = user.getWorld();
        if (world.isClient()) return ActionResult.PASS;
        if (!(user instanceof ServerPlayerEntity attacker) || !(target instanceof ServerPlayerEntity victim)) {
            return ActionResult.PASS;
        }
        if (!BttIdentity.isBttMode(world)) return ActionResult.PASS;
        if (!GameWorldComponent.KEY.get(world).isRunning()) return ActionResult.PASS;
        if (!GameFunctions.isPlayerAliveAndSurvival(victim)) return ActionResult.PASS;
        if (attacker.distanceTo(victim) > RANGE) return ActionResult.PASS;
        if (attacker.getItemCooldownManager().isCoolingDown(this)) return ActionResult.PASS;

        GameFunctions.killPlayer(victim, true, attacker, GameConstants.DeathReasons.KNIFE);
        attacker.swingHand(hand);
        if (!attacker.isCreative()) {
            attacker.getItemCooldownManager().set(this,
                    GameConstants.ITEM_COOLDOWNS.getOrDefault(this, GameConstants.getInTicks(1, 0)));
        }
        return ActionResult.SUCCESS;
    }
}
