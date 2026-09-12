package org.agmas.noellesroles.voice;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.SoundPacketEvent;
import de.maxhenkel.voicechat.api.packets.Packet;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.GameMode;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.btt.BttPlayerComponent;

public class NoellesrolesVoiceChatPlugin implements VoicechatPlugin {
    @Override
    public String getPluginId() {
        return Noellesroles.MOD_ID;
    }

    /** 服务端语音 API（C-109 胃袋分组需要在事件外拿到它） */
    private static VoicechatServerApi serverApi;
    /** 饕餮 uuid → 其"胃袋"分组 */
    private static final java.util.Map<java.util.UUID, de.maxhenkel.voicechat.api.Group> STOMACH_GROUPS = new java.util.HashMap<>();

    @Override
    public void initialize(VoicechatApi api) {
        if (api instanceof VoicechatServerApi server) serverApi = server;
        VoicechatPlugin.super.initialize(api);
    }

    // ===== 饕餮 <绑架> 胃袋分组（C-109）=====

    /**
     * 把被吞者放进饕餮的"胃袋"分组（照 NRS `addToStomachGroup` 口径）：组内互通、**外部听不见被吞者**；
     * 饕餮本人**不进组**（保持与外界正常通话）。被吞者逐 tick 被传送到饕餮身上，故其"听觉位置"也在饕餮处
     * ——即 {@code Type.NORMAL} 组下他们仍能听到饕餮周围的动静（自带"附身"效果）。
     */
    public static void joinStomach(ServerPlayerEntity kidnapper, ServerPlayerEntity victim) {
        if (serverApi == null) return;
        de.maxhenkel.voicechat.api.Group group = STOMACH_GROUPS.get(kidnapper.getUuid());
        // 自愈：voicechat 会在组内无人时自动删组（NRS 用 RemoveGroupEvent 清理，这里直接校验存在性）
        if (group != null && serverApi.getGroup(group.getId()) == null) {
            STOMACH_GROUPS.remove(kidnapper.getUuid());
            group = null;
        }
        if (group == null) {
            String name = kidnapper.getName().getString();
            if (name.length() > 12) name = name.substring(0, 12); // 组名上限 16
            group = serverApi.groupBuilder()
                    .setPersistent(false)
                    .setHidden(true)
                    .setType(de.maxhenkel.voicechat.api.Group.Type.NORMAL)
                    .setName("btt-" + name)
                    .build();
            STOMACH_GROUPS.put(kidnapper.getUuid(), group);
        }
        VoicechatConnection connection = serverApi.getConnectionOf(victim.getUuid());
        if (connection != null) connection.setGroup(group);
    }

    /** 出腹：回到普通近距离语音 */
    public static void leaveStomach(ServerPlayerEntity victim) {
        if (serverApi == null) return;
        VoicechatConnection connection = serverApi.getConnectionOf(victim.getUuid());
        if (connection != null) connection.setGroup(null);
    }

