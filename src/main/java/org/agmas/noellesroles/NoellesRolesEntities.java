package org.agmas.noellesroles;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.btt.entity.BttPoisonGasBombEntity;
import org.agmas.noellesroles.btt.entity.BttPoisonGasCloudEntity;
import org.agmas.noellesroles.entities.RoleMineEntity;

public class NoellesRolesEntities {
    public static final EntityType<RoleMineEntity> ROLE_MINE_ENTITY_ENTITY_TYPE = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(Noellesroles.MOD_ID, "cube"),
            EntityType.Builder.create(RoleMineEntity::new, SpawnGroup.MISC).dimensions(0.75f, 0.75f).build("cube")
    );

    // ===== BTT 毒气弹（C-084）=====
    /** 投掷物：命中后生成毒气云 */
    public static final EntityType<BttPoisonGasBombEntity> POISON_GAS_BOMB = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(Noellesroles.MOD_ID, "poison_gas_bomb"),
            EntityType.Builder.create(BttPoisonGasBombEntity::new, SpawnGroup.MISC)
                    .dimensions(0.25f, 0.25f).build("poison_gas_bomb")
    );
    /** 毒气云：BFS 扩散 + 滞留中毒（无渲染实体，粒子表现） */
    public static final EntityType<BttPoisonGasCloudEntity> POISON_GAS_CLOUD = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(Noellesroles.MOD_ID, "poison_gas_cloud"),
            EntityType.Builder.create(BttPoisonGasCloudEntity::new, SpawnGroup.MISC)
                    .dimensions(0.1f, 0.1f).build("poison_gas_cloud")
    );

    public static void init() {}
}
