package com.fofo.racas;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Arrays;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** /raça mudar|retirar|selecionar  (também funciona como /raca). Só OP. */
public final class RaceCommand {
    private RaceCommand() {}

    /** Checagem de OP usada por /raça e /buff. Se a 1.21.11 reclamar, ajuste só aqui. */
    static java.util.function.Predicate<ServerCommandSource> opOnly() {
        return CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK);
    }

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(build("raça"));
        d.register(build("raca"));
    }

    private static LiteralArgumentBuilder<ServerCommandSource> build(String name) {
        return CommandManager.literal(name)
            // Se der erro de compilação aqui, veja o README (seção "Se o build falhar").
            .requires(opOnly())
            .then(CommandManager.literal("mudar")
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .then(CommandManager.argument("raca", StringArgumentType.word())
                        .suggests((ctx, b) -> CommandSource.suggestMatching(
                            Arrays.stream(Race.values()).map(r -> r.id), b))
                        .executes(RaceCommand::mudar))))
            .then(CommandManager.literal("retirar")
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .executes(RaceCommand::retirar)))
            .then(CommandManager.literal("selecionar")
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .executes(RaceCommand::selecionar)));
    }

    private static int mudar(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
        Race race = Race.byId(StringArgumentType.getString(ctx, "raca"));
        if (race == null) {
            throw new SimpleCommandExceptionType(Text.literal("Raça inválida.")).create();
        }
        RacasMod.chooseRace(target, race);
        ctx.getSource().sendFeedback(
            () -> Text.literal(target.getName().getString() + " agora é " + race.display + "."), true);
        return 1;
    }

    private static int retirar(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
        RacasMod.removeRace(target);
        ctx.getSource().sendFeedback(
            () -> Text.literal("Raça de " + target.getName().getString() + " removida."), true);
        return 1;
    }

    private static int selecionar(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
        RaceData.get().setPending(target.getUuid());
        RaceMenu.open(target);
        ctx.getSource().sendFeedback(
            () -> Text.literal("Menu de raças aberto para " + target.getName().getString() + "."), true);
        return 1;
    }
}
