package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.block.SmallDoorBlock;
import dev.doctor4t.wathe.block_entity.DoorBlockEntity;
import dev.doctor4t.wathe.block_entity.SmallDoorBlockEntity;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheSounds;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

/**
 * 建筑师 architect（C-083；docx：听到任意位置被卡/被撬的门并 修复；冷却 2 分钟）。
 * <p>
 * 参考实现 = NRS Engineer（{@code references/NoellesRolesSpark}），取用与差异登记：
 * <ul>
 *   <li>NRS 感知走 {@code DoorStateChanged.BLAST/JAM} —— 该事件是 **WatheSpark fork-only**，官方 wathe 1.3.2 无；
 *       故改由 {@code BttDoorStateMixin} 钩 {@link DoorBlockEntity#jam()}/{@link DoorBlockEntity#blast()} 末尾自行派发
 *       （全车仅 LockpickItem / CrowbarItem 会调用这两个方法，serverTick 的 jammedTime 递减不走这里）。</li>
 *   <li>NRS 用 [维修工具] 物品右键门（修复/上锁/解锁三态）；BTT 策划案只登记 修复，按 G 键无目标技能口径实现。</li>
 * </ul>
 */
public final class BttArchitect {
    private BttArchitect() {}

    /** 修复作用距离（准星方块射线，格） */
    private static final double REPAIR_RANGE = 4.0;

    /** S2C 下行注册（由 {@link BttGuessReceiver#register()} 调用；payload 类型需双端注册） */
    public static void register() {
        PayloadTypeRegistry.playS2C().register(BttDoorHighlightS2CPacket.ID, BttDoorHighlightS2CPacket.CODEC);
    }

    /** 任意位置的门被撬/被卡 → 行动栏提示 + 5 秒描边（发给全车存活建筑师） */
    public static void onDoorTampered(DoorBlockEntity door) {
        if (!(door.getWorld() instanceof ServerWorld world)) return;
        if (!BttIdentity.isBttMode(world)) return;
        GameWorldComponent gwc = GameWorldComponent.KEY.get(world);
        if (!gwc.isRunning()) return;
        boolean blasted = door.isBlasted();
        BlockPos pos = door.getPos();
        for (ServerPlayerEntity architect : world.getPlayers()) {
            if (!gwc.isRole(architect, BttRoles.ARCHITECT)) continue;
            if (!GameFunctions.isPlayerAliveAndSurvival(architect)) continue;
            // C-098：技能通知（动作栏）用身份色
            architect.sendMessage(Text.translatable(blasted
                            ? "noellesroles.btt.action.architect.blasted" : "noellesroles.btt.action.architect.jammed")
                    .withColor(BttRoles.ARCHITECT.color()), true);
            ServerPlayNetworking.send(architect, new BttDoorHighlightS2CPacket(pos, blasted));
        }
    }

    /**
     * 修复：清掉准星所指门的 被撬/被卡（双开门连邻居）。成功返回 true；
     * 未命中门或门无需修复时提示并不消耗冷却（返回 false）。
     */
    public static boolean repair(ServerPlayerEntity architect) {
        if (!(architect.getWorld() instanceof ServerWorld world)) return false;
        if (!(architect.raycast(REPAIR_RANGE, 1.0F, false) instanceof BlockHitResult hit)) return false;
        DoorBlockEntity door = doorAt(world, hit.getBlockPos());
        if (door == null) {
            architect.sendMessage(Text.translatable("noellesroles.btt.action.architect.no_door").withColor(BttRoles.ARCHITECT.color()), true);
            return false;
        }
        if (!door.isBlasted() && !door.isJammed()) {
            architect.sendMessage(Text.translatable("noellesroles.btt.action.architect.intact").withColor(BttRoles.ARCHITECT.color()), true);
            return false;
        }
        restop(world, door);
        // 双开门：邻居同修（两扇共用一次施法）
        BlockState state = world.getBlockState(door.getPos());
        if (door instanceof SmallDoorBlockEntity && state.getBlock() instanceof SmallDoorBlock) {
            SmallDoorBlockEntity neighbor = SmallDoorBlock.getNeighborDoorEntity(state, world, door.getPos());
            if (neighbor != null && (neighbor.isBlasted() || neighbor.isJammed())) {
                restop(world, neighbor);
            }
        }
        world.playSound(null, door.getPos(), WatheSounds.BLOCK_DOOR_TOGGLE, SoundCategory.BLOCKS, 1f, 1.2f);
        architect.sendMessage(Text.translatable("noellesroles.btt.action.architect.repaired").withColor(BttRoles.ARCHITECT.color()), true);
        return true;
    }

    /** 清 被撬（先清标记再关门——DoorBlockEntity#toggle 会拒绝被撬中的门）+ 清 被卡 + 同步客户端 */
    private static void restop(ServerWorld world, DoorBlockEntity door) {
        if (door.isBlasted()) {
            door.setBlasted(false);
            if (door.isOpen()) door.toggle(false);
        }
        if (door.isJammed()) {
            door.setJammed(0);
        }
        door.sync();
    }

    /** 准星命中的方块本体/上下半格找门方块实体（小门 BE 挂在下半格） */
    private static DoorBlockEntity doorAt(ServerWorld world, BlockPos pos) {
        if (world.getBlockEntity(pos) instanceof DoorBlockEntity door) return door;
        if (world.getBlockEntity(pos.up()) instanceof DoorBlockEntity door) return door;
        if (world.getBlockEntity(pos.down()) instanceof DoorBlockEntity door) return door;
        return null;
    }
}
