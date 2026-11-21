package com.fofo.racas;

import java.util.Arrays;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Inventário de 54 slots que aponta direto para o inventário de outro jogador.
 * Tudo que o OP mexer aqui muda o inventário real do jogador (e vice-versa, ao vivo).
 */
public class ProxyInventory implements Inventory {
    /** Para cada slot da tela: índice no PlayerInventory, ou -1 para vidro decorativo. */
    static final int[] MAP = new int[54];

    static {
        Arrays.fill(MAP, -1);
        for (int i = 0; i < 27; i++) MAP[i] = 9 + i;   // inventário principal
        for (int i = 0; i < 9; i++) MAP[27 + i] = i;   // hotbar
        MAP[37] = 39; // capacete
        MAP[38] = 38; // peitoral
        MAP[39] = 37; // calça
        MAP[40] = 36; // bota
        MAP[42] = 40; // mão secundária
    }

    private final ServerPlayerEntity target;

    public ProxyInventory(ServerPlayerEntity target) {
        this.target = target;
    }

    private static ItemStack filler() {
        ItemStack s = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        s.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        return s;
    }

    private static int map(int slot) {
        return slot < 0 || slot >= MAP.length ? -1 : MAP[slot];
    }

    @Override
    public int size() {
        return 54;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public ItemStack getStack(int slot) {
        int i = map(slot);
        return i < 0 ? filler() : target.getInventory().getStack(i);
    }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        int i = map(slot);
        return i < 0 ? ItemStack.EMPTY : target.getInventory().removeStack(i, amount);
    }

    @Override
    public ItemStack removeStack(int slot) {
        int i = map(slot);
        return i < 0 ? ItemStack.EMPTY : target.getInventory().removeStack(i);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        int i = map(slot);
        if (i >= 0) target.getInventory().setStack(i, stack);
    }

    @Override
    public void markDirty() {
        target.getInventory().markDirty();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return !target.isRemoved();
    }

    @Override
    public void clear() {
        // de propósito não faz nada: nunca limpa o inventário do alvo por acidente
    }
}
