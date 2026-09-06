package org.agmas.noellesroles.btt;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * 失忆患者尸体交互上行（G 键 + 自带尸体射线，2026-09-06：不再依赖 NR targetBody——
 * CoronerHud 射线在暗处被 wathe renderHud 早退跳过且活人距离仅 2 格）。
 */
public record BttCorpseActionC2SPacket(UUID body, int action) implements CustomPayload {
    public static final Identifier ID_RAW = Identifier.of(BttGameModes.BEFORE_THE_TERMINAL.ID.getNamespace(), "corpse_action");
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
