package com.fofo.racas;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** Guarda (só na memória) os mods e texturas que cada jogador informou ao entrar. */
public final class ClientInfo {
    private ClientInfo() {}

    public record Data(List<String> mods, List<String> packs) {}

    private static final Map<UUID, Data> MAP = new ConcurrentHashMap<>();

    public static Data get(UUID id) {
        return MAP.get(id);
    }

    /** Chamar nos dois lados (servidor e cliente): registra o tipo de pacote. */
    public static void register() {
        PayloadTypeRegistry.playC2S().register(ClientInfoPayload.ID, ClientInfoPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ClientInfoPayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            MAP.put(p.getUuid(), new Data(List.copyOf(payload.mods()), List.copyOf(payload.packs())));
            // transparência: o jogador sabe que a lista foi enviada
            p.sendMessage(Text.literal("[Fofo Raças] O servidor registrou a lista dos seus mods e texturas."), false);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            MAP.remove(handler.getPlayer().getUuid()));
    }
}
