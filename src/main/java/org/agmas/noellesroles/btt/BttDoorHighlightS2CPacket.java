package org.agmas.noellesroles.btt;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.agmas.noellesroles.Noellesroles;

/**
 * C-083 建筑师 &lt;感知&gt; 下行：门被撬/被卡的位置，客户端 5 秒描边（移植 NRS EngineerDoorHighlightS2CPacket）。
 * 只发给建筑师；{@code blasted} 决定描边配色（被撬=红 / 被卡=金）。
 */
public record BttDoorHighlightS2CPacket(BlockPos pos, boolean blasted) implements CustomPayload {
    public static final Id<BttDoorHighlightS2CPacket> ID =
            new Id<>(Identifier.of(Noellesroles.MOD_ID, "btt_door_highlight"));
    public static final PacketCodec<RegistryByteBuf, BttDoorHighlightS2CPacket> CODEC = PacketCodec.of(
            BttDoorHighlightS2CPacket::write, BttDoorHighlightS2CPacket::read);

    private static void write(BttDoorHighlightS2CPacket payload, PacketByteBuf buf) {
        buf.writeBlockPos(payload.pos);
        buf.writeBoolean(payload.blasted);
    }

    private static BttDoorHighlightS2CPacket read(PacketByteBuf buf) {
        return new BttDoorHighlightS2CPacket(buf.readBlockPos(), buf.readBoolean());
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
