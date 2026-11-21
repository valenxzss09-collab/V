package com.fofo.racas;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** /inv <jogador>: abre o inventário de outro jogador, com edição ao vivo. */
public class InvMenu extends GenericContainerScreenHandler {

    private InvMenu(int syncId, PlayerInventory inv, ProxyInventory container) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, inv, container, 6);
    }

    public static void open(ServerPlayerEntity viewer, ServerPlayerEntity target) {
        ProxyInventory proxy = new ProxyInventory(target);
        viewer.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (id, inv, p) -> new InvMenu(id, inv, proxy),
            Text.literal("Inventário de " + target.getName().getString())));
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        // Vidros decorativos (slots sem mapeamento) não podem ser clicados.
        if (slotIndex >= 0 && slotIndex < 54 && ProxyInventory.MAP[slotIndex] < 0) return;
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        // Shift+clique desativado (evita mover itens para os vidros). Arraste normalmente.
        return ItemStack.EMPTY;
    }
}
