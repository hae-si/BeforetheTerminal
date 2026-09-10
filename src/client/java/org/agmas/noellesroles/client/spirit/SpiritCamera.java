package org.agmas.noellesroles.client.spirit;

import com.mojang.authlib.GameProfile;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import net.minecraft.block.BlockState;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.client.network.ClientConnectionState;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.server.ServerLinks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import org.agmas.noellesroles.client.mixin.btt.BttKeyBindingAccessor;

import java.util.Collections;
import java.util.UUID;

/**
 * 梦游病 &lt;入梦&gt; 的假相机实体（C-087；逻辑参照 NRS {@code SpiritCamera} / Freecam，剥离 fork API）。
 * <p>
 * 要点：
 * <ul>
 *   <li>独立 {@link ClientPlayerEntity} 子实体（**不是**旁观者模式——wathe 用 gamemode 表示生死，
 *       切旁观会被判成死者）；</li>
 *   <li>私接 {@link ClientPlayNetworkHandler}，{@code sendPacket} 空实现：否则这个假实体的
 *       移动包会把真实玩家"传送"到灵魂位置；</li>
 *   <li>水平飞行沿用原版飞行分支（WASD 不被 wathe 抑制）；**垂直自控**——原版上浮读 jumpKey，
 *       而 wathe 会在存活玩家身上把它抑制成 false，wathe 1.3.2 没有 fork 的按键放行事件；</li>
 *   <li>{@code noClip = true} 穿墙（省掉 NRS 的 BlockState 空碰撞 mixin）；</li>
 *   <li>位置裁剪：躯体 30 格半径 + wathe 地图 playArea。</li>
 * </ul>
 */
public class SpiritCamera extends ClientPlayerEntity {

    private static final MinecraftClient MC = MinecraftClient.getInstance();

    /** 灵魂离躯体的最远距离（格；doc 未规定，沿用 NRS 30） */
    static final double MAX_RADIUS = 30.0;

    /** 原版飞行速度 = wathe 行走速度的等效值（0.07 * 0.09/0.4；不读 fork 的移动配置） */
    private static final float FLY_SPEED = 0.07f * (0.09f / 0.4f);

    /** 自控垂直速度（格/tick） */
    private static final double VERTICAL_SPEED = 0.2;

    private double bodyX;
    private double bodyY;
    private double bodyZ;

    private static final ClientPlayNetworkHandler DUMMY_HANDLER = new ClientPlayNetworkHandler(
            MC,
            MC.getNetworkHandler().getConnection(),
            new ClientConnectionState(
                    new GameProfile(UUID.randomUUID(), "SpiritCamera"),
                    MC.getTelemetryManager().createWorldSession(false, null, null),
                    DynamicRegistryManager.Immutable.EMPTY,
                    FeatureSet.empty(),
                    null,
                    MC.getCurrentServerEntry(),
                    MC.currentScreen,
                    Collections.emptyMap(),
                    MC.inGameHud.getChatHud().toChatState(),
                    false,
                    Collections.emptyMap(),
                    ServerLinks.EMPTY
            )
    ) {
        @Override
        public void sendPacket(Packet<?> packet) {
            // 假实体：一个包都不发（否则会把真实玩家"传送"到灵魂位置）
        }
    };

    public SpiritCamera(int id) {
        super(MC, MC.world, DUMMY_HANDLER, MC.player.getStatHandler(), MC.player.getRecipeBook(), false, false);
        setId(id);
        setPose(EntityPose.SWIMMING);
        setNoGravity(true);
        this.noClip = true;
        getAbilities().flying = true;
        input = new KeyboardInput(MC.options);
    }

    public void applyPosition(Entity entity) {
        double y = getSwimmingY(entity);
        refreshPositionAndAngles(entity.getX(), y, entity.getZ(), entity.getYaw(), entity.getPitch());
        renderPitch = getPitch();
        renderYaw = getYaw();
        lastRenderPitch = renderPitch;
        lastRenderYaw = renderYaw;
    }

    /** 让假相机的"眼"落在本体眼高上（SWIMMING 姿态眼高更低） */
    private static double getSwimmingY(Entity entity) {
        if (entity.getPose() == EntityPose.SWIMMING) {
            return entity.getY();
        }
        return entity.getY() - entity.getEyeHeight(EntityPose.SWIMMING) + entity.getEyeHeight(entity.getPose());
    }

