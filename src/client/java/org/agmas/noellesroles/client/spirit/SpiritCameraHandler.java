package org.agmas.noellesroles.client.spirit;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.agmas.noellesroles.btt.BttPlayerComponent;

/**
 * 梦游病 &lt;入梦&gt; 客户端相机开关（C-087；流程参照 NRS {@code SpiritCameraHandler} / Freecam）。
 * <p>
 * 由 {@code NoellesrolesClient} 的 END_CLIENT_TICK 每 tick 调用 {@link #tick()}：
 * 读服务端同步的 {@link BttPlayerComponent#isProjecting()}，自己开关假相机；
 * 出窍期间把真实玩家的 {@code input} 换成空 {@link Input}（保留潜行位），使躯体原地静止。
 * <p>
 * 差异（剥离 fork/多余项）：不做视角透视记忆（wathe 游戏内强制第一人称）、不做被吞特判
 * （BTT 未实装饕餮 &lt;绑架&gt;）、不额外抑制声音包（doc 未要求）。
 */
public final class SpiritCameraHandler {
    private SpiritCameraHandler() {}

    private static final MinecraftClient MC = MinecraftClient.getInstance();

    /** 假相机实体 id（负 id 不撞车） */
    private static final int CAMERA_ID = -421;

    private static SpiritCamera spiritCamera;
    private static boolean active = false;

    public static boolean isActive() {
        return active;
    }

    public static SpiritCamera getSpiritCamera() {
        return spiritCamera;
    }

    public static void enable() {
        if (active || MC.player == null || MC.world == null) return;

        MC.chunkCullingEnabled = false;
        MC.gameRenderer.setRenderHand(false);

        BttPlayerComponent pc = BttPlayerComponent.KEY.get(MC.player);
        spiritCamera = new SpiritCamera(CAMERA_ID);
        spiritCamera.setBodyPosition(pc.bodyX, pc.bodyY, pc.bodyZ);
        spiritCamera.applyPosition(MC.player);
        spiritCamera.spawn();
        MC.setCameraEntity(spiritCamera);

        active = true;
    }

    public static void disable() {
        if (!active) return;

        MC.chunkCullingEnabled = true;
        MC.gameRenderer.setRenderHand(true);
        if (MC.player != null) {
            MC.setCameraEntity(MC.player);
        }
        if (spiritCamera != null) {
            spiritCamera.despawn();
            spiritCamera.input = new Input();
            spiritCamera = null;
        }
        if (MC.player != null) {
            MC.player.input = new KeyboardInput(MC.options);
        }

        active = false;
    }

    /** 每客户端 tick：状态同步 + 输入冻结（由 NoellesrolesClient 调用） */
    public static void tick() {
        if (MC.player == null) return;

        boolean projecting = BttPlayerComponent.KEY.get(MC.player).isProjecting();
        if (projecting && !active) {
            enable();
        } else if (!projecting && active) {
            disable();
        }

        if (active) {
            // 躯体静止：真实玩家输入清空（保留潜行）
            if (MC.player.input instanceof KeyboardInput) {
                Input frozen = new Input();
                frozen.sneaking = MC.player.input.sneaking;
                MC.player.input = frozen;
            }
            MC.gameRenderer.setRenderHand(false);
        }
    }
}
