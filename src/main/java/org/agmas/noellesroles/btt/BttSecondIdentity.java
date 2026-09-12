package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.AnnounceWelcomePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * 第二身份核心（C-110）：照 StupidExpress 的做法（`role/amnesiac/RoleSelectionHandler`）——
 * **`GameWorldComponent.addRole(player, role)` + `ModdedRoleAssigned.EVENT`** 即"真的变成另一个身份"，
 * 于是 BTT 既有的按身份分派（`BttRoleDefs` 的 kit、`BttGuessReceiver` 的技能键、客户端按角色选 UI）**自动跟随**，
 * 无需为每个身份写第二套逻辑。
 * <p>
 * 两条路线：
 * <ul>
 *   <li>{@link #takeOver} = **真替换**（连阵营一起变）：失忆患者（docx「得到身份和阵营」）、哲人（得能力）。</li>
 *   <li>{@link #borrow} = **只借技能**（阵营不变）：食人族（从平民尸体"暂时习得技能"）、前任叛徒/前任卧底
 *       （"和一位随机的不在场身份无异"，但阵营仍是原阵营）。技能判定统一走 {@code BttRoles.isPlayingAs}，
 *       客户端 `effectiveRole` 会把它当作当前角色来开技能 UI。</li>
 * </ul>
 * 范围口径（docx §24）：**技能 = 主动（技能键）**；被动天赋/免死等仍按自身身份判定，不随借用变化。
 */
public final class BttSecondIdentity {
    private BttSecondIdentity() {}

    /**
     * 真·身份替换：改角色 + 派发 HML 身份事件（各模组重新给 kit）+ 补发本角色 kit + 重新播报身份。
     * 「仅限一次」由角色改变本身保证（原来那个身份已经不在 `getRole` 里了）。
     */
    public static void takeOver(ServerPlayerEntity player, Role role) {
        if (role == null) return;
        GameWorldComponent gwc = GameWorldComponent.KEY.get(player.getWorld());
        gwc.addRole(player, role);
        gwc.sync();
        org.agmas.harpymodloader.events.ModdedRoleAssigned.EVENT.invoker().assignModdedRole(player, role);
        BttRoleDef def = BttRoleDefs.get(role);
        if (def != null) def.dispatchKit(player);
        reannounce(player, role);
    }

    /**
     * 借技能（不改阵营）：记 {@code borrowedRole} 供技能分派使用；{@code ticks <= 0} 表示永久到本局结束。
     * **不发道具**（食人族只"习得技能"，物品仍属原主；前任系的道具由调用方另行继承）。
     */
    public static void borrow(ServerPlayerEntity player, Role role, int ticks) {
        if (role == null) return;
        BttPlayerComponent.KEY.get(player).setBorrowedRole(role.identifier().toString(), Math.max(0, ticks));
    }

    /** 每 tick（BttEvents 全局循环）：借来的技能到期归还 */
    public static void tick(ServerPlayerEntity player, BttPlayerComponent pc) {
        if (pc.borrowedTicks <= 0) return; // 0/负数 = 永久
        if (--pc.borrowedTicks <= 0) pc.clearBorrowedRole();
    }

    /** 重新播报身份（迎新覆盖层）：复用 GameMode 的公告索引口径 */
    private static void reannounce(ServerPlayerEntity player, Role role) {
        GameWorldComponent gwc = GameWorldComponent.KEY.get(player.getWorld());
        int killers = 0;
        int passengers = 0;
        for (Role r : gwc.getRoles().values()) {
            if (r.canUseKiller()) killers++;
            else if (r.isInnocent()) passengers++;
        }
        ServerPlayNetworking.send(player, new AnnounceWelcomePayload(
                BeforeTheTerminalGameMode.announcementIndex(role), killers, passengers));
    }

    /** 提示（身份色由调用方决定；这里统一用新身份色） */
    public static Text tookOverText(Role role) {
        return Text.translatable("noellesroles.btt.action.second.took_over",
                BttIdentity.displayName(role).getString()).withColor(role.color());
    }
}