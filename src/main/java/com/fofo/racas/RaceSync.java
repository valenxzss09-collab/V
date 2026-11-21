package com.fofo.racas;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

/** Envia a raça para quem tem o mod no cliente (jogadores vanilla simplesmente não recebem). */
public final class RaceSync {
    private RaceSync() {}

    /** Chamar nos dois lados (servidor e cliente). */
    public static void register() {
        PayloadTypeRegistry.playS2C().register(RaceSyncPayload.ID, RaceSyncPayload.CODEC);
    }

    public static void send(ServerPlayerEntity p) {
        if (!ServerPlayNetworking.canSend(p, RaceSyncPayload.ID)) return;
        Race r = RaceData.get().getRace(p.getUuid());
        ServerPlayNetworking.send(p, new RaceSyncPayload(r == null ? "" : r.id, RaceEffects.oMode(p.getUuid())));
    }
}
