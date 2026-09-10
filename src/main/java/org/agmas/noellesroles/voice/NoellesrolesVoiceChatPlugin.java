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

    @Override
    public void initialize(VoicechatApi api) {
        VoicechatPlugin.super.initialize(api);
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
        registration.registerEvent(MicrophonePacketEvent.class, this::mutedSpeakerEvent);
        registration.registerEvent(EntitySoundPacketEvent.class, this::mutedListenerEvent);
        registration.registerEvent(LocationalSoundPacketEvent.class, this::mutedListenerEvent);
        VoicechatPlugin.super.registerEvents(registration);
    }

    // ===== 虐待狂 <缄默>（C-086）：哑 + 聋（移植 NRS Silencer/SilencedPlayerComponent 的语音口径）=====

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
