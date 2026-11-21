package com.fofo.racas;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** /mods, /texturas e /inv (só OP). */
public final class ViewCommands {
    private ViewCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(CommandManager.literal("mods")
            .requires(RaceCommand.opOnly())
            .executes(ctx -> listAll(ctx, true))
            .then(CommandManager.argument("player", EntityArgumentType.player())
                .executes(ctx -> detail(ctx, true))));

        d.register(CommandManager.literal("texturas")
            .requires(RaceCommand.opOnly())
            .executes(ctx -> listAll(ctx, false))
            .then(CommandManager.argument("player", EntityArgumentType.player())
                .executes(ctx -> detail(ctx, false))));

        d.register(CommandManager.literal("inv")
            .requires(RaceCommand.opOnly())
            .then(CommandManager.argument("player", EntityArgumentType.player())
                .executes(ViewCommands::inv)));
    }

    private static int listAll(CommandContext<ServerCommandSource> ctx, boolean mods) {
        ServerCommandSource src = ctx.getSource();
        String title = mods ? "=== Mods dos jogadores ===" : "=== Texturas dos jogadores ===";
        src.sendFeedback(() -> Text.literal(title).formatted(Formatting.GOLD), false);
        for (ServerPlayerEntity p : src.getServer().getPlayerManager().getPlayerList()) {
            ClientInfo.Data info = ClientInfo.get(p.getUuid());
            String name = p.getName().getString();
            String line;
            if (info == null) {
                line = name + ": sem dados (cliente sem o mod Fofo Raças)";
            } else if (mods) {
                line = name + ": " + info.mods().size() + " mods";
            } else {
                line = name + ": " + info.packs().size() + " pacotes de textura";
            }
            src.sendFeedback(() -> Text.literal(line), false);
        }
        String hint = mods ? "Detalhes: /mods <jogador>" : "Detalhes: /texturas <jogador>";
        src.sendFeedback(() -> Text.literal(hint).formatted(Formatting.GRAY), false);
        return 1;
    }

    private static int detail(CommandContext<ServerCommandSource> ctx, boolean mods) throws CommandSyntaxException {
        ServerCommandSource src = ctx.getSource();
        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
        String name = target.getName().getString();
        ClientInfo.Data info = ClientInfo.get(target.getUuid());
        if (info == null) {
            src.sendFeedback(() -> Text.literal(name
                + " não enviou dados (o cliente dele não tem o mod Fofo Raças instalado)."), false);
            return 0;
        }
        List<String> list = mods ? info.mods() : info.packs();
        String title = "=== " + (mods ? "Mods" : "Texturas") + " de " + name + " (" + list.size() + ") ===";
        src.sendFeedback(() -> Text.literal(title).formatted(Formatting.GOLD), false);
        if (list.isEmpty()) {
            src.sendFeedback(() -> Text.literal("(nenhum)").formatted(Formatting.GRAY), false);
            return 1;
        }
        // junta de 6 em 6 para não lotar o chat
        for (int i = 0; i < list.size(); i += 6) {
            String chunk = String.join(" | ", list.subList(i, Math.min(i + 6, list.size())));
            src.sendFeedback(() -> Text.literal(chunk), false);
        }
        return 1;
    }

    private static int inv(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity viewer = ctx.getSource().getPlayerOrThrow();
        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
        InvMenu.open(viewer, target);
        return 1;
    }
}
