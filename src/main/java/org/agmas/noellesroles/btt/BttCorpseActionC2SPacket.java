package org.agmas.noellesroles.btt;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * 尸体交互上行（复用 NR 秃鹫交互基建：G 键 + 注视尸体 targetBody）：
 * action 0=窃贼<搜刮>、1=失忆患者<取遗物>。
 */
public record BttCorpseActionC2SPacket(UUID body, int action) implements CustomPayload {
    public static final Identifier ID_RAW = Identifier.of(BttGameModes.BEFORE_THE_TERMINAL.ID.getNamespace(), "btt_corpse_action");
    public static final Id<BttCorpseActionC2SPacket> ID = new Id<>(ID_RAW);
    public static final PacketCodec<RegistryByteBuf, BttCorpseActionC2SPacket> CODEC = PacketCodec.of(
            BttCorpseActionC2SPacket::write, BttCorpseActionC2SPacket::read);

    private static void write(BttCorpseActionC2SPacket p, PacketByteBuf buf) {
        buf.writeUuid(p.body);
        buf.writeVarInt(p.action);
    }

    private static BttCorpseActionC2SPacket read(PacketByteBuf buf) {
        return new BttCorpseActionC2SPacket(buf.readUuid(), buf.readVarInt());
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
