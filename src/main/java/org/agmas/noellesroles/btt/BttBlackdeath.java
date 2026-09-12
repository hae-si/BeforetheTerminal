package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.UUID;

/**
 * 黑死病附身链（C-134，docx 2026-09-12 形态重写）。
 *
 * <p>docx 规格：初始**病人形态**；被以任何方式杀死后 → **病原体形态附身击杀者**，击杀者成为病人
 * （若病人被杀死则感染击杀者）；病原体形态处于**凶手对讲机频道**；**只有病人跳车或精神崩溃，黑死病才会终结**；
 * 黑死病在场时只有包括其他凶手在内所有人都死亡，凶手阵营才会胜利。
 *
 * <p>实现口径（作者 2026-09-13 澄清 + docx 字面）：
 * <ul>
 *   <li><b>理智</b>：黑死病本人 = **凶手理智**（{@code MoodType.FAKE}：无理智/需求、红旗）；
 *       "精神崩溃"只可能发生在**被附身的病人**身上（病人按自己的身份跑理智——乘客病人会耗尽理智）。</li>
 *   <li><b>病原体形态</b> = 黑死病玩家变为旁观态（无躯体、不可被枪杀），**仍在凶手对讲机频道**
 *       （语音中继对"无体病原体"豁免存活判定，见 {@code NoellesrolesVoiceChatPlugin}）。</li>
 *   <li><b>转移</b>：病人被"有击杀者"地杀死 → 击杀者成为新病人（链式传染）；病人**无击杀者**死亡
 *       （跳车/环境/崩溃）→ 黑死病**终结**（病原体消散，此后按"黑死病已死"参与结算）。</li>
 *   <li><b>在场</b>：{@link #isPresent} —— 本体存活，或病原体附身于存活病人。结算里黑死病按在场计入凶手侧
 *       （{@code onlyBlackdeathLeft} 门槛不变）。</li>
 * </ul>
 */
public final class BttBlackdeath {
    private BttBlackdeath() {}

    /** 开局：黑死病本人 = 初始病人形态 */
    public static void onRoundStart(ServerWorld world, java.util.List<ServerPlayerEntity> players) {
        GameWorldComponent gwc = GameWorldComponent.KEY.get(world);
        for (ServerPlayerEntity p : players) {
            BttPlayerComponent c = BttPlayerComponent.KEY.get(p);
            boolean isBlackdeath = gwc.isRole(p, BttRoles.BLACKDEATH);
            c.blackdeathPatient = isBlackdeath;
            c.blackdeathHost = "";
        }
    }

    /** 该玩家是否是**无体病原体**（黑死病已死、病原体在别人身上，本人为旁观态） */
    public static boolean isBodylessPathogen(PlayerEntity player) {
        if (player == null) return false;
        BttPlayerComponent c = BttPlayerComponent.KEY.get(player);
        if (c.blackdeathHost.isEmpty()) return false;
        return GameWorldComponent.KEY.get(player.getWorld()).isRole(player, BttRoles.BLACKDEATH);
    }

    /** 该黑死病玩家是否**在场**（本体存活，或病原体附身于存活病人） */
    public static boolean isPresent(ServerWorld world, ServerPlayerEntity blackdeath) {
        BttPlayerComponent c = BttPlayerComponent.KEY.get(blackdeath);
        if (c.blackdeathHost.isEmpty()) {
            return GameFunctions.isPlayerAliveAndSurvival(blackdeath); // 本体即病人
        }
        ServerPlayerEntity host = hostOf(world, c.blackdeathHost);
        return host != null && GameFunctions.isPlayerAliveAndSurvival(host);
    }

