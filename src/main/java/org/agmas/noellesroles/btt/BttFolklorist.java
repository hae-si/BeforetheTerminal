package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Set;

/**
 * 民俗学家 Folklorist（C-131）：docx「每当有人使用**描述中带有「任何人」的能力**，
 * 则能透视到他十秒，但不知道使用的能力」。
 * <p>
 * 口径：以下身份的 docx 描述含「任何人／任意两个人」，且其技能走 BTT 上行包 → 使用者会被民俗学家透视 10 秒
 * （**只揭示使用者**，不揭示用了什么能力）：
 * <ul>
 *   <li>侦探 detective（调查任何人）</li>
 *   <li>猎人 hunter（狙击任何人）</li>
 *   <li>救世主 messiah（预知任何人）</li>
 *   <li>小说家 novelist（猜测任何活人或死人）</li>
 *   <li>记者 journalist（选择任何人跟踪）</li>
 *   <li>魔术师 magician（交换任意两个人）</li>
 *   <li>绳艺师 rigger（拘束任意两个人）</li>
 * </ul>
 * **未覆盖**（技能不在 BTT 上行包内，登记在 ROADMAP.BT-FOLKLORIST-GAP）：女巫 voodoo（诅咒任何人，NR 原生技能包）；
 * 预言家 prophet（描述为「某位玩家」、无「任何人」字样 → 不计入）。
 */
public final class BttFolklorist {
    private BttFolklorist() {}

    /** 透视时长（docx：十秒） */
    private static final int REVEAL_TICKS = 200;

    /** 「描述中带有『任何人』」的技能使用者（见类注释） */
    private static final Set<Role> ANYONE_USERS = Set.of(
            BttRoles.DETECTIVE, BttRoles.HUNTER, BttRoles.MESSIAH, BttRoles.NOVELIST,
            BttRoles.JOURNALIST, BttRoles.MAGICIAN, BttRoles.RIGGER,
            // C-140：女巫诅咒走 NR 原生上行包（不经 BttGuessReceiver），在 NR 接收器里补调本方法
            BttRoles.WITCH);

    /** 有人使用「任何人」类能力时调用（BttGuessReceiver 的上行包入口） */
    public static void onAnyoneAbility(ServerPlayerEntity user, GameWorldComponent gwc) {
        Role role = BttRoles.effectiveRole(gwc, user);
        if (role == null || !ANYONE_USERS.contains(role)) return;
        for (ServerPlayerEntity p : user.getServerWorld().getPlayers()) {
            if (p == user) continue;
            if (!GameFunctions.isPlayerAliveAndSurvival(p)) continue;
            if (!gwc.isRole(p, BttRoles.FOLKLORIST)) continue;
            BttPlayerComponent pc = BttPlayerComponent.KEY.get(p);
            pc.folkTarget = user.getUuid().toString();
            pc.folkTicks = REVEAL_TICKS;
            pc.sync();
        }
    }

    /** 每 tick（BttEvents 全局循环）：倒计时归零即清目标 */
    public static void tick(ServerPlayerEntity player, BttPlayerComponent pc) {
        if (pc.folkTicks <= 0) return;
        if (--pc.folkTicks > 0) return;
        pc.folkTarget = "";
        pc.sync();
    }
}
