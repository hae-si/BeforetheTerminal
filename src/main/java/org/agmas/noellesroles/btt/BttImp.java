package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.agmas.noellesroles.AbilityPlayerComponent;

import java.util.UUID;

/**
 * 小恶魔 &lt;印记&gt;（C-111，用户 2026-09-11 新增，取代被删除的冒牌货）：
 * 「拥有[刀]。&lt;印记&gt;一个人，如果他是独行中立或外人中立，你死亡后，他成为小恶魔（仍能使用印记），更换选择冷却一分钟。」
 * <ul>
 *   <li>E 屏选人（任意人）→ 记下 {@link BttPlayerComponent#impMark}；**更换选择 CD 1 分钟**；</li>
 *   <li>印记**本身不看身份**（谁都能印，印错就白印——由玩家承担风险）；</li>
 *   <li>小恶魔死亡时（{@code BttEvents} 的 AllowPlayerDeath 监听，注册在最后 = 其它免死都放行后才结算）：
 *       若印记目标**存活**且其身份是**独行中立**（`BttRoles.LONE_NEUTRALS`）或**外人中立**（`Faction.OUTSIDER`）
 *       → {@link BttSecondIdentity#takeOver} 让目标**真变**成小恶魔（连阵营一起，含 [刀] 与印记技能），
 *       并把新小恶魔的印记清空（重新选择）；</li>
 *   <li>印记落空（目标非中立/已死/离线）→ 无事发生。</li>
 * </ul>
 */
public final class BttImp {
    private BttImp() {}

    /** 更换印记的冷却（「更换选择冷却一分钟」）；同时作为初始冷却（C-099） */
    public static final int COOLDOWN_TICKS = GameConstants.getInTicks(1, 0);

    /** E 屏 &lt;印记&gt;：给准星所指玩家留下印记（可更换，CD 1 分钟） */
    public static void mark(ServerPlayerEntity user, ServerPlayerEntity target) {
        AbilityPlayerComponent ability = AbilityPlayerComponent.KEY.get(user);
        if (ability.cooldown > 0) return;
        ability.cooldown = COOLDOWN_TICKS;
        ability.sync();
        BttPlayerComponent.KEY.get(user).impMark = target.getUuidAsString();
        // C-113：**不**告诉使用者印记是否"合得上"（用户文本原则：设计上不应让使用者知道结果）
        user.sendMessage(Text.translatable("noellesroles.btt.action.imp.marked", target.getName().getString())
                .withColor(BttRoles.IMP.color()), true);
    }

    /** 可继承者：独行中立（小说家/小丑/窃贼/纵火犯）或外人中立（魔女/救世主/饕餮/花匠） */
    public static boolean isLoneOrOutsider(Role role) {
        return role != null && (BttRoles.LONE_NEUTRALS.contains(role)
                || BttRoles.factionOf(role) == BttRoles.Faction.OUTSIDER);
    }

    /** 小恶魔死亡 → 印记目标（若合得上且存活）继位；由 AllowPlayerDeath 监听在最后调用 */
    public static void succession(ServerPlayerEntity deadImp) {
        BttPlayerComponent pc = BttPlayerComponent.KEY.get(deadImp);
        if (pc.impMark.isEmpty()) return;
        ServerPlayerEntity successor;
        try {
            successor = deadImp.getServerWorld().getPlayerByUuid(UUID.fromString(pc.impMark)) instanceof ServerPlayerEntity s ? s : null;
        } catch (IllegalArgumentException e) {
            successor = null;
        }
        if (successor == null || !GameFunctions.isPlayerAliveAndSurvival(successor)) return;
        Role role = GameWorldComponent.KEY.get(deadImp.getWorld()).getRole(successor);
        if (!isLoneOrOutsider(role)) return; // 印记落空
        BttSecondIdentity.takeOver(successor, BttRoles.IMP); // 真替换：成为小恶魔（含 [刀] 与印记技能）
        BttPlayerComponent.KEY.get(successor).impMark = "";  // 新小恶魔从零开始印记
        successor.sendMessage(Text.translatable("noellesroles.btt.action.imp.succession")
                .withColor(BttRoles.IMP.color()), true);
    }
}