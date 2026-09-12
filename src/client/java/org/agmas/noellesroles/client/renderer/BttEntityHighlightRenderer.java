package org.agmas.noellesroles.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.btt.BttPlayerComponent;
import org.agmas.noellesroles.btt.BttRoles;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 记者 &lt;跟踪&gt; / 工程师 &lt;扫描&gt; 的**本机透视描边**（C-089）。
 * <p>
 * 为什么不再用 {@code Entity.setGlowing}：glowing 是**实体共享旗标**，服务端一置所有客户端都看得见
 * ——实测表现为「莫名其妙有个玩家一直发光，把他刀死又换别人发光」。改为只在本机按**自己**的组件状态
 * 自绘穿墙描边；与 C-083 建筑师门描边同款通道（AFTER_TRANSLUCENT + DEBUG_LINES + 关深度测试）。
 * <p>
 * 口径（2026-09-11 用户裁定）：记者**只**透视自己标记的那名玩家——策划案已删除「未标记时自动盯最远者」，
 * 故无标记 / 标记失效时**不描边**。
 * <p>
 * 依赖 {@link BttPlayerComponent#markedTarget} / {@link BttPlayerComponent#engineerScanTicks} 已同步到
 * 本机（CCA 自同步；服务端在赋值时、以及扫描计时期间周期同步）。
 */
public final class BttEntityHighlightRenderer {
    private BttEntityHighlightRenderer() {}

    /** 记者描边色（金） */
    private static final float[] JOURNALIST_RGB = {1.0F, 0.85F, 0.27F};
    /** 工程师描边色（青） */
    private static final float[] ENGINEER_RGB = {0.29F, 0.82F, 0.94F};
    /** 窃贼 <搜刮> 后全员透视色 = 窃贼职业色（与 C-092 尸体透视同口径） */
    private static final float[] THIEF_RGB = rgb(BttRoles.THIEF.color());
    /** 民俗学家「透视使用者」描边色 = 民俗学家职业色（C-131） */
    private static final float[] FOLKLORIST_RGB = rgb(BttRoles.FOLKLORIST.color());

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(BttEntityHighlightRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        if (!BttIdentity.isBttMode(client.world)) return;
        GameWorldComponent gwc = GameWorldComponent.KEY.get(client.world);
        if (!gwc.isRunning()) return;
        if (!GameFunctions.isPlayerAliveAndSurvival(client.player)) return;
        MatrixStack matrices = context.matrixStack();
        if (matrices == null) return;

        BttPlayerComponent own = BttPlayerComponent.KEY.get(client.player);
        List<Entity> targets = new ArrayList<>();
        float[] rgb = null;

        // 记者：只描边自己显式标记的那名玩家（无标记 / 标记已死 → 不描边）
        if (gwc.isRole(client.player, BttRoles.JOURNALIST)) {
            Entity target = markedTarget(client, own);
            if (target != null) {
                targets.add(target);
                rgb = JOURNALIST_RGB;
            }
        }
        // 工程师：<扫描> 剩余读秒内全车存活者（除自己）
        if (own.engineerScanTicks > 0) {
            int added = 0;
            for (AbstractClientPlayerEntity o : client.world.getPlayers()) {
                if (o == client.player || !GameFunctions.isPlayerAliveAndSurvival(o)) continue;
                targets.add(o);
                added++;
            }
            if (added > 0) rgb = ENGINEER_RGB;
        }
        // 窃贼：<搜刮> 成功后 10 秒内全车存活者（除自己）——C-095（照 NRS vulture highlightTicks 口径：always + 角色色）
        if (own.thiefRevealTicks > 0) {
            int added = 0;
            for (AbstractClientPlayerEntity o : client.world.getPlayers()) {
                if (o == client.player || !GameFunctions.isPlayerAliveAndSurvival(o)) continue;
                if (!targets.contains(o)) targets.add(o);
                added++;
            }
            if (added > 0) rgb = THIEF_RGB;
        }
        // 民俗学家（C-131）：被动透视「任何人」类技能的**使用者** 10 秒（只描该人）
        if (own.folkTicks > 0 && !own.folkTarget.isEmpty()) {
            Entity target = playerByUuid(client, own.folkTarget);
            if (target != null && !targets.contains(target)) {
                targets.add(target);
                rgb = FOLKLORIST_RGB;
            }
        }
        if (targets.isEmpty() || rgb == null) return;

        Vec3d camera = context.camera().getPos();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(3.0F);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        for (Entity target : targets) {
            drawBox(matrices, buffer, camera, target, rgb);
        }
        BuiltBuffer built = buffer.endNullable();
        if (built != null) BufferRenderer.drawWithGlobalProgram(built);

        RenderSystem.lineWidth(1.0F);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }

    /** ARGB → 归一化 rgb */
    private static float[] rgb(int argb) {
        return new float[]{((argb >> 16) & 0xFF) / 255.0F, ((argb >> 8) & 0xFF) / 255.0F, (argb & 0xFF) / 255.0F};
    }

    /** 记者 &lt;跟踪&gt; 的目标 = 自己标记且仍存活的玩家；无有效标记返回 null */
    /** 按 UUID 找存活玩家（民俗学家「透视使用者」用；C-131） */
    private static Entity playerByUuid(MinecraftClient client, String uuid) {
        try {
            UUID id = UUID.fromString(uuid);
            for (AbstractClientPlayerEntity o : client.world.getPlayers()) {
                if (o.getUuid().equals(id) && GameFunctions.isPlayerAliveAndSurvival(o)) return o;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return null;
    }

    private static Entity markedTarget(MinecraftClient client, BttPlayerComponent own) {
        if (own.markedTarget.isEmpty()) return null;
        try {
            UUID marked = UUID.fromString(own.markedTarget);
            for (AbstractClientPlayerEntity o : client.world.getPlayers()) {
                if (o.getUuid().equals(marked) && GameFunctions.isPlayerAliveAndSurvival(o)) return o;
            }
        } catch (IllegalArgumentException ignored) {
        }
        return null;
    }

    /** 实体碰撞箱 12 条边（轻微外扩防与模型面重叠闪烁） */
    private static void drawBox(MatrixStack matrices, BufferBuilder buffer, Vec3d camera, Entity entity, float[] rgb) {
        Box box = entity.getBoundingBox().expand(0.05);
        float x1 = (float) (box.minX - camera.x);
        float y1 = (float) (box.minY - camera.y);
        float z1 = (float) (box.minZ - camera.z);
        float x2 = (float) (box.maxX - camera.x);
        float y2 = (float) (box.maxY - camera.y);
        float z2 = (float) (box.maxZ - camera.z);

        matrices.push();
        Matrix4f mat = matrices.peek().getPositionMatrix();
        line(buffer, mat, rgb, x1, y1, z1, x2, y1, z1);
        line(buffer, mat, rgb, x2, y1, z1, x2, y1, z2);
        line(buffer, mat, rgb, x2, y1, z2, x1, y1, z2);
        line(buffer, mat, rgb, x1, y1, z2, x1, y1, z1);
        line(buffer, mat, rgb, x1, y2, z1, x2, y2, z1);
        line(buffer, mat, rgb, x2, y2, z1, x2, y2, z2);
        line(buffer, mat, rgb, x2, y2, z2, x1, y2, z2);
        line(buffer, mat, rgb, x1, y2, z2, x1, y2, z1);
        line(buffer, mat, rgb, x1, y1, z1, x1, y2, z1);
        line(buffer, mat, rgb, x2, y1, z1, x2, y2, z1);
        line(buffer, mat, rgb, x2, y1, z2, x2, y2, z2);
        line(buffer, mat, rgb, x1, y1, z2, x1, y2, z2);
        matrices.pop();
    }

    private static void line(BufferBuilder buffer, Matrix4f mat, float[] rgb,
                             float x1, float y1, float z1, float x2, float y2, float z2) {
        buffer.vertex(mat, x1, y1, z1).color(rgb[0], rgb[1], rgb[2], 1.0F);
        buffer.vertex(mat, x2, y2, z2).color(rgb[0], rgb[1], rgb[2], 1.0F);
    }
}