    public void spawn() {
        if (clientWorld != null) {
            clientWorld.addEntity(this);
        }
    }

    public void despawn() {
        if (clientWorld != null && clientWorld.getEntityById(getId()) != null) {
            clientWorld.removeEntity(getId(), RemovalReason.DISCARDED);
        }
    }

    public void setBodyPosition(double x, double y, double z) {
        this.bodyX = x;
        this.bodyY = y;
        this.bodyZ = z;
    }

    // ===== 物理豁免 =====

    @Override
    protected void fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {
    }

    @Override
    public boolean isClimbing() {
        return false;
    }

    @Override
    public boolean isTouchingWater() {
        return false;
    }

    /** 关掉原版"游泳"分支，垂直位移完全自控 */
    @Override
    public boolean isSwimming() {
        return false;
    }

    @Override
    protected void onSwimmingStart() {
    }

    @Override
    public PistonBehavior getPistonBehavior() {
        return PistonBehavior.IGNORE;
    }

    @Override
    public boolean collidesWith(Entity other) {
        return false;
    }

    @Override
    public boolean shouldSlowDown() {
        return false;
    }

    @Override
    public void setPose(EntityPose pose) {
        super.setPose(EntityPose.SWIMMING);
    }

    /** 药水效果委托本体（夜视等渲染仍生效） */
    @Override
    public StatusEffectInstance getStatusEffect(RegistryEntry<StatusEffect> effect) {
        return MC.player.getStatusEffect(effect);
    }

    // ===== 移动 =====

    @Override
    public void tickMovement() {
        // PlayerEntity.tick() 每 tick 开头都会把 noClip 复位成 isSpectator()(=false)，水平移动走
        // Entity.move 因而照旧撞墙（垂直位移是本类手动 setPosition，所以看起来"上下能穿墙"）——
        // 必须在移动前重新置位。
        this.noClip = true;
        getAbilities().flying = true;
        getAbilities().setFlySpeed(FLY_SPEED);

        double beforeY = getY();
        super.tickMovement(); // 水平：原版飞行分支 + 假相机自己的 KeyboardInput

        // 垂直：自控（原版上浮读 jumpKey，wathe 会把它抑制成 false）
        double want = 0;
        if (jumpHeld()) want += VERTICAL_SPEED;
        if (input.sneaking) want -= VERTICAL_SPEED;
        setPosition(getX(), beforeY + want, getZ());
        setVelocity(getVelocity().x, 0, getVelocity().z);
        setOnGround(false);

        clampPosition();
    }

    /** 原始上浮键状态（绕过 wathe 对 jumpKey 的抑制） */
    static boolean jumpHeld() {
        return ((BttKeyBindingAccessor) MC.options.jumpKey).noellesroles$isPressedRaw();
    }

    /** 限制在 playArea 与躯体 30 格半径内 */
    private void clampPosition() {
        double newX = getX();
        double newY = getY();
        double newZ = getZ();
        boolean clamped = false;

        try {
            Box playArea = MapVariablesWorldComponent.KEY.get(MC.world).getPlayArea();
            if (playArea != null) {
                double cx = MathHelper.clamp(newX, playArea.minX, playArea.maxX);
                double cy = MathHelper.clamp(newY, playArea.minY, playArea.maxY);
                double cz = MathHelper.clamp(newZ, playArea.minZ, playArea.maxZ);
                if (cx != newX || cy != newY || cz != newZ) {
                    newX = cx;
                    newY = cy;
                    newZ = cz;
                    clamped = true;
                }
            }
        } catch (Exception ignored) {
        }

        double dx = newX - bodyX;
        double dy = newY - bodyY;
        double dz = newZ - bodyZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist > MAX_RADIUS) {
            double scale = MAX_RADIUS / dist;
            newX = bodyX + dx * scale;
            newY = bodyY + dy * scale;
            newZ = bodyZ + dz * scale;
            clamped = true;
        }

        if (clamped) {
            setPosition(newX, newY, newZ);
        }
    }
}
