package com.fofo.racas;

import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.entry.RegistryEntry;

/** Tipos de buff do comando /buff. "percent" = valor em porcentagem (20 = +20%). */
public enum BuffType {
    FORCA("forca", "Força", EntityAttributes.ATTACK_DAMAGE, EntityAttributeModifier.Operation.ADD_VALUE, false),
    VIDA("vida", "Vida máxima", EntityAttributes.MAX_HEALTH, EntityAttributeModifier.Operation.ADD_VALUE, false),
    ARMADURA("armadura", "Armadura", EntityAttributes.ARMOR, EntityAttributeModifier.Operation.ADD_VALUE, false),
    VELOCIDADE("velocidade", "Velocidade", EntityAttributes.MOVEMENT_SPEED, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE, true),
    PULO("pulo", "Pulo", EntityAttributes.JUMP_STRENGTH, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE, true),
    TAMANHO("tamanho", "Tamanho", EntityAttributes.SCALE, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE, true);

    public final String id;
    public final String display;
    public final RegistryEntry<EntityAttribute> attribute;
    public final EntityAttributeModifier.Operation operation;
    public final boolean percent;

    BuffType(String id, String display, RegistryEntry<EntityAttribute> attribute,
             EntityAttributeModifier.Operation operation, boolean percent) {
        this.id = id;
        this.display = display;
        this.attribute = attribute;
        this.operation = operation;
        this.percent = percent;
    }

    public static BuffType byId(String id) {
        if (id == null) return null;
        for (BuffType t : values()) {
            if (t.id.equalsIgnoreCase(id)) return t;
        }
        return null;
    }
}
