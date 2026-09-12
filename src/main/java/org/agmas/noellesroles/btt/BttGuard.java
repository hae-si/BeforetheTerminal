package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.agmas.noellesroles.AbilityPlayerComponent;

import java.util.UUID;

/**
 * 保镖 `bodyguard` 与 寄生者 `leech`（docx 2026-09-12 新增身份，C-117）。
 * <p>
 * 两者共用同一种「免死绑定」模型（都是 {@code AllowPlayerDeath} 否决 + UUID 绑定）：
 * <ul>
 *   <li><b>保镖</b> &lt;守护&gt; 身边者 30 秒：期间**只有保镖死亡**才会让被守护者死亡，
 *       其余任何致命伤一律否决；守护到期或被守护者死亡即解除。</li>
 *   <li><b>寄生者</b> 仅限一次 &lt;寄生&gt; 身边者：**宿主存活期间寄生者不会死亡**；
 *       宿主死亡后寄生者恢复可被杀（本局寄生不能改选）。</li>
 * </ul>
 * 免死一律走 {@link dev.doctor4t.wathe.api.event.AllowPlayerDeath}（同花匠/教授护盾口径），
 * 不碰 wathe 生死模型。
 */
public final class BttGuard {
    private BttGuard() {}

    /** 保镖 <守护> 作用距离（与其余"身边者"技能一致） */
    private static final double RANGE = 6.0;
    /** 保镖 <守护> 持续 30 秒（docx 2026-09-12） */
    public static final int GUARD_TICKS = GameConstants.getInTicks(0, 30);
    /** 保镖 <守护> 冷却 2 分钟（docx 2026-09-12） */
    public static final int GUARD_CD = GameConstants.getInTicks(2, 0);

    // ===== 保镖 =====

    /** 保镖 &lt;守护&gt;：身边者 30 秒内"只有保镖死亡他才会死亡"（G 键身边者，CD 2 分钟） */
    public static void guard(ServerPlayerEntity bodyguard, ServerPlayerEntity target) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(bodyguard);
        if (ability.cooldown > 0) return;
        if (bodyguard.distanceTo(target) > RANGE) {
            bodyguard.sendMessage(Text.translatable("noellesroles.btt.action.common.not_nearby")
                    .withColor(BttRoles.BODYGUARD.color()), true);
            return;
        }
        ability.setCooldown(GUARD_CD);
        ability.sync();
        BttPlayerComponent g = BttPlayerComponent.KEY.get(bodyguard);
        g.guardTarget = target.getUuid().toString();
        g.sync();
        BttPlayerComponent t = BttPlayerComponent.KEY.get(target);
        t.guardedBy = bodyguard.getUuid().toString();
        t.guardedTicks = GUARD_TICKS;
        t.sync();
        bodyguard.sendMessage(Text.translatable("noellesroles.btt.action.guard.give", target.getName().getString())
                .withColor(BttRoles.BODYGUARD.color()), true);
    }

    // ===== 寄生者 =====

    /** 寄生者 &lt;寄生&gt;（仅限一次）：身边者成为宿主，宿主存活期间寄生者不会死亡 */
    public static void parasitize(ServerPlayerEntity leech, ServerPlayerEntity target) {
        BttPlayerComponent lc = BttPlayerComponent.KEY.get(leech);
        if (!lc.parasiteHost.isEmpty()) return; // 仅限一次（本局已寄生，不能改选）
        if (leech.distanceTo(target) > RANGE) {
            leech.sendMessage(Text.translatable("noellesroles.btt.action.common.not_nearby")
                    .withColor(BttRoles.LEECH.color()), true);
            return;
        }
        lc.parasiteHost = target.getUuid().toString();
        lc.sync();
        leech.sendMessage(Text.translatable("noellesroles.btt.action.leech.bond", target.getName().getString())
                .withColor(BttRoles.LEECH.color()), true);
    }

    // ===== 免死否决（AllowPlayerDeath） =====

    /**
     * 返回 true = 否决这次死亡。
     * 被守护者（守护未过期且保镖存活）与寄生者（宿主存活）一律免死。
     */
    public static boolean vetoDeath(ServerPlayerEntity victim) {
        if (!(victim.getWorld() instanceof ServerWorld world)) return false;
        BttPlayerComponent pc = BttPlayerComponent.KEY.get(victim);
        if (pc.guardedTicks > 0 && !pc.guardedBy.isEmpty()) {
            ServerPlayerEntity guardian = resolve(world, pc.guardedBy);
            if (guardian != null && GameFunctions.isPlayerAliveAndSurvival(guardian)) return true;
            clearGuard(pc);
        }
        if (!pc.parasiteHost.isEmpty()) {
            ServerPlayerEntity host = resolve(world, pc.parasiteHost);
            if (host != null && GameFunctions.isPlayerAliveAndSurvival(host)) return true;
            pc.parasiteHost = ""; // 宿主已死 → 恢复可被杀
            pc.sync();
        }
        return false;
    }

    // ===== 每 tick：守护倒计时 =====

    public static void tick(ServerPlayerEntity player, BttPlayerComponent pc) {
        if (pc.guardedTicks <= 0) return;
        if (--pc.guardedTicks > 0) return;
        clearGuard(pc);
    }

    // ===== 死亡联动（kill hook） =====

    /** 保镖死亡 → 带走被守护者（先清其守护态，否则会被自己的免死否决）；宿主死亡 → 寄生者恢复可被杀 */
    public static void onDeath(ServerPlayerEntity victim, ServerWorld world, GameWorldComponent gwc) {
        if (gwc.isRole(victim, BttRoles.BODYGUARD)) {
            BttPlayerComponent bc = BttPlayerComponent.KEY.get(victim);
            ServerPlayerEntity guarded = resolve(world, bc.guardTarget);
            bc.guardTarget = "";
            bc.sync();
            if (guarded != null && GameFunctions.isPlayerAliveAndSurvival(guarded)) {
                BttPlayerComponent gc = BttPlayerComponent.KEY.get(guarded);
                clearGuard(gc);
                GameFunctions.killPlayer(guarded, true, victim, GameConstants.DeathReasons.GENERIC);
            }
        }
        // 宿主死亡 → 寄生者恢复可被杀
        for (ServerPlayerEntity p : world.getPlayers()) {
            BttPlayerComponent c = BttPlayerComponent.KEY.get(p);
            if (victim.getUuid().toString().equals(c.parasiteHost)) {
                c.parasiteHost = "";
                c.sync();
            }
        }
    }

    private static void clearGuard(BttPlayerComponent pc) {
        pc.guardedBy = "";
        pc.guardedTicks = 0;
        pc.sync();
    }

    private static ServerPlayerEntity resolve(ServerWorld world, String uuid) {
        if (uuid == null || uuid.isEmpty()) return null;
        try {
            return world.getPlayerByUuid(UUID.fromString(uuid)) instanceof ServerPlayerEntity s ? s : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
