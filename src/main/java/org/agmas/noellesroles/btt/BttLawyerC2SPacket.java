package org.agmas.noellesroles.btt;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 律师 &lt;起诉&gt; 上行（C-103）：一次提交**全部**指名（点几次凑齐后单包发出，照 swapper 的两段式口径）。
 * 服务端在 {@link BttGuessReceiver} 校验「指名集合 == 场上存活凶手席位集合」，正确则全部死亡。
 */
public record BttLawyerC2SPacket(List<UUID> picks) implements CustomPayload {
    public static final Identifier ID_RAW = Identifier.of(BttGameModes.BEFORE_THE_TERMINAL.ID.getNamespace(), "lawyer");
    public static final Id<BttLawyerC2SPacket> ID = new Id<>(ID_RAW);
    public static final PacketCodec<RegistryByteBuf, BttLawyerC2SPacket> CODEC = PacketCodec.of(
            BttLawyerC2SPacket::write, BttLawyerC2SPacket::read);

    private static void write(BttLawyerC2SPacket p, PacketByteBuf buf) {
        buf.writeVarInt(p.picks.size());
        for (UUID uuid : p.picks) buf.writeUuid(uuid);
    }

    private static BttLawyerC2SPacket read(PacketByteBuf buf) {
        int n = buf.readVarInt();
        List<UUID> picks = new ArrayList<>(n);
        for (int i = 0; i < n; i++) picks.add(buf.readUuid());
        return new BttLawyerC2SPacket(picks);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}