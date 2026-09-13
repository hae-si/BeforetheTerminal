package org.agmas.noellesroles.client.voice;

import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.agmas.noellesroles.btt.BttIdentity;
import org.agmas.noellesroles.btt.BttPlayerComponent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 笑匠 &lt;变声&gt; 的**接收端**实现（C-139，仅 client sourceSet）。
 * <p>
 * 挂在 simple-voice-chat 的 `ClientReceiveSoundEvent.EntitySound`（解码后、空间化前）：
 * 按说话人 UUID 找玩家 → 读 {@link BttPlayerComponent#voicePitchTicks} → 变调 → 写回 PCM。
 * 因为变调发生在**听者**一侧，所以被变声者本人听自己的声音是正常的（承受者不觉察）。
 * <p>
 * 由公共 sourceSet 的 {@code NoellesrolesVoiceChatPlugin} 通过**反射**注册（服务端没有这些 client 类）。
 */
public final class BttVoiceClientReceiver {
    /** 变调倍率（氦气音）与收尾渐变时长 */
    private static final float RATIO = 1.35f;
    private static final float RAMP_OUT_TICKS = 10f;

    private static final Map<UUID, BttVoiceShifter> SHIFTERS = new ConcurrentHashMap<>();

    private BttVoiceClientReceiver() {}

    public static void register(EventRegistration registration) {
        registration.registerEvent(ClientReceiveSoundEvent.EntitySound.class, BttVoiceClientReceiver::onEntitySound);
    }

    private static void onEntitySound(ClientReceiveSoundEvent.EntitySound event) {
        if (event.isCancelled()) return;
        short[] pcm = event.getRawAudio();
        if (pcm == null || pcm.length == 0) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || !BttIdentity.isBttMode(client.world)) return;
        UUID speaker = event.getEntityId();
        if (speaker == null) return;
        PlayerEntity player = client.world.getPlayerByUuid(speaker);
        if (player == null) {
            SHIFTERS.remove(speaker);
            return;
        }
        BttPlayerComponent component = BttPlayerComponent.KEY.get(player);
        if (component.voicePitchTicks <= 0) {
            SHIFTERS.remove(speaker);
            return;
        }
        float ratio = RATIO;
        if (component.voicePitchTicks < RAMP_OUT_TICKS) { // 收尾渐变，避免突然变回原声
            ratio = 1.0f + (RATIO - 1.0f) * (component.voicePitchTicks / RAMP_OUT_TICKS);
        }
        BttVoiceShifter shifter = SHIFTERS.computeIfAbsent(speaker, id -> new BttVoiceShifter());
        event.setRawAudio(shifter.process(pcm, ratio));
    }
}
