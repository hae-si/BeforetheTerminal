package org.agmas.noellesroles.item;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.agmas.noellesroles.btt.BttDeathReasons;
import org.agmas.noellesroles.btt.BttIdentity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 飞剑（C-136，doc 剑客初始 [剑]「扔出飞剑贯穿一行人；冷却 1 分钟」）。
 * <p>
 * 实现 = **射线贯穿**：右键后沿视线取一条长射线，命中线上所有存活玩家（按距离排序依次击杀），
 * 墙体阻断（逐目标 `canSee` 视线校验，与纵火犯 3 格视线口径同款）。死亡理由 = `noellesroles:flying_sword`（"飞剑"）。
 * 冷却走 {@code ItemCooldownManager}（1 分钟，登记在 {@code ModItems.init}）。
 * <p>
 * 【待作者】占位：纹理=铁剑（`minecraft:item/iron_sword`）；飞剑音效/飞行物表现未做（现为即时射线）。
 */
public class SwordItem extends Item {
    /** 贯穿距离（格）：一条线上的全部目标都会中招 */
    private static final double RANGE = 24.0;
    /** 射线半径（格）：目标中心到射线的垂直距离小于它才算"在线上" */
    private static final double RADIUS = 1.25;

    public SwordItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.noellesroles.sword.tooltip"));
        super.appendTooltip(stack, context, tooltip, type);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!(user instanceof ServerPlayerEntity swordsman)) return TypedActionResult.pass(stack);
        if (!BttIdentity.isBttMode(world)) return TypedActionResult.pass(stack);
        if (!GameWorldComponent.KEY.get(world).isRunning()) return TypedActionResult.pass(stack);
        if (swordsman.getItemCooldownManager().isCoolingDown(this)) return TypedActionResult.pass(stack);

        Vec3d eye = swordsman.getEyePos();
        Vec3d dir = swordsman.getRotationVec(1.0F);
        List<ServerPlayerEntity> hit = new ArrayList<>();
        List<Double> alongs = new ArrayList<>();
        for (ServerPlayerEntity target : swordsman.getServerWorld().getPlayers()) {
            if (target == swordsman || !GameFunctions.isPlayerAliveAndSurvival(target)) continue;
            Vec3d to = target.getPos().add(0, target.getHeight() / 2.0, 0).subtract(eye);
            double along = to.dotProduct(dir);
            if (along <= 0 || along > RANGE) continue;
            if (to.subtract(dir.multiply(along)).length() > RADIUS) continue;
            if (!swordsman.canSee(target)) continue; // 墙后不中
            hit.add(target);
            alongs.add(along);
        }
        if (hit.isEmpty()) return TypedActionResult.pass(stack); // 未命中不消耗冷却（同建筑师/花匠口径）

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < hit.size(); i++) order.add(i);
        order.sort(Comparator.comparingDouble(alongs::get)); // 由近及远贯穿
        for (int i : order) {
            GameFunctions.killPlayer(hit.get(i), true, swordsman, BttDeathReasons.FLYING_SWORD);
        }
        swordsman.swingHand(hand);
        if (!swordsman.isCreative()) {
            swordsman.getItemCooldownManager().set(this,
                    GameConstants.ITEM_COOLDOWNS.getOrDefault(this, GameConstants.getInTicks(1, 0)));
        }
        return TypedActionResult.success(stack);
    }
}
