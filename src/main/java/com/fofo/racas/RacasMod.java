package com.fofo.racas;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RacasMod implements ModInitializer {
    public static final String MOD_ID = "fofo_racas";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(RaceData::load);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> RaceData.get().save());

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
        {
            RaceCommand.register(dispatcher);
            InfoCommand.register(dispatcher);
            BuffCommand.register(dispatcher);
            ViewCommands.register(dispatcher);
        });

        // Recebe a lista de mods/texturas enviada pelo cliente (/mods e /texturas).
        ClientInfo.register();
        RaceSync.register();

        ServerTickEvents.END_SERVER_TICK.register(RaceEffects::tick);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.getPlayer();
            RaceData data = RaceData.get();
            data.markKnown(p.getUuid());
            Race r = data.getRace(p.getUuid());
            if (r != null) RaceEffects.apply(p, r, 0);
            RaceEffects.applyBuffs(p);
            refreshTab(p);
            RaceSync.send(p);
        });

        // Selkie: animais não fogem quando levam hit.
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (entity instanceof AnimalEntity animal
                && source.getAttacker() instanceof ServerPlayerEntity sp
                && RaceData.get().getRace(sp.getUuid()) == Race.SELKIE) {
                animal.setAttacker(null);
                animal.getNavigation().stop();
            }
        });

        PlayerBlockBreakEvents.AFTER.register(RaceEffects::onBlockBroken);

        UseItemCallback.EVENT.register(RacasMod::onUseItem);

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (RaceData.get().getRace(sp.getUuid()) == Race.LAMPADE) return torches(sp, hand);
            return ActionResult.PASS;
        });

        // Meio Besta-Fera: doma cavalos instantaneamente.
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClient()
                && hand == Hand.MAIN_HAND
                && player instanceof ServerPlayerEntity sp
                && entity instanceof AbstractHorseEntity horse
                && !horse.isTame()
                && RaceData.get().getRace(sp.getUuid()) == Race.MEIO_BESTA_FERA) {
                horse.bondWithPlayer(sp);
            }
            return ActionResult.PASS;
        });
    }

    private static ActionResult onUseItem(PlayerEntity player, World world, Hand hand) {
        if (world.isClient() || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
        Race r = RaceData.get().getRace(sp.getUuid());
        if (r == Race.MEIO_BESTA_FERA) {
            ItemStack s = sp.getStackInHand(hand);
            if (s.isIn(ItemTags.MEAT) || s.isIn(ItemTags.FISHES)) {
                sp.sendMessage(Text.literal("Você não consegue comer carne."), true);
                return ActionResult.FAIL;
            }
        }
        if (r == Race.DEUSA && hand == Hand.MAIN_HAND && sp.getMainHandStack().isOf(Items.STICK)) {
            return RaceEffects.wandUse(sp);
        }
        if (r == Race.O && hand == Hand.MAIN_HAND && sp.getMainHandStack().isOf(Items.BLAZE_ROD)) {
            return RaceEffects.oWandUse(sp);
        }
        if (r == Race.LAMPADE) return torches(sp, hand);
        return ActionResult.PASS;
    }

    private static ActionResult torches(ServerPlayerEntity sp, Hand hand) {
        if (hand == Hand.MAIN_HAND && sp.isSneaking() && sp.getMainHandStack().isEmpty()
            && RaceEffects.giveTorches(sp)) {
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    // ----------------------------------------------------- API usada por menu/comandos

    public static void chooseRace(ServerPlayerEntity p, Race race) {
        RaceData data = RaceData.get();
        Race old = data.getRace(p.getUuid());
        RaceEffects.clear(p, old);
        data.setRace(p.getUuid(), race);
        RaceEffects.apply(p, race, 0);
        p.setHealth(p.getMaxHealth());
        RaceSync.send(p);
        p.sendMessage(Text.literal("Você agora é " + race.display + "!"), false);
        refreshTab(p);
        if (race == Race.DEUSA) {
            ItemStack wand = new ItemStack(Items.STICK);
            wand.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Cajado da Deusa"));
            p.getInventory().offerOrDrop(wand);
            p.sendMessage(Text.literal("Laser: toque duplo no Shift. Telecinese: botão direito com o cajado."), false);
        }
        if (race == Race.O) {
            ItemStack rod = new ItemStack(Items.BLAZE_ROD);
            rod.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Cetro do O"));
            p.getInventory().offerOrDrop(rod);
            p.sendMessage(Text.literal("Cetro do O: botão direito usa o poder, Shift + botão direito troca de poder."), false);
        }
    }

    public static void removeRace(ServerPlayerEntity p) {
        RaceData data = RaceData.get();
        Race old = data.getRace(p.getUuid());
        RaceEffects.clear(p, old);
        data.removeRace(p.getUuid());
        RaceSync.send(p);
        p.sendMessage(Text.literal("Sua raça foi removida."), false);
        refreshTab(p);
    }

    /** Atualiza o nome "Raça - Nome" na lista de jogadores de todo mundo. */
    public static void refreshTab(ServerPlayerEntity p) {
        p.getEntityWorld().getServer().getPlayerManager().sendToAll(
            new PlayerListS2CPacket(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME, p));
    }
}
