package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;

/**
 * BT-ARCH-001：声明式身份定义。每身份最多挂四个钩子，全部为 BTT 特有规则；
 * wathe/NR 子系统（killPlayer/尸体/mood/psycho/商店/计时）直接调用原 API，不在此包装。
 * <p>
 * 定义表见 {@link BttRoleDefs}；派发点：
 * <ul>
 *   <li>kit → {@code BttEvents}（ModdedRoleAssigned）+ 失忆患者取遗物</li>
 *   <li>onKill → {@code BttKillHookMixin}（全局杀人历史/祭品协议之后、处决规则之前）</li>
 *   <li>onTick → {@code BttEvents}（END_SERVER_TICK，BTT 局内 running 门控后）</li>
 *   <li>use → {@code BttEvents}（UseEntityCallback，主手/BTT/running 门控后）</li>
 * </ul>
 */
public final class BttRoleDef {
    /** 身份分配时发放初始物品/初始化回合状态（doc"初始拥有"；失忆患者复用） */
    public interface Kit {
        void give(ServerPlayerEntity player);
    }

    /** 本身份玩家击杀他人（killPlayer 内；reason=死因；victim 可能为非服侧实体） */
    public interface OnKill {
        void accept(ServerPlayerEntity shooter, PlayerEntity victim, Identifier reason, GameWorldComponent gwc);
    }

    /** BTT 局内每服务端 tick（仅 running 时） */
    public interface OnTick {
        void tick(ServerPlayerEntity player, ServerWorld world, GameWorldComponent gwc);
    }

    /** 主手对实体右键（已过 BTT/running 门控）；返回 PASS = 本身份不处理该目标 */
    public interface Use {
        ActionResult use(ServerPlayerEntity user, Entity target, GameWorldComponent gwc);
    }

    public final Role role;
    private Kit kit;
    private OnKill onKill;
    private OnTick onTick;
    private Use use;

    BttRoleDef(Role role) {
        this.role = role;
    }

    public void dispatchKit(ServerPlayerEntity player) {
        if (kit != null) kit.give(player);
    }

    public void dispatchKill(ServerPlayerEntity shooter, PlayerEntity victim, Identifier reason, GameWorldComponent gwc) {
        if (onKill != null) onKill.accept(shooter, victim, reason, gwc);
    }

    public void dispatchTick(ServerPlayerEntity player, ServerWorld world, GameWorldComponent gwc) {
        if (onTick != null) onTick.tick(player, world, gwc);
    }

    public ActionResult dispatchUse(ServerPlayerEntity user, Entity target, GameWorldComponent gwc) {
        return use == null ? ActionResult.PASS : use.use(user, target, gwc);
    }

    // ===== builder（仅 BttRoleDefs 同包使用） =====

    static BttRoleDef of(Role role) {
        return new BttRoleDef(role);
    }

    BttRoleDef kit(Kit kit) {
        this.kit = kit;
        return this;
    }

    BttRoleDef onKill(OnKill onKill) {
        this.onKill = onKill;
        return this;
    }

    BttRoleDef onTick(OnTick onTick) {
        this.onTick = onTick;
        return this;
    }

    BttRoleDef use(Use use) {
        this.use = use;
        return this;
    }
}
