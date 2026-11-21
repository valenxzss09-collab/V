package com.fofo.racas;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Servidor -> cliente: raça do jogador e poder atual do O (para o HUD). */
public record RaceSyncPayload(String raceId, int mode) implements CustomPayload {
    public static final CustomPayload.Id<RaceSyncPayload> ID =
        new CustomPayload.Id<>(Identifier.of(RacasMod.MOD_ID, "race_sync"));

    public static final PacketCodec<ByteBuf, RaceSyncPayload> CODEC = PacketCodec.tuple(
        PacketCodecs.STRING, RaceSyncPayload::raceId,
        PacketCodecs.VAR_INT, RaceSyncPayload::mode,
        RaceSyncPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
