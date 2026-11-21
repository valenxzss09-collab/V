package com.fofo.racas;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Menu de baú no servidor: cada raça é um bloco. Funciona com cliente vanilla. */
public class RaceMenu extends GenericContainerScreenHandler {
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 20, 21, 22, 23, 24};

    private RaceMenu(int syncId, PlayerInventory inv, SimpleInventory container) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, inv, container, 3);
    }

    public static void open(ServerPlayerEntity player) {
        SimpleInventory c = new SimpleInventory(27);
        ItemStack filler = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        filler.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        for (int i = 0; i < 27; i++) c.setStack(i, filler.copy());
        Race[] races = Race.menuRaces();
        for (int i = 0; i < races.length && i < SLOTS.length; i++) {
            c.setStack(SLOTS[i], icon(races[i]));
        }
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (id, inv, p) -> new RaceMenu(id, inv, c),
            Text.literal("Escolha sua raça")));
    }

    private static ItemStack icon(Race r) {
        ItemStack s = new ItemStack(r.icon.asItem());
        s.set(DataComponentTypes.CUSTOM_NAME,
            Text.literal(r.display).styled(st -> st.withItalic(false).withBold(true).withColor(Formatting.GOLD)));
        List<Text> lore = new ArrayList<>();
        for (String line : r.lore) {
            Formatting color = line.startsWith("+") ? Formatting.GREEN : Formatting.RED;
            lore.add(Text.literal(line).styled(st -> st.withItalic(false).withColor(color)));
        }
        lore.add(Text.empty());
        lore.add(Text.literal("Clique para escolher").styled(st -> st.withItalic(false).withColor(Formatting.GRAY)));
        s.set(DataComponentTypes.LORE, new LoreComponent(lore));
        return s;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        // Nunca chama super: nada pode ser movido neste menu.
        if (player instanceof ServerPlayerEntity sp) {
            Race[] races = Race.menuRaces();
            for (int i = 0; i < races.length && i < SLOTS.length; i++) {
                if (SLOTS[i] == slotIndex) {
                    RacasMod.chooseRace(sp, races[i]);
                    sp.closeHandledScreen();
                    return;
                }
            }
        }
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }
}
