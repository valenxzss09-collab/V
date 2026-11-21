package com.fofo.racas;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Arrays;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** /info raças  e  /info raças <raça>  (qualquer jogador pode usar). */
public final class InfoCommand {
    private InfoCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(build("raças"));
        d.register(build("racas"));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> build(String sub) {
        return CommandManager.literal("info")
            .then(CommandManager.literal(sub)
                .executes(InfoCommand::list)
                .then(CommandManager.argument("raca", StringArgumentType.word())
                    .suggests((ctx, b) -> CommandSource.suggestMatching(
                        Arrays.stream(Race.values()).map(r -> r.id), b))
                    .executes(InfoCommand::detail)));
    }

    private static int list(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource src = ctx.getSource();
        src.sendFeedback(() -> Text.literal("=== Raças dos jogadores ===").formatted(Formatting.GOLD), false);
        for (ServerPlayerEntity p : src.getServer().getPlayerManager().getPlayerList()) {
            Race r = RaceData.get().getRace(p.getUuid());
            String raceName = r == null ? "sem raça" : r.display;
            String playerName = p.getName().getString();
            src.sendFeedback(() -> Text.literal(raceName + " - " + playerName), false);
        }
        String ids = String.join(", ", Arrays.stream(Race.values()).map(r -> r.id).toList());
        src.sendFeedback(() -> Text.literal("Detalhes: /info raças <raça>").formatted(Formatting.GRAY), false);
        src.sendFeedback(() -> Text.literal("Raças: " + ids).formatted(Formatting.GRAY), false);
        return 1;
    }

    private static int detail(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        Race r = Race.byId(StringArgumentType.getString(ctx, "raca"));
        if (r == null) {
            throw new SimpleCommandExceptionType(Text.literal("Raça não encontrada.")).create();
        }
        ServerCommandSource src = ctx.getSource();
        src.sendFeedback(() -> Text.literal("=== " + r.display + " ===").formatted(Formatting.GOLD), false);
        for (String line : r.lore) {
            Formatting color = line.startsWith("+") ? Formatting.GREEN : Formatting.RED;
            src.sendFeedback(() -> Text.literal(line).formatted(color), false);
        }
        return 1;
    }
}