    /** 拥有该病人（宿主）的黑死病玩家；无人认领 → null */
    public static ServerPlayerEntity ownerOf(ServerWorld world, ServerPlayerEntity patient) {
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p == patient) {
                // 本体形态：病人就是黑死病自己
                if (GameWorldComponent.KEY.get(world).isRole(p, BttRoles.BLACKDEATH)
                        && BttPlayerComponent.KEY.get(p).blackdeathPatient) return p;
                continue;
            }
            String host = BttPlayerComponent.KEY.get(p).blackdeathHost;
            if (!host.isEmpty() && host.equals(patient.getUuidAsString())) return p;
        }
        return null;
    }

    /**
     * 病人被击杀（由 {@code BttKillHookMixin} 在 killPlayer HEAD 调用）：
     * ① 有玩家击杀者 → 病原体转移到击杀者（击杀者成为病人），返回 true 表示"链式继续"；
     * ② 无击杀者（跳车/环境/崩溃）→ 终结黑死病，返回 false。
     * 病人本体照常死亡（不 cancel）——被附身的躯体只是承载物。
     */
    public static boolean onPatientDeath(ServerPlayerEntity patient, PlayerEntity killer) {
        ServerWorld world = patient.getServerWorld();
        ServerPlayerEntity blackdeath = ownerOf(world, patient);
        if (blackdeath == null) return false;
        BttPlayerComponent bdState = BttPlayerComponent.KEY.get(blackdeath);
        if (killer instanceof ServerPlayerEntity newHost && newHost != patient
                && GameFunctions.isPlayerAliveAndSurvival(newHost)) {
            // 转移：病人标记搬家，病原体（黑死病玩家）保持旁观态并记下新宿主
            BttPlayerComponent.KEY.get(patient).blackdeathPatient = false;
            BttPlayerComponent.KEY.get(newHost).blackdeathPatient = true;
            bdState.blackdeathHost = newHost.getUuidAsString();
            bdState.sync();
            BttPlayerComponent.KEY.get(newHost).sync();
            // 附带：病原体不再渲染躯体（黑死病本体死亡走 wathe 原生 → 旁观）
            blackdeath.setCameraEntity(newHost); // 病原体"附身"= 用病人的眼睛看（沿用 C-109 饕餮同款原版 API）
            notify(blackdeath, "noellesroles.btt.action.blackdeath.possess", nameOf(newHost));
            notify(newHost, "noellesroles.btt.action.blackdeath.patient", nameOf(patient));
            return true;
        }
        // 无击杀者 → 终结
        BttPlayerComponent.KEY.get(patient).blackdeathPatient = false;
        BttPlayerComponent.KEY.get(patient).sync();
        terminate(blackdeath);
        return false;
    }

    /** 黑死病终结：病原体消散（宿主无人认领 / 病人跳车 / 精神崩溃） */
    public static void terminate(ServerPlayerEntity blackdeath) {
        BttPlayerComponent c = BttPlayerComponent.KEY.get(blackdeath);
        if (c.blackdeathHost.isEmpty() && !c.blackdeathPatient) return; // 本来就已终结
        c.blackdeathHost = "";
        c.blackdeathPatient = false;
        c.sync();
        notify(blackdeath, "noellesroles.btt.action.blackdeath.ended", null);
    }

    /** 每 tick（GameMode 循环）：①病原体相机跟随宿主；②病人理智归零 → 精神崩溃 */
    public static void tick(ServerWorld world) {
        GameWorldComponent gwc = GameWorldComponent.KEY.get(world);
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (!gwc.isRole(p, BttRoles.BLACKDEATH)) continue;
            BttPlayerComponent c = BttPlayerComponent.KEY.get(p);
            if (c.blackdeathHost.isEmpty()) continue;
            ServerPlayerEntity host = hostOf(world, c.blackdeathHost);
            if (host == null) continue;
            if (!GameFunctions.isPlayerAliveAndSurvival(host)) continue; // 死亡路径由击杀钩子处理
            if (p.getCameraEntity() != host) p.setCameraEntity(host);
            // 精神崩溃：病人（按自己的身份跑理智；只有 REAL 身份会掉理智）理智归零 → 崩溃死亡 → 黑死病终结
            if (PlayerMoodComponent.KEY.get(host).getMood() <= 0f) {
                GameFunctions.killPlayer(host, true, null, BttDeathReasons.COLLAPSE);
            }
        }
    }

    private static ServerPlayerEntity hostOf(ServerWorld world, String uuid) {
        try {
            return world.getPlayerByUuid(UUID.fromString(uuid)) instanceof ServerPlayerEntity p ? p : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void notify(ServerPlayerEntity player, String key, String arg) {
        if (player == null) return;
        net.minecraft.text.MutableText text = arg == null
                ? Text.translatable(key) : Text.translatable(key, arg);
        player.sendMessage(text.withColor(BttRoles.BLACKDEATH.color()), true);
    }

    private static String nameOf(ServerPlayerEntity p) {
        return p == null ? "？" : p.getGameProfile().getName();
    }
}
