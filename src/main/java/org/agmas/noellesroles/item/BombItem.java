package org.agmas.noellesroles.item;

import dev.doctor4t.wathe.cca.GameWorldComponent;
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
import org.agmas.noellesroles.btt.BttBomb;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.btt.BttPlayerComponent;
import org.agmas.noellesroles.btt.BttRoles;

import java.util.List;

/**
 * 炸弹箱（C-084，doc 恐怖分子初始 [炸弹箱]）——**取代原 G 键「安放炸弹」技能**：
 * <ul>
 *   <li>恐怖分子右键准星所指玩家 → 安放炸弹（5 秒静默 → 15 秒倒计时），**物品进 30 秒冷却**（doc：冷却 30 秒）；</li>
 *   <li>任何人身上带着倒计时的炸弹时右键他人 → 传递（3 秒传递冷却，与被安放者无关）；</li>
 *   <li>传递入口另有 G 键（{@code BttGuessReceiver} → {@link BttBomb#transfer}），两条入口共用同一状态机。</li>
 * </ul>
 * 物品**不消耗**（doc：30 秒冷却后可再安放；省一份纹理 = 安放/传递共用一个物品）。
 */
public class BombItem extends Item {
    public BombItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.noellesroles.bomb.tooltip"));
        super.appendTooltip(stack, context, tooltip, type);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (!(entity instanceof PlayerEntity target) || target == user) return ActionResult.PASS;
        World world = user.getWorld();
        if (world.isClient()) return ActionResult.PASS;
        if (!(user instanceof ServerPlayerEntity owner) || !(target instanceof ServerPlayerEntity victim)) {
            return ActionResult.PASS;
        }
        if (!BttIdentity.isBttMode(world)) return ActionResult.PASS;
        GameWorldComponent gwc = GameWorldComponent.KEY.get(world);
        if (!gwc.isRunning()) return ActionResult.PASS;
        if (!GameFunctions.isPlayerAliveAndSurvival(victim)) return ActionResult.PASS;

        // 持有倒计时炸弹者：传递（不受安放冷却/身份限制）
        BttPlayerComponent holder = BttPlayerComponent.KEY.get(owner);
        if (holder.bombPlaced && holder.bombBeeping) {
            return BttBomb.transfer(owner, victim) ? ActionResult.CONSUME : ActionResult.PASS;
        }

        // 恐怖分子安放
        if (!gwc.isRole(owner, BttRoles.TERRORIST)) return ActionResult.PASS;
        if (owner.getItemCooldownManager().isCoolingDown(this)) return ActionResult.PASS;
        if (!BttBomb.place(owner, victim)) return ActionResult.PASS;
        owner.getItemCooldownManager().set(this, BttBomb.PLACE_CD_TICKS);
        return ActionResult.CONSUME;
    }
}
