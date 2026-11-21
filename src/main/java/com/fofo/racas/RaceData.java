package com.fofo.racas;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

/** Guarda a raça de cada jogador num JSON dentro da pasta do mundo. */
public final class RaceData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static RaceData instance = new RaceData(null);

    private static class State {
        Map<String, String> races = new HashMap<>();
        Set<String> pending = new HashSet<>(); // quem precisa (ou pode) escolher
        Set<String> known = new HashSet<>();   // quem já entrou alguma vez
        Map<String, Map<String, Double>> buffs = new HashMap<>(); // buffs do /buff
    }

    private final Path file;
    private State state = new State();

    private RaceData(Path file) {
        this.file = file;
    }

    public static RaceData get() {
        return instance;
    }

    public static void load(MinecraftServer server) {
        Path f = server.getSavePath(WorldSavePath.ROOT).resolve("fofo_racas.json");
        RaceData d = new RaceData(f);
        if (Files.exists(f)) {
            try {
                State s = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), State.class);
                if (s != null) {
                    if (s.races == null) s.races = new HashMap<>();
                    if (s.pending == null) s.pending = new HashSet<>();
                    if (s.known == null) s.known = new HashSet<>();
                    if (s.buffs == null) s.buffs = new HashMap<>();
                    d.state = s;
                }
            } catch (Exception e) {
                RacasMod.LOGGER.error("Falha ao ler {}", f, e);
            }
        }
        instance = d;
    }

    public void save() {
        if (file == null) return;
        try {
            Files.writeString(file, GSON.toJson(state), StandardCharsets.UTF_8);
        } catch (IOException e) {
            RacasMod.LOGGER.error("Falha ao salvar {}", file, e);
        }
    }

    public Race getRace(UUID id) {
        return Race.byId(state.races.get(id.toString()));
    }

    public void setRace(UUID id, Race r) {
        state.races.put(id.toString(), r.id);
        state.pending.remove(id.toString());
        save();
    }

    public void removeRace(UUID id) {
        state.races.remove(id.toString());
        state.pending.remove(id.toString());
        save();
    }

    public boolean isPending(UUID id) {
        return state.pending.contains(id.toString());
    }

    public void setPending(UUID id) {
        state.pending.add(id.toString());
        save();
    }

    /** Primeira vez que o jogador entra: ele fica pendente para escolher a raça. */
    public void markKnown(UUID id) {
        if (state.known.add(id.toString())) {
            if (!state.races.containsKey(id.toString())) state.pending.add(id.toString());
            save();
        }
    }

    // ---- buffs ----

    public Map<String, Double> getBuffs(UUID id) {
        Map<String, Double> m = state.buffs.get(id.toString());
        return m == null ? Map.of() : m;
    }

    public void setBuff(UUID id, String type, double value) {
        state.buffs.computeIfAbsent(id.toString(), k -> new HashMap<>()).put(type, value);
        save();
    }

    public void removeBuff(UUID id, String type) {
        Map<String, Double> m = state.buffs.get(id.toString());
        if (m != null) {
            m.remove(type);
            if (m.isEmpty()) state.buffs.remove(id.toString());
            save();
        }
    }

    public void clearBuffs(UUID id) {
        state.buffs.remove(id.toString());
        save();
    }
}
