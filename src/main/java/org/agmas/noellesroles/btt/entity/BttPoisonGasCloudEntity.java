package org.agmas.noellesroles.btt.entity;

import dev.doctor4t.wathe.cca.PlayerPoisonComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 毒气云（C-084，doc 毒气弹「充满房间」）：以 BFS 在**可通行方块**间扩散填充所在房间/车厢，
 * 每 5 tick 扩散一圈、最多 {@link #MAX_GAS_BLOCKS} 格、半径上限 {@link #MAX_RADIUS} 格；
 * 在毒气中连续滞留 {@link #EXPOSURE_THRESHOLD} tick（5 秒）→ 中毒 40 秒（wathe 原生毒判定，死因 poison）。
 * 实心/门/玻璃等碰撞箱非空的方块**不可穿透**（关门即隔断），云本身无渲染实体、只用绿色尘埃粒子表现。
 * 与 NRS 毒气云的差异（未做的部分）：无体力/窒息附加效果 → ROADMAP BT-GAS-CLOUD。
 */
public class BttPoisonGasCloudEntity extends Entity {
    /** 云寿命：30 秒 */
    private static final int MAX_LIFETIME = 600;
    /** 扩散间隔：每 5 tick 一圈 */
    private static final int SPREAD_INTERVAL = 5;
    /** 扩散格数上限（约一节车厢） */
    private static final int MAX_GAS_BLOCKS = 400;
    /** 扩散半径上限（格） */
    private static final double MAX_RADIUS = 12.0;
    /** 滞留多久中毒（5 秒） */
    private static final int EXPOSURE_THRESHOLD = 100;
    /** 中毒时长（40 秒） */
    private static final int POISON_TICKS = 800;
    private static final DustParticleEffect GAS_PARTICLE =
            new DustParticleEffect(new Vector3f(0.35F, 0.75F, 0.25F), 1.5F);

    private final Set<BlockPos> gasBlocks = new HashSet<>();
    private final Map<UUID, Integer> exposure = new HashMap<>();
    private Set<BlockPos> frontier = new HashSet<>();
    private UUID ownerUuid;
    private int age;

    public BttPoisonGasCloudEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    public void setOwnerUuid(UUID uuid) {
        this.ownerUuid = uuid;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.getWorld() instanceof ServerWorld world)) return;
        this.age++;
        if (this.age > MAX_LIFETIME) {
            this.discard();
            return;
        }
        if (this.age == 1) {
            BlockPos origin = this.getBlockPos();
            this.gasBlocks.add(origin);
            this.frontier.add(origin);
        }
        if (this.age % SPREAD_INTERVAL == 0) this.spread(world);
        if (this.age % 3 == 0) this.spawnCloudParticles(world);
        this.applyPoison(world);
    }

    /** BFS 扩散一圈：只进入碰撞箱为空（可通行）的方块 */
    private void spread(ServerWorld world) {
        if (this.frontier.isEmpty() || this.gasBlocks.size() >= MAX_GAS_BLOCKS) return;
        BlockPos origin = this.getBlockPos();
        Set<BlockPos> next = new HashSet<>();
        for (BlockPos pos : this.frontier) {
            if (this.gasBlocks.size() >= MAX_GAS_BLOCKS) break;
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.offset(direction);
                if (this.gasBlocks.contains(neighbor)) continue;
                if (neighbor.getSquaredDistance(origin) > MAX_RADIUS * MAX_RADIUS) continue;
                BlockState state = world.getBlockState(neighbor);
                if (!state.getCollisionShape(world, neighbor).isEmpty()) continue;
                this.gasBlocks.add(neighbor);
                next.add(neighbor);
            }
        }
        this.frontier = next;
    }

    /** 绿色尘埃（双端可见；无自定义渲染实体） */
    private void spawnCloudParticles(ServerWorld world) {
        if (this.gasBlocks.isEmpty()) return;
        List<BlockPos> blocks = new ArrayList<>(this.gasBlocks);
        int samples = Math.min(6, blocks.size());
        for (int i = 0; i < samples; i++) {
            BlockPos pos = blocks.get(world.getRandom().nextInt(blocks.size()));
            world.spawnParticles(GAS_PARTICLE, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
                    1, 0.3, 0.3, 0.3, 0.0);
        }
    }

    /** 滞留中毒：连续在毒气里 5 秒 → 40 秒中毒（离开即清零滞留计时） */
    private void applyPoison(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!GameFunctions.isPlayerAliveAndSurvival(player)) continue;
            BlockPos feet = player.getBlockPos();
            BlockPos eye = BlockPos.ofFloored(player.getX(), player.getEyeY(), player.getZ());
            if (!this.gasBlocks.contains(feet) && !this.gasBlocks.contains(eye)) {
                this.exposure.remove(player.getUuid());
                continue;
            }
            int ticks = this.exposure.merge(player.getUuid(), 1, Integer::sum);
            if (ticks != EXPOSURE_THRESHOLD) continue;
            PlayerPoisonComponent poison = PlayerPoisonComponent.KEY.get(player);
            int poisonTicks = poison.poisonTicks > 0
                    ? Math.max(1, poison.poisonTicks - POISON_TICKS)
                    : POISON_TICKS;
            poison.setPoisonTicks(poisonTicks, this.ownerUuid);
            player.sendMessage(Text.literal("你吸入了毒气！").formatted(Formatting.DARK_GREEN), true);
        }
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.age = nbt.getInt("Age");
        if (nbt.containsUuid("Owner")) this.ownerUuid = nbt.getUuid("Owner");
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Age", this.age);
        if (this.ownerUuid != null) nbt.putUuid("Owner", this.ownerUuid);
    }
}