package com.fofo.racas;

import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Pacote cliente -> servidor com a lista de mods e de texturas ativas do jogador. */
public record ClientInfoPayload(List<String> mods, List<String> packs) implements CustomPayload {
    public static final CustomPayload.Id<ClientInfoPayload> ID =
        new CustomPayload.Id<>(Identifier.of(RacasMod.MOD_ID, "client_info"));

    private static final PacketCodec<ByteBuf, List<String>> LIST =
        PacketCodecs.STRING.collect(PacketCodecs.toList(1000));

    public static final PacketCodec<ByteBuf, ClientInfoPayload> CODEC = PacketCodec.tuple(
        LIST, ClientInfoPayload::mods,
        LIST, ClientInfoPayload::packs,
        ClientInfoPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
