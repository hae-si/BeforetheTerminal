package org.agmas.noellesroles.btt;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * 乘务员 &lt;广播&gt;（docx 2026-09-12，C-123）：随时切换「正常讲话 ⇄ 全车广播」。
 * <ul>
 *   <li>G 键切换（无目标、无冷却；{@link BttGuessReceiver} 直发分支）。</li>
 *   <li>开启后：乘务员开口时，语音包**额外**中继给全车存活玩家
 *       （{@code NoellesrolesVoiceChatPlugin#broadcastEvent}，复用 NRS「偏执杀人狂」同款
 *       {@code sendLocationalSoundPacketTo} 中继；近距离原路径不受影响）。</li>
 *   <li>关掉即回到普通近距离语音；死亡/新一局自动失效（组件 reset）。</li>
 * </ul>
 */
public final class BttBroadcast {
    private BttBroadcast() {}

    /** G 键切换广播开关 */
    public static void toggle(ServerPlayerEntity attendant) {
        BttPlayerComponent pc = BttPlayerComponent.KEY.get(attendant);
        pc.broadcastOn = !pc.broadcastOn;
        pc.sync();
        // C-128：广播开/关音
        attendant.getServerWorld().playSound(null, attendant.getBlockPos(),
                pc.broadcastOn ? BttSounds.BROADCAST_ON : BttSounds.BROADCAST_OFF,
                net.minecraft.sound.SoundCategory.PLAYERS, 1.0F, 1.0F);
        attendant.sendMessage(Text.translatable(pc.broadcastOn
                        ? "noellesroles.btt.action.attendant.broadcast_on"
                        : "noellesroles.btt.action.attendant.broadcast_off")
                .withColor(BttRoles.ATTENDANT.color()), true);
    }

    /** 该玩家此刻是否处于"全车广播"状态（语音中继判定；死亡即失效） */
    public static boolean isBroadcasting(ServerPlayerEntity player) {
        if (!BttIdentity.isBttMode(player.getWorld())) return false;
        GameWorldComponent gwc = GameWorldComponent.KEY.get(player.getWorld());
        if (!gwc.isRunning()) return false;
        if (gwc.getRole(player) != BttRoles.ATTENDANT
                && !BttRoles.isPlayingAs(gwc, player, BttRoles.ATTENDANT)) return false;
        if (!GameFunctions.isPlayerAliveAndSurvival(player)) return false;
        return BttPlayerComponent.KEY.get(player).broadcastOn;
    }
}
