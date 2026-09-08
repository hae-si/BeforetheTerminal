package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 花匠小花（C-062，掉落物方案）：三种掉落物=三生长阶段。
 * 种子期 20s：虚拟（无实体，隐形）；幼苗期 40s：POPPY 掉落物（走近=拔除清除，20m 范围玩家收到提示）；
 * 成花期：WITHER_ROSE 掉落物（非花匠走近=死亡，花随后死亡）。
 * 花匠自己捡不了（pickupDelay=MAX + 距离判定豁免）；实体每 100t 重建防 5 分钟消失。
 * 相邻小花 ≥20m；不能露天（isSkyVisibleAdjacent）。{花匠尾声}期间仅花匠 20m 内的成花攻击。
 */
public final class BttFlowers {
    private BttFlowers() {}

    public static final int SEED_TICKS = 400;    // 20s
    public static final int SPROUT_TICKS = 800;  // 40s
    public static final double PICK_RADIUS = 1.2;
    public static final double HINT_RADIUS = 20.0;
    public static final double EPILOGUE_RADIUS = 20.0;
    private static final int ENTITY_REFRESH = 100;

    public static final class Flower {
        public ServerWorld world;
        public BlockPos pos;
        public UUID gardener;
        public int stage;      // 0=种子 1=幼苗 2=成花
        public int stageTicks;
        public ItemEntity entity; // 种子期=null
    }

    private static final List<Flower> FLOWERS = new ArrayList<>();

    public static int count(ServerWorld world) {
        int n = 0;
        for (Flower f : FLOWERS) if (f.world == world) n++;
        return n;
    }

    public static void clear() {
        for (Flower f : FLOWERS) discard(f);
        FLOWERS.clear();
    }

    /** <栽培>：返回错误消息，null=成功 */
    public static String plant(ServerPlayerEntity gardener) {
        ServerWorld world = gardener.getServerWorld();
        if (dev.doctor4t.wathe.Wathe.isSkyVisibleAdjacent(gardener)) {
            return "不能在露天栽培。";
        }
        for (Flower f : FLOWERS) {
            if (f.world == world && f.pos.getSquaredDistance(gardener.getBlockPos()) < 20 * 20) {
                return "距离其他小花太近（需 ≥20 米）。";
            }
        }
        Flower f = new Flower();
        f.world = world;
        f.pos = gardener.getBlockPos();
        f.gardener = gardener.getUuid();
        f.stage = 0;
        f.stageTicks = 0;
        FLOWERS.add(f);
        return null;
    }

    /** 每 tick（GameMode 循环调用；epilogueGardener={花匠尾声}进行中 → 仅花匠 20m 内成花攻击） */
    public static void tick(ServerWorld world, ServerPlayerEntity gardener, boolean gardenerEpilogue) {
        List<Flower> mine = new ArrayList<>();
        for (Flower f : FLOWERS) if (f.world == world) mine.add(f);
        for (Flower f : mine) {
            f.stageTicks++;
            if (f.stage == 0 && f.stageTicks >= SEED_TICKS) {
                f.stage = 1;
                f.stageTicks = 0;
                spawnEntity(f, new ItemStack(Items.POPPY));
                hintNearby(f, "附近传来花香……");
            } else if (f.stage == 1 && f.stageTicks >= SPROUT_TICKS) {
                f.stage = 2;
                f.stageTicks = 0;
                spawnEntity(f, new ItemStack(Items.WITHER_ROSE));
            }
            if (f.entity != null && f.entity.isRemoved()) f.entity = null;
            if (f.entity != null && f.stageTicks % ENTITY_REFRESH == 0) {
                ItemStack stack = f.entity.getStack();
                f.entity.discard();
                spawnEntity(f, stack); // 重建防 5 分钟消失（age 归零）
            }

            boolean attacks = f.stage == 2 && (!gardenerEpilogue
                    || (gardener != null && gardener.getBlockPos().getSquaredDistance(f.pos) <= EPILOGUE_RADIUS * EPILOGUE_RADIUS));
            if (!attacks) continue;

            ServerPlayerEntity touched = nearestPlayer(f, gardener);
            if (touched == null) continue;
            if (f.stage == 1) {
                discard(f); // 拔除
                FLOWERS.remove(f);
            } else {
                ServerPlayerEntity owner = world.getPlayerByUuid(f.gardener) instanceof ServerPlayerEntity o ? o : null;
                GameFunctions.killPlayer(touched, true,
                        owner instanceof ServerPlayerEntity o ? o : null, GameConstants.DeathReasons.GENERIC);
                discard(f); // 成花杀人后死亡
                FLOWERS.remove(f);
            }
        }
    }

    private static ServerPlayerEntity nearestPlayer(Flower f, ServerPlayerEntity exempt) {
        ServerPlayerEntity best = null;
        double bestDist = PICK_RADIUS * PICK_RADIUS;
        for (ServerPlayerEntity p : f.world.getPlayers()) {
            if (p == exempt || !GameFunctions.isPlayerAliveAndSurvival(p)) continue;
            if (p.getUuid().equals(f.gardener)) continue; // 花匠自己捡不了
            double d = p.getBlockPos().getSquaredDistance(f.pos);
            if (d <= bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    private static void hintNearby(Flower f, String message) {
        for (ServerPlayerEntity p : f.world.getPlayers()) {
            if (p.getBlockPos().getSquaredDistance(f.pos) <= HINT_RADIUS * HINT_RADIUS) {
                p.sendMessage(Text.literal(message).formatted(Formatting.GREEN), true);
            }
        }
    }

    private static void spawnEntity(Flower f, ItemStack stack) {
        ItemEntity entity = new ItemEntity(f.world,
                f.pos.getX() + 0.5, f.pos.getY() + 0.5, f.pos.getZ() + 0.5, stack);
        entity.setPickupDelay(Integer.MAX_VALUE); // 永不可拾取（交互走距离判定）
        f.world.spawnEntity(entity);
        f.entity = entity;
    }

    private static void discard(Flower f) {
        if (f.entity != null) f.entity.discard();
    }
}
