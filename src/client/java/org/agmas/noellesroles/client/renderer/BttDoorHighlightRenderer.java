package org.agmas.noellesroles.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.doctor4t.wathe.block.SmallDoorBlock;
import dev.doctor4t.wathe.block_entity.DoorBlockEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * C-083 建筑师 门描边（移植 NRS EngineerDoorHighlightRenderer）：被撬/被卡的门 5 秒透视描边。
 * 描边几何直接取方块自身 outline shape（含开门滑移位移），不写死模型常量；被撬=红，被卡=金。
 */
public final class BttDoorHighlightRenderer {
    private BttDoorHighlightRenderer() {}

    /** 描边时长（5 秒，NRS 口径） */
    private static final int DURATION_TICKS = 100;

    private static final List<Entry> ENTRIES = new ArrayList<>();

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(BttDoorHighlightRenderer::render);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) {
                ENTRIES.clear();
                return;
            }
            Iterator<Entry> it = ENTRIES.iterator();
            while (it.hasNext()) {
                if (--it.next().remainingTicks <= 0) it.remove();
            }
        });
    }

    /** S2C 回执：同一位置重复触发只刷新计时 */
    public static void onHighlight(BlockPos pos, boolean blasted) {
        for (Entry entry : ENTRIES) {
            if (entry.pos.equals(pos)) {
                entry.remainingTicks = DURATION_TICKS;
                entry.blasted = blasted;
                return;
            }
        }
        ENTRIES.add(new Entry(pos, blasted));
    }

    private static void render(WorldRenderContext context) {
        if (ENTRIES.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;
        Vec3d camera = context.camera().getPos();

        // 透视描边（穿墙可见）+ position_color/DEBUG_LINES（无需法线，12 条边可靠）
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(4.0F);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        for (Entry entry : ENTRIES) {
            drawDoor(client, matrices, buffer, camera, entry);
        }
        BuiltBuffer built = buffer.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);

        RenderSystem.lineWidth(1.0F);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }

    private static void drawDoor(MinecraftClient client, MatrixStack matrices, BufferBuilder buffer,
                                 Vec3d camera, Entry entry) {
        if (client.world == null || client.player == null) return;
        BlockPos lower = lowerPos(client, entry.pos);
        if (lower == null) return;
        BlockState state = client.world.getBlockState(lower);
        VoxelShape shape = state.getOutlineShape(client.world, lower, ShapeContext.of(client.player));
        if (shape.isEmpty()) return;
        Box box = shape.getBoundingBox();
        float green = entry.blasted ? 0.19F : 0.78F;
        float blue = entry.blasted ? 0.19F : 0.13F;
        matrices.push();
        matrices.translate(lower.getX() - camera.x, lower.getY() - camera.y, lower.getZ() - camera.z);
        drawBox(matrices, buffer,
                (float) box.minX / 16F, (float) box.minY / 16F, (float) box.minZ / 16F,
                (float) box.maxX / 16F, (float) box.maxY / 16F, (float) box.maxZ / 16F,
                1.0F, green, blue);
        matrices.pop();
    }

    /** 门方块实体挂在下半格（小门/车厢门同）：命中上半格时下移一格 */
    private static BlockPos lowerPos(MinecraftClient client, BlockPos pos) {
        if (client.world == null) return null;
        BlockState state = client.world.getBlockState(pos);
        if (state.getBlock() instanceof SmallDoorBlock && state.contains(SmallDoorBlock.HALF)
                && state.get(SmallDoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            return pos.down();
        }
        if (client.world.getBlockEntity(pos) instanceof DoorBlockEntity) return pos;
        if (client.world.getBlockEntity(pos.down()) instanceof DoorBlockEntity) return pos.down();
        return null;
    }

    /** box 的全部 12 条边（DEBUG_LINES + POSITION_COLOR，无需法线） */
    private static void drawBox(MatrixStack matrices, BufferBuilder buffer,
                                float x1, float y1, float z1, float x2, float y2, float z2,
                                float red, float green, float blue) {
        Matrix4f mat = matrices.peek().getPositionMatrix();
        line(buffer, mat, red, green, blue, x1, y1, z1, x2, y1, z1);
        line(buffer, mat, red, green, blue, x2, y1, z1, x2, y1, z2);
        line(buffer, mat, red, green, blue, x2, y1, z2, x1, y1, z2);
        line(buffer, mat, red, green, blue, x1, y1, z2, x1, y1, z1);
        line(buffer, mat, red, green, blue, x1, y2, z1, x2, y2, z1);
        line(buffer, mat, red, green, blue, x2, y2, z1, x2, y2, z2);
        line(buffer, mat, red, green, blue, x2, y2, z2, x1, y2, z2);
        line(buffer, mat, red, green, blue, x1, y2, z2, x1, y2, z1);
        line(buffer, mat, red, green, blue, x1, y1, z1, x1, y2, z1);
        line(buffer, mat, red, green, blue, x2, y1, z1, x2, y2, z1);
        line(buffer, mat, red, green, blue, x2, y1, z2, x2, y2, z2);
        line(buffer, mat, red, green, blue, x1, y1, z2, x1, y2, z2);
    }

    private static void line(BufferBuilder buffer, Matrix4f mat, float red, float green, float blue,
                             float x1, float y1, float z1, float x2, float y2, float z2) {
        buffer.vertex(mat, x1, y1, z1).color(red, green, blue, 1.0F);
        buffer.vertex(mat, x2, y2, z2).color(red, green, blue, 1.0F);
    }

    private static final class Entry {
        private final BlockPos pos;
        private boolean blasted;
        private int remainingTicks = DURATION_TICKS;

        private Entry(BlockPos pos, boolean blasted) {
            this.pos = pos;
            this.blasted = blasted;
        }
    }
}