    public void paranoidEvent(MicrophonePacketEvent event) {
        VoicechatServerApi api = event.getVoicechat();
        ServerPlayerEntity spectator = ((ServerPlayerEntity)event.getSenderConnection().getPlayer().getPlayer());
        GameWorldComponent gameWorldComponent = (GameWorldComponent) GameWorldComponent.KEY.get(spectator.getWorld());
        if (spectator.interactionManager.getGameMode().equals(GameMode.SPECTATOR)) {
            spectator.getWorld().getPlayers().forEach((p) -> {
                if (gameWorldComponent.isRole(p, Noellesroles.THE_INSANE_DAMNED_PARANOID_KILLER_OF_DOOM_DEATH_DESTRUCTION_AND_WAFFLES) && GameFunctions.isPlayerAliveAndSurvival(p)) {
                    if (spectator.distanceTo(p) <= api.getVoiceChatDistance()) {
                        VoicechatConnection con = api.getConnectionOf(p.getUuid());
                        api.sendLocationalSoundPacketTo(con, event.getPacket().locationalSoundPacketBuilder()
                                        .position(api.createPosition(p.getX(), p.getY(), p.getZ()))
                                        .distance((float)api.getVoiceChatDistance())
                                        .build());
                    }
                }
            });
        }
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::paranoidEvent);
        registration.registerEvent(MicrophonePacketEvent.class, this::broadcastEvent);
        registration.registerEvent(MicrophonePacketEvent.class, this::walkieTalkieEvent);
        registration.registerEvent(MicrophonePacketEvent.class, this::mutedSpeakerEvent);
        registration.registerEvent(EntitySoundPacketEvent.class, this::mutedListenerEvent);
        registration.registerEvent(LocationalSoundPacketEvent.class, this::mutedListenerEvent);
        VoicechatPlugin.super.registerEvents(registration);
    }

    // ===== 虐待狂 <缄默>（C-086）：哑 + 聋（移植 NRS Silencer/SilencedPlayerComponent 的语音口径）=====

    /**
     * 对讲机（C-130，docx：「呼叫需要拿出对讲机，而收听不需要」；技法同 WatheSpark）：
     * 说话者**主/副手持**对讲机 → 该段语音**额外**发给所有**物品栏持有**对讲机的存活玩家
     * （位置取**接收者自身**、距离 8），因此不受常规近距离限制；近处玩家已有原版近距离投递 → 跳过以免双份。
     */
    public void walkieTalkieEvent(MicrophonePacketEvent event) {
        ServerPlayerEntity speaker = serverPlayerOf(event.getSenderConnection());
        if (speaker == null) return;
        if (!BttIdentity.isBttMode(speaker.getWorld())) return;
        if (!GameFunctions.isPlayerAliveAndSurvival(speaker)) return;
        if (!org.agmas.noellesroles.item.WalkieTalkieItem.isHeld(speaker)) return;
        VoicechatServerApi api = event.getVoicechat();
        double near = api.getVoiceChatDistance();
        for (ServerPlayerEntity listener : speaker.getServerWorld().getPlayers()) {
            if (listener == speaker) continue;
            if (!GameFunctions.isPlayerAliveAndSurvival(listener)) continue;
            if (!org.agmas.noellesroles.item.WalkieTalkieItem.isCarried(listener)) continue;
            if (speaker.squaredDistanceTo(listener) <= near * near) continue; // 已在原版近距投递范围内
            VoicechatConnection con = api.getConnectionOf(listener.getUuid());
            if (con == null) continue;
            api.sendLocationalSoundPacketTo(con, event.getPacket().locationalSoundPacketBuilder()
                    .position(api.createPosition(listener.getX(), listener.getY(), listener.getZ()))
                    .distance(8f)
                    .build());
        }
    }

    /**
     * 乘务员 &lt;广播&gt;（C-123）：开着广播的乘务员开口 → 语音包**额外**中继给全车存活玩家
     * （复用 {@link #paranoidEvent} 同款 {@code sendLocationalSoundPacketTo}；近距离原路径不受影响）。
     */
    public void broadcastEvent(MicrophonePacketEvent event) {
        ServerPlayerEntity speaker = serverPlayerOf(event.getSenderConnection());
        if (speaker == null) return;
        if (!org.agmas.noellesroles.btt.BttBroadcast.isBroadcasting(speaker)) return;
        VoicechatServerApi api = event.getVoicechat();
        for (ServerPlayerEntity listener : speaker.getServerWorld().getPlayers()) {
            if (listener == speaker) continue;
            if (!GameFunctions.isPlayerAliveAndSurvival(listener)) continue;
            VoicechatConnection con = api.getConnectionOf(listener.getUuid());
            if (con == null) continue;
            api.sendLocationalSoundPacketTo(con, event.getPacket().locationalSoundPacketBuilder()
                    .position(api.createPosition(speaker.getX(), speaker.getY(), speaker.getZ()))
                    .distance((float) api.getVoiceChatDistance())
                    .build());
        }
    }

    /** 哑：被缄默者说不了（NRS：取消其 MicrophonePacket） */
    public void mutedSpeakerEvent(MicrophonePacketEvent event) {
        ServerPlayerEntity speaker = serverPlayerOf(event.getSenderConnection());
        if (speaker == null) return;
        if (!BttIdentity.isBttMode(speaker.getWorld())) return;
        if (BttPlayerComponent.KEY.get(speaker).isMuted()) event.cancel();
    }

    /** 聋：被缄默者听不到别人（NRS：取消发往其连接的声音包） */
    public <T extends Packet> void mutedListenerEvent(SoundPacketEvent<T> event) {
        ServerPlayerEntity recipient = serverPlayerOf(event.getReceiverConnection());
        if (recipient == null) return;
        if (!BttIdentity.isBttMode(recipient.getWorld())) return;
        if (BttPlayerComponent.KEY.get(recipient).isMuted()) event.cancel();
    }

    private static ServerPlayerEntity serverPlayerOf(VoicechatConnection connection) {
        if (connection == null || connection.getPlayer() == null) return null;
        return connection.getPlayer().getPlayer() instanceof ServerPlayerEntity player ? player : null;
    }
}
