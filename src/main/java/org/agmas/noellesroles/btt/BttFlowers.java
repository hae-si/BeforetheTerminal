package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
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
 * 种子期 20s：虚拟（无实体，隐形）；幼苗期 40s：**红树胎生苗**掉落物（走近=拔除清除，20m 范围玩家收到提示）；
 * 成花期：**铃兰**掉落物（非花匠走近=被吞噬，死因 `noellesroles:bloom`"绽放"；花随后死亡）。
 * 花匠自己捡不了（pickupDelay=MAX + 距离判定豁免）；实体每 100t 重建防 5 分钟消失。
 * 相邻小花 ≥20m（"不能露天"已按用户裁定删除：该限制原为"小花数量≈胜利条件"而设，现已无关）；
 * 花匠可**被动透视**全部小花（C-091，走本能高亮通道、免按键）。
 * 每株小花=花匠 1 层护盾（挡一次致命伤后花谢，见 BttEvents）。
 */
public final class BttFlowers {
    private BttFlowers() {}

    public static final int SEED_TICKS = 400;    // 20s
    public static final int SPROUT_TICKS = 800;  // 40s
    public static final double PICK_RADIUS = 1.2;
    public static final double HINT_RADIUS = 20.0;
    private static final int ENTITY_REFRESH = 100;

    /** 幼苗期掉落物：红树胎生苗（C-091 用户指定） */
    public static final Item SPROUT_ITEM = Items.MANGROVE_PROPAGULE;
    /** 成花期掉落物：铃兰（C-091 用户指定） */
    public static final Item BLOOM_ITEM = Items.LILY_OF_THE_VALLEY;

    /** 是否为小花实体（花匠透视用；C-091） */
    public static boolean isFlower(ItemStack stack) {
        return stack.isOf(SPROUT_ITEM) || stack.isOf(BLOOM_ITEM);
    }

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

    /** 移除一株花（花匠护盾消耗） */
    public static void removeOne(ServerWorld world) {
        for (Flower f : FLOWERS) {
            if (f.world == world) {
                discard(f);
                FLOWERS.remove(f);
                return;
            }
        }
    }

    public static void clear() {
        for (Flower f : FLOWERS) discard(f);
        FLOWERS.clear();
    }

    /** <栽培>：返回错误消息，null=成功 */
    public static String plant(ServerPlayerEntity gardener) {
        ServerWorld world = gardener.getServerWorld();
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

    /** 每 tick（GameMode 循环调用） */
    public static void tick(ServerWorld world, ServerPlayerEntity gardener) {
        List<Flower> mine = new ArrayList<>();
        for (Flower f : FLOWERS) if (f.world == world) mine.add(f);
        for (Flower f : mine) {
            f.stageTicks++;
            if (f.stage == 0 && f.stageTicks >= SEED_TICKS) {
                f.stage = 1;
                f.stageTicks = 0;
                spawnEntity(f, new ItemStack(SPROUT_ITEM));
                hintNearby(f, "附近传来花香……");
            } else if (f.stage == 1 && f.stageTicks >= SPROUT_TICKS) {
                f.stage = 2;
                f.stageTicks = 0;
                spawnEntity(f, new ItemStack(BLOOM_ITEM));
            }
            if (f.entity != null && f.entity.isRemoved()) f.entity = null;
            if (f.entity != null && f.stageTicks > 0 && f.stageTicks % ENTITY_REFRESH == 0) {
                ItemStack stack = f.entity.getStack();
                f.entity.discard();
                spawnEntity(f, stack); // 重建防 5 分钟消失（age 归零）
            }

            // 幼苗期：非花匠玩家走近 → 拔除
            if (f.stage == 1) {
                if (nearestPlayer(f, gardener) != null) {
                    discard(f);
                    FLOWERS.remove(f);
                }
                continue;
            }
            // 成花期：杀死第一个靠近者后花谢
            if (f.stage == 2) {
                ServerPlayerEntity touched = nearestPlayer(f, gardener);
                if (touched == null) continue;
                ServerPlayerEntity owner = world.getPlayerByUuid(f.gardener) instanceof ServerPlayerEntity o ? o : null;
                GameFunctions.killPlayer(touched, true, owner, BttDeathReasons.BLOOM);
                discard(f);
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
        if (f.entity != null) f.entity.discard(); // C-091：阶段推进必须换掉旧实体（原先幼苗长成花后，胎生苗仍留在地上）
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
