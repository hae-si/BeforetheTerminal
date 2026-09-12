package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.block_entity.DoorBlockEntity;
import dev.doctor4t.wathe.index.WatheSounds;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

/**
 * 锁匠 Locksmith（docx 2026-09-12 新增身份，C-119）：G 键 &lt;上锁&gt; 准星所指的门 → **坚不可摧直到本人开锁**。
 * <p>
 * 实现口径：
 * <ul>
 *   <li>门锁 = wathe {@link DoorBlockEntity#setJammed(int)} 的**长时长**（被卡的门本身拒绝 toggle）；
 *       {@code BttDoorStateMixin} 另在 {@code blast()} HEAD 拦截撬棍，做到"坚不可摧"。</li>
 *   <li>门主记录在 {@link BttGameWorldComponent#doorLocks}（`维度;pos.asLong()` → 锁匠 UUID）；同一锁匠再按一次 → 开锁。</li>
 *   <li>**开局清锁**（{@code initializeGame} → {@link #clearAll}）：`jammedTime` 存在方块 NBT 里，
 *       若不清理会跨局残留（含服务器重启后残留的记录）。</li>
 * </ul>
 * 与建筑师的交互：建筑师 &lt;修复&gt; 会清掉被卡（即解锁）——属既有门状态链，作者未禁止。
 */
public final class BttLocksmith {
    private BttLocksmith() {}

    /** 上锁作用距离（准星方块射线，格；同建筑师） */
    private static final double RANGE = 4.0;
    /** 锁定时长：等效"永久"（wathe 每 tick -1；同永久醉口径 MAX/4，远离溢出） */
    private static final int LOCK_TICKS = Integer.MAX_VALUE / 4;

    /** &lt;上锁&gt; / 开锁：G 键直发（无目标、无选人屏） */
    public static void toggle(ServerPlayerEntity user) {
        if (!(user.getWorld() instanceof ServerWorld world)) return;
        var ability = org.agmas.noellesroles.AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        if (!(user.raycast(RANGE, 1.0F, false) instanceof BlockHitResult hit)) {
            user.sendMessage(Text.translatable("noellesroles.btt.action.locksmith.no_door")
                    .withColor(BttRoles.LOCKSMITH.color()), true);
            return;
        }
        DoorBlockEntity door = BttArchitect.doorAt(world, hit.getBlockPos());
        if (door == null) {
            user.sendMessage(Text.translatable("noellesroles.btt.action.locksmith.no_door")
                    .withColor(BttRoles.LOCKSMITH.color()), true);
            return;
        }
        BttGameWorldComponent btt = BttGameWorldComponent.KEY.get(world);
        String key = key(world, door.getPos());
        String owner = btt.doorLocks.get(key);
        if (user.getUuid().toString().equals(owner)) {
            door.setJammed(0);
            door.sync();
            btt.doorLocks.remove(key);
            btt.sync();
            setCd(ability);
            world.playSound(null, door.getPos(), WatheSounds.BLOCK_DOOR_TOGGLE, SoundCategory.BLOCKS, 1f, 0.8f);
            user.sendMessage(Text.translatable("noellesroles.btt.action.locksmith.unlocked")
                    .withColor(BttRoles.LOCKSMITH.color()), true);
            return;
        }
        if (owner != null) {
            // 别人的锁：不能解（也没有权限提示——文本原则：不暴露他人信息）
            user.sendMessage(Text.translatable("noellesroles.btt.action.locksmith.foreign")
                    .withColor(BttRoles.LOCKSMITH.color()), true);
            return;
        }
        door.setJammed(LOCK_TICKS);
        door.sync();
        btt.doorLocks.put(key, user.getUuid().toString());
        btt.sync();
        setCd(ability);
        world.playSound(null, door.getPos(), WatheSounds.BLOCK_DOOR_TOGGLE, SoundCategory.BLOCKS, 1f, 0.6f);
        user.sendMessage(Text.translatable("noellesroles.btt.action.locksmith.locked")
                .withColor(BttRoles.LOCKSMITH.color()), true);
    }

    /** 该门是否被锁匠锁住（`BttDoorStateMixin` 拦撬棍用） */
    public static boolean isLocked(ServerWorld world, BlockPos pos) {
        return BttGameWorldComponent.KEY.get(world).doorLocks.containsKey(key(world, pos));
    }

    /** 开局清锁：把所有记录过的门恢复为未卡，并清空记录（跨局/重启残留一并处理） */
    public static void clearAll(ServerWorld world) {
        BttGameWorldComponent btt = BttGameWorldComponent.KEY.get(world);
        if (btt.doorLocks.isEmpty()) return;
        for (String key : new java.util.ArrayList<>(btt.doorLocks.keySet())) {
            BlockPos pos = parsePos(key);
            if (pos == null) continue;
            DoorBlockEntity door = BttArchitect.doorAt(world, pos);
            if (door != null) {
                door.setJammed(0);
                door.sync();
            }
        }
        btt.doorLocks.clear();
        btt.sync();
    }

    private static void setCd(org.agmas.noellesroles.AbilityPlayerComponent ability) {
        ability.setCooldown(BttRoleDefs.CD_1MIN);
        ability.sync();
    }

    /** 维度 + 方块坐标的稳定键（跨维度不撞车） */
    private static String key(ServerWorld world, BlockPos pos) {
        return world.getRegistryKey().getValue() + ";" + pos.asLong();
    }

    private static BlockPos parsePos(String key) {
        int i = key.indexOf(';');
        if (i < 0) return null;
        try {
            return BlockPos.fromLong(Long.parseLong(key.substring(i + 1)));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
