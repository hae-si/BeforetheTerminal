package org.agmas.noellesroles.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.List;

/**
 * 对讲机（docx：**所有凶手以及卧底**初始持有；**呼叫需要拿出对讲机，而收听不需要**；C-130）。
 * <p>
 * 物品本身没有动作：**手持**（主手/副手）说话即进入凶手频道，**随身持有**（任意格）即可收听。
 * 语音中继 = {@code NoellesrolesVoiceChatPlugin#walkieTalkieEvent}（照 WatheSpark 口径：发送方手持、
 * 接收方物品栏持有；位置取**接收者自身**、距离 8，因此不受常规近距离限制）。
 * <p>
 * 掉落：docx 把 [对讲机] 列在**不会掉落**的道具里——wathe `shouldDropOnDeath` 默认 false、NR 只登记了
 * [万能钥匙]，故本物品天然不掉落。
 */
public class WalkieTalkieItem extends Item {
    public WalkieTalkieItem(Settings settings) {
        super(settings);
    }

    /** 是否**手持**（主手或副手）——"呼叫需要拿出对讲机" */
    public static boolean isHeld(PlayerEntity player) {
        return player.getMainHandStack().isOf(org.agmas.noellesroles.ModItems.WALKIE_TALKIE)
                || player.getOffHandStack().isOf(org.agmas.noellesroles.ModItems.WALKIE_TALKIE);
    }

    /** 是否**随身持有**（主包 + 副手）——"收听不需要（拿出）" */
    public static boolean isCarried(PlayerEntity player) {
        if (player.getOffHandStack().isOf(org.agmas.noellesroles.ModItems.WALKIE_TALKIE)) return true;
        for (ItemStack stack : player.getInventory().main) {
            if (stack.isOf(org.agmas.noellesroles.ModItems.WALKIE_TALKIE)) return true;
        }
        return false;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.noellesroles.walkie_talkie.tooltip"));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
