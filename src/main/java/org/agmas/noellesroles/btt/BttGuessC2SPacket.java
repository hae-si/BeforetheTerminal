package org.agmas.noellesroles.btt;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * BT-P2-UI 选人 UI 统一上行：目标玩家 + 角色猜测文本（舞蛇人=控诉、魔术师=空串即换位请求）。
 * 语义按发起者身份在 {@link BttGuessReceiver} 分派。
 */
public record BttGuessC2SPacket(UUID target, String guess) implements CustomPayload {
    public static final Identifier ID_RAW = Identifier.of(BttGameModes.BEFORE_THE_TERMINAL.ID.getNamespace(), "select");
    public static final Id<BttGuessC2SPacket> ID = new Id<>(ID_RAW);
    public static final PacketCodec<RegistryByteBuf, BttGuessC2SPacket> CODEC = PacketCodec.of(
            BttGuessC2SPacket::write, BttGuessC2SPacket::read);

    private static void write(BttGuessC2SPacket p, PacketByteBuf buf) {
        buf.writeUuid(p.target);
        buf.writeString(p.guess);
    }

    private static BttGuessC2SPacket read(PacketByteBuf buf) {
        return new BttGuessC2SPacket(buf.readUuid(), buf.readString());
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
