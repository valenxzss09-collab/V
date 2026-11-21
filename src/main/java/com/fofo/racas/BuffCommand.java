package com.fofo.racas;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Arrays;
import java.util.Map;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * /buff <player> <tipo> <valor>   ex.: /buff Pessoa forca 20  (+20 de força)
 * /buff remover <player> [tipo]
 * /buff ver <player>
 * Tipos: forca, vida, armadura (valor absoluto) e velocidade, pulo, tamanho (em %).
 * Valor negativo vira debuff. Fica salvo até ser removido.
 */
public final class BuffCommand {
    private BuffCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(CommandManager.literal("buff")
            .requires(RaceCommand.opOnly())
            .then(CommandManager.literal("remover")
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .executes(ctx -> remover(ctx, null))
                    .then(CommandManager.argument("tipo", StringArgumentType.word())
                        .suggests(BuffCommand::suggestTypes)
                        .executes(ctx -> remover(ctx, StringArgumentType.getString(ctx, "tipo"))))))
            .then(CommandManager.literal("ver")
                .then(CommandManager.argument("player", EntityArgumentType.player())
                    .executes(BuffCommand::ver)))
            .then(CommandManager.argument("player", EntityArgumentType.player())
                .then(CommandManager.argument("tipo", StringArgumentType.word())
                    .suggests(BuffCommand::suggestTypes)
                    .then(CommandManager.argument("valor", DoubleArgumentType.doubleArg(-1000, 1000))
                        .executes(BuffCommand::dar)))));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestTypes(
            CommandContext<ServerCommandSource> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder b) {
        return CommandSource.suggestMatching(Arrays.stream(BuffType.values()).map(t -> t.id), b);
    }

    private static BuffType type(String id) throws CommandSyntaxException {
        BuffType t = BuffType.byId(id);
        if (t == null) {
            throw new SimpleCommandExceptionType(Text.literal(
                "Tipo inválido. Use: forca, vida, armadura, velocidade, pulo, tamanho.")).create();
        }
        return t;
    }

    private static int dar(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
        BuffType t = type(StringArgumentType.getString(ctx, "tipo"));
        double v = DoubleArgumentType.getDouble(ctx, "valor");
        RaceData.get().setBuff(target.getUuid(), t.id, v);
        RaceEffects.applyBuffs(target);
        String suffix = t.percent ? "%" : "";
        String sign = v >= 0 ? "+" : "";
        ctx.getSource().sendFeedback(() -> Text.literal(
            sign + trim(v) + suffix + " de " + t.display + " para " + target.getName().getString() + "."), true);
        return 1;
    }

    private static int remover(CommandContext<ServerCommandSource> ctx, String tipo) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
        if (tipo == null) {
            RaceData.get().clearBuffs(target.getUuid());
        } else {
            RaceData.get().removeBuff(target.getUuid(), type(tipo).id);
        }
        RaceEffects.applyBuffs(target);
        ctx.getSource().sendFeedback(() -> Text.literal(
            "Buffs de " + target.getName().getString() + " removidos."), true);
        return 1;
    }

    private static int ver(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
        Map<String, Double> buffs = RaceData.get().getBuffs(target.getUuid());
        if (buffs.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal(target.getName().getString() + " não tem buffs."), false);
            return 1;
        }
        for (Map.Entry<String, Double> e : buffs.entrySet()) {
            BuffType t = BuffType.byId(e.getKey());
            if (t == null) continue;
            double v = e.getValue();
            String line = target.getName().getString() + ": " + (v >= 0 ? "+" : "") + trim(v)
                + (t.percent ? "%" : "") + " " + t.display;
            ctx.getSource().sendFeedback(() -> Text.literal(line), false);
        }
        return 1;
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
