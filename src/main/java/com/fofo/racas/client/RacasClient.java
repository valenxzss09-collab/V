package com.fofo.racas.client;

import com.fofo.racas.ClientInfoPayload;
import com.fofo.racas.RaceSyncPayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.text.Text;

/**
 * Parte de cliente:
 * - envia a lista de mods/texturas ao entrar num servidor que tenha este mod;
 * - mostra o HUD com as habilidades da raça (à esquerda). /fofohud liga e desliga.
 */
public class RacasClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RaceHud.register();

        ClientPlayNetworking.registerGlobalReceiver(RaceSyncPayload.ID,
            (payload, context) -> RaceHud.set(payload.raceId(), payload.mode()));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("fofohud").executes(ctx -> {
                RaceHud.enabled = !RaceHud.enabled;
                ctx.getSource().sendFeedback(Text.literal(
                    RaceHud.enabled ? "HUD de habilidades ligado." : "HUD de habilidades desligado."));
                return 1;
            })));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> RaceHud.clear());

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!ClientPlayNetworking.canSend(ClientInfoPayload.ID)) return;

            List<String> mods = new ArrayList<>();
            for (ModContainer m : FabricLoader.getInstance().getAllMods()) {
                if (m.getContainingMod().isPresent()) continue; // ignora mods embutidos (jar-in-jar)
                ModMetadata md = m.getMetadata();
                mods.add(cut(md.getId() + " " + md.getVersion().getFriendlyString()));
            }
            Collections.sort(mods);

            List<String> packs = new ArrayList<>();
            for (ResourcePackProfile prof : client.getResourcePackManager().getEnabledProfiles()) {
                packs.add(cut(prof.getId() + " (" + prof.getDisplayName().getString() + ")"));
            }

            ClientPlayNetworking.send(new ClientInfoPayload(limit(mods), limit(packs)));
        });
    }

    private static String cut(String s) {
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    private static List<String> limit(List<String> l) {
        return l.size() > 900 ? new ArrayList<>(l.subList(0, 900)) : l;
    }
}
