package com.fofo.racas;

import java.util.Arrays;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

public enum Race {
    KITSUNE("kitsune", "Kitsune", Blocks.ORANGE_WOOL,
        "+ Mais velocidade, HP e pulo",
        "+ Zumbis evitam você",
        "- Dobro de dano de lobos e cães",
        "- Perde as vantagens na água"),
    MEIO_BESTA_FERA("meio_besta_fera", "Meio Besta-Fera", Blocks.HAY_BLOCK,
        "+ Mais velocidade e HP",
        "+ Menos fome",
        "+ Doma cavalos instantaneamente",
        "- Não consegue comer carne"),
    ULFHEDNAR("ulfhednar", "Ulfhednar", Blocks.DEEPSLATE,
        "+ Mais dano em Kitsunes (+2,5%)",
        "+ À noite: força, resistência e regeneração leve",
        "- De dia perde as vantagens (menos o dano em Kitsunes)",
        "- De dia fica com muita fome"),
    SELKIE("selkie", "Selkie", Blocks.PRISMARINE,
        "+ Animais não fogem quando levam hit",
        "+ Regenera e nada muito rápido na água",
        "- 5,2% a mais de dano de fogo, magma e lava"),
    ENCANTADOS_AQUATICOS("encantados_aquaticos", "Encantados Aquáticos", Blocks.SEA_LANTERN,
        "+ Respira debaixo da água",
        "+ Nada extremamente rápido",
        "+ Mais força e resistência (na água)",
        "- Fora da água fica lento e com muita fome"),
    SATIRO("satiro", "Sátiro", Blocks.GRASS_BLOCK,
        "+ Pula alto e anda rápido",
        "+ Sem fome pisando em grama ou terra",
        "- Menos HP que o normal"),
    DVERGAR("dvergar", "Dvergar", Blocks.RAW_IRON_BLOCK,
        "+ Mais rápido",
        "+ Minera minérios já processados (lingotes)",
        "- Menor em tamanho",
        "- Menos HP"),
    BANSHEE("banshee", "Banshee", Blocks.PACKED_ICE,
        "+ Poções que recebe duram o dobro",
        "+ Imune a congelamento e frio",
        "- 12% a mais de dano de armas de ferro"),
    LAMPADE("lampade", "Lampade", Blocks.GLOWSTONE,
        "+ Visão noturna",
        "+ Shift + botão direito com a mão vazia dá tochas",
        "+ Zumbis e esqueletos não atacam",
        "- Pega fogo no sol"),
    SPARTOI("spartoi", "Spartoi", Blocks.BONE_BLOCK,
        "+ Imune ao fogo",
        "+ Mais resistência, dano e HP",
        "- Toma dano da água e da chuva"),
    MAYOIREI("mayoirei", "Mayoirei", Blocks.PURPUR_BLOCK,
        "+ Zumbis e esqueletos ignoram você",
        "+ Imune a dano de queda",
        "+ Dobro de HP",
        "- Dobro de dano de armas de ferro, lava, fogo e magma",
        "- Lento e fraco no Nether"),
    VAL("val", "Val", Blocks.QUARTZ_BLOCK,
        "+ Consegue voar"),
    DEUSA("deusa", "Deusa", Blocks.GOLD_BLOCK,
        "+ Voa, não sente fome e se regenera",
        "+ Muito mais HP, velocidade, pulo, dano e armadura",
        "+ Imune a queda, fogo e afogamento",
        "+ Laser: toque duplo no Shift (destrói muitos blocos)",
        "+ Telecinese: botão direito com o cajado (graveto)",
        "- Laser tem recarga de 10 segundos"),
    O("o", "O", Blocks.CRYING_OBSIDIAN,
        "+ Imortal: nenhum dano te mata (nem /kill nem o void)",
        "+ Cetro do O (vara de blaze): botão direito usa o poder, Shift + botão direito troca",
        "+ Explosão gigante e longa, Congelar, Fogo, Telecinese, Escudo e Dobrar Água",
        "+ Sem fome, respira na água e imune a fogo",
        "- Cada poder tem recarga");

    /** Poderes do O, na mesma ordem de RaceEffects.OPower (usado pelo HUD). */
    public static final List<String> O_POWERS =
        List.of("Explosão", "Congelar", "Fogo", "Telecinese", "Escudo", "Dobrar Água");

    public final String id;
    public final String display;
    public final Block icon;
    public final List<String> lore;

    Race(String id, String display, Block icon, String... lore) {
        this.id = id;
        this.display = display;
        this.icon = icon;
        this.lore = List.of(lore);
    }

    /** Raças que aparecem no menu. A Deusa e o O só podem ser dados por OP (/raça mudar). */
    public static Race[] menuRaces() {
        return Arrays.stream(values()).filter(r -> r != DEUSA && r != O).toArray(Race[]::new);
    }

    public static Race byId(String id) {
        if (id == null) return null;
        for (Race r : values()) {
            if (r.id.equalsIgnoreCase(id)) return r;
        }
        return null;
    }
}
