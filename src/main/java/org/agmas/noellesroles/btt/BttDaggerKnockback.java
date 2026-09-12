package org.agmas.noellesroles.btt;

import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.ModItems;
import org.agmas.noellesroles.Noellesroles;

/**
 * 匕首击退（C-133）：持[匕首]时挂 +0.5 攻击击退（数值/操作同 wathe 刀），松开即摘。
 * <p>
 * ★必须是**独立类**而不是 mixin 里的静态字段：mixin 的静态字段初始化会被并入目标类的
 * {@code <clinit>}，而 {@code LivingEntity} 在 Bootstrap 早期（Items/FoodComponents 之前）就初始化，
 * 会连锁加载本 mod 类 → 实测导致 wathe {@code WatheItems} 初始化 NPE 崩服。
 */
public final class BttDaggerKnockback {
    private BttDaggerKnockback() {}

    private static final Identifier MODIFIER_ID = Identifier.of(Noellesroles.MOD_ID, "dagger_knockback_modifier");
    private static final EntityAttributeModifier MODIFIER = new EntityAttributeModifier(
            MODIFIER_ID, 0.5, EntityAttributeModifier.Operation.ADD_VALUE);

    /** 每 tick（LivingEntity tick HEAD）：按主手是否为匕首增删属性修饰符 */
    public static void tick(PlayerEntity player) {
        EntityAttributeInstance attribute = player.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_KNOCKBACK);
        if (attribute == null) return;
        boolean dagger = player.getMainHandStack().isOf(ModItems.DAGGER);
        boolean hasModifier = attribute.hasModifier(MODIFIER_ID);
        if (dagger && !hasModifier) {
            attribute.addPersistentModifier(MODIFIER);
        } else if (!dagger && hasModifier) {
            attribute.removeModifier(MODIFIER_ID);
        }
    }
}
