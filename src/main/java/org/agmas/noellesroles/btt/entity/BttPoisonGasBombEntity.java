package org.agmas.noellesroles.btt.entity;

import dev.doctor4t.wathe.index.WatheSounds;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import org.agmas.noellesroles.ModItems;
import org.agmas.noellesroles.NoellesRolesEntities;

/**
 * 毒气弹投掷物（C-084）：命中即消失并原地生成毒气云（{@link BttPoisonGasCloudEntity}）。
 * 投掷手感参照 wathe {@code GrenadeEntity}，作用改为放毒气而非爆炸。
 */
public class BttPoisonGasBombEntity extends ThrownItemEntity {
    public BttPoisonGasBombEntity(EntityType<?> type, World world) {
        super(NoellesRolesEntities.POISON_GAS_BOMB, world);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.POISON_GAS_GRENADE;
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (!(this.getWorld() instanceof ServerWorld world)) return;
        BttPoisonGasCloudEntity cloud = new BttPoisonGasCloudEntity(NoellesRolesEntities.POISON_GAS_CLOUD, world);
        cloud.setPos(this.getX(), this.getY(), this.getZ());
        if (this.getOwner() instanceof PlayerEntity owner) cloud.setOwnerUuid(owner.getUuid());
        world.spawnEntity(cloud);
        world.playSound(null, this.getBlockPos(), WatheSounds.ITEM_GRENADE_EXPLODE,
                SoundCategory.PLAYERS, 1.0F, 0.6F);
        this.discard();
    }
}