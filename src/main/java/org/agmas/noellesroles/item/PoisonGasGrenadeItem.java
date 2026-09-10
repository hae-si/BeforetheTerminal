package org.agmas.noellesroles.item;

import dev.doctor4t.wathe.index.WatheSounds;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.agmas.noellesroles.NoellesRolesEntities;
import org.agmas.noellesroles.btt.entity.BttPoisonGasBombEntity;

import java.util.List;

/**
 * 毒气弹（C-084，doc 炼金术士商店 200 专属商品「充满房间」）：右键投掷 →
 * 命中后生成毒气云（BFS 扩散填充所在房间，滞留 5 秒中毒）。投掷手感同 wathe 手雷。
 * 投放路径见 ROADMAP BT-SYS-SHOP（身份专属商店条目未实装，当前仅 /give 可取得）。
 */
public class PoisonGasGrenadeItem extends Item {
    public PoisonGasGrenadeItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.noellesroles.poison_gas_grenade.tooltip"));
        super.appendTooltip(stack, context, tooltip, type);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        world.playSound(null, user.getX(), user.getY(), user.getZ(), WatheSounds.ITEM_GRENADE_THROW,
                SoundCategory.NEUTRAL, 0.5F, 1.0F + (world.random.nextFloat() - 0.5F) / 10.0F);
        if (!world.isClient()) {
            BttPoisonGasBombEntity bomb = new BttPoisonGasBombEntity(NoellesRolesEntities.POISON_GAS_BOMB, world);
            bomb.setOwner(user);
            bomb.setPos(user.getX(), user.getEyeY() - 0.1, user.getZ());
            bomb.setVelocity(user, user.getPitch(), user.getYaw(), 0.0F, 0.5F, 1.0F);
            world.spawnEntity(bomb);
        }
        user.incrementStat(Stats.USED.getOrCreateStat(this));
        stack.decrementUnlessCreative(1, user);
        return TypedActionResult.success(stack, world.isClient());
    }
}
