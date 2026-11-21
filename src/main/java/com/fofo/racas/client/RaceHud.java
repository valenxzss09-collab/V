package com.fofo.racas.client;

import com.fofo.racas.Race;
import com.fofo.racas.RacasMod;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** Painel à esquerda da tela com as habilidades da raça do jogador. */
public final class RaceHud {
    private RaceHud() {}

    public static boolean enabled = true;
    private static Race race;
    private static int mode;

    private static final int WIDTH = 150;
    private static final int PAD = 5;
    private static final int LINE = 10;

    private static final int GOLD = 0xFFFFAA00;
    private static final int GREEN = 0xFF55FF55;
    private static final int RED = 0xFFFF5555;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFAAAAAA;

    private record Line(OrderedText text, int color) {}

    public static void set(String raceId, int m) {
        race = Race.byId(raceId);
        mode = m;
    }

    public static void clear() {
        race = null;
        mode = 0;
    }

    public static void register() {
        HudElementRegistry.addLast(Identifier.of(RacasMod.MOD_ID, "habilidades"), RaceHud::render);
    }

    private static void render(DrawContext ctx, RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!enabled || race == null || mc.player == null || mc.options.hudHidden) return;

        TextRenderer tr = mc.textRenderer;
        int inner = WIDTH - PAD * 2 - 2;
        List<Line> lines = new ArrayList<>();

        if (race == Race.O) {
            add(lines, tr, "+ Imortal: nenhum dano te mata", GREEN, inner);
            add(lines, tr, "Poderes (Cetro do O):", GRAY, inner);
            for (int i = 0; i < Race.O_POWERS.size(); i++) {
                boolean sel = i == mode;
                add(lines, tr, (sel ? "> " : "  ") + (i + 1) + ". " + Race.O_POWERS.get(i),
                    sel ? GOLD : WHITE, inner);
            }
            add(lines, tr, "Botão direito: usar", GRAY, inner);
            add(lines, tr, "Shift + botão direito: trocar", GRAY, inner);
        } else {
            for (String l : race.lore) {
                int color = l.startsWith("+") ? GREEN : l.startsWith("-") ? RED : WHITE;
                add(lines, tr, l, color, inner);
            }
        }

        int h = PAD * 2 + 14 + lines.size() * LINE;
        int x = 6;
        int y = (ctx.getScaledWindowHeight() - h) / 2;

        ctx.fill(x, y, x + WIDTH, y + h, 0xB0101018);            // fundo
        ctx.fill(x, y, x + WIDTH, y + 14, 0xD0302208);           // barra do título
        ctx.fill(x, y, x + 2, y + h, GOLD);                      // faixa dourada à esquerda
        ctx.fill(x + 2, y + 14, x + WIDTH, y + 15, 0x60FFAA00);  // linha sob o título

        ctx.drawTextWithShadow(tr, race.display, x + PAD + 2, y + 3, GOLD);

        int ty = y + 14 + PAD;
        for (Line line : lines) {
            ctx.drawTextWithShadow(tr, line.text(), x + PAD + 2, ty, line.color());
            ty += LINE;
        }
    }

    private static void add(List<Line> out, TextRenderer tr, String text, int color, int width) {
        for (OrderedText o : tr.wrapLines(Text.literal(text), width)) {
            out.add(new Line(o, color));
        }
    }
}
