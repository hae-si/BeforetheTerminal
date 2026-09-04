package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameConstants;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

/**
 * 教父 <查验>：右键注视任意活人或尸体 → 向本人揭示其具体身份（GD/RD：冷却 30 秒）。
 * 服务端权威：服务端自行按视线射线判定目标，客户端无逻辑。
 */
public class BttInspectItem extends Item {

    private static final double RANGE = 64.0;

    public BttInspectItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) {
            return TypedActionResult.consume(stack);
        }
        ServerPlayerEntity player = (ServerPlayerEntity) user;

        GameWorldComponent gwc = GameWorldComponent.KEY.get(world);
        if (!(gwc.getGameMode() instanceof BeforeTheTerminalGameMode)) {
            return TypedActionResult.pass(stack);
        }

        HitResult hit = ProjectileUtil.getCollision(player, e -> e instanceof PlayerEntity || e instanceof PlayerBodyEntity, RANGE);
        Entity target = hit instanceof EntityHitResult ehr ? ehr.getEntity() : null;

        if (target instanceof PlayerEntity living) {
            dev.doctor4t.wathe.api.Role role = gwc.getRole(living);
            if (role == null) {
                say(player, "btt.inspect.no_identity");
            } else {
                player.sendMessage(Text.translatable("btt.inspect.result.living",
                        BttIdentity.displayName(role).formatted(Formatting.BOLD)).formatted(Formatting.YELLOW), false);
            }
        } else if (target instanceof PlayerBodyEntity body) {
            var comp = org.agmas.noellesroles.coroner.BodyDeathReasonComponent.KEY.get(body);
            dev.doctor4t.wathe.api.Role role = comp == null ? null : findRole(comp.playerRole);
            if (role == null) {
                say(player, "btt.inspect.no_identity");
            } else {
                player.sendMessage(Text.translatable("btt.inspect.result.body",
                        BttIdentity.displayName(role).formatted(Formatting.BOLD)).formatted(Formatting.YELLOW), false);
            }
        } else {
            say(player, "btt.inspect.no_target");
        }

        player.getItemCooldownManager().set(this, GameConstants.getInTicks(0, 30));
        return TypedActionResult.consume(stack);
    }

    private static void say(ServerPlayerEntity player, String key) {
        player.sendMessage(Text.translatable(key).formatted(Formatting.RED), false);
    }

    private static dev.doctor4t.wathe.api.Role findRole(net.minecraft.util.Identifier id) {
        for (dev.doctor4t.wathe.api.Role role : dev.doctor4t.wathe.api.WatheRoles.ROLES) {
            if (role.identifier().equals(id)) return role;
        }
        return null;
    }
}
