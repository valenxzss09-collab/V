package com.fofo.racas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

/** Toda a lógica de vantagens e desvantagens das raças. */
public final class RaceEffects {
    private RaceEffects() {}

    private static final Set<Item> IRON_WEAPONS = Set.of(
        Items.IRON_SWORD, Items.IRON_AXE, Items.IRON_PICKAXE, Items.IRON_SHOVEL, Items.IRON_HOE);

    private static final Map<UUID, Long> TORCH_COOLDOWN = new HashMap<>();

    private record Conversion(ServerWorld world, BlockPos pos) {}
    private static final List<Conversion> CONVERSIONS = new ArrayList<>();

    // ---------------------------------------------------------------- tick

    public static void tick(MinecraftServer server) {
        int t = server.getTicks();

        if (!CONVERSIONS.isEmpty()) {
            for (Conversion c : CONVERSIONS) convertDrops(c);
            CONVERSIONS.clear();
        }

        boolean scan = t % 5 == 0;
        boolean full = t % 10 == 0;

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            RaceData data = RaceData.get();
            Race r = data.getRace(p.getUuid());
            if (r == Race.DEUSA) {
                supremaSneak(p, t);
                telekinesisTick(p);
            }
            if (!scan && !full) continue;
            if (full) applyBuffs(p);
            if (t % 40 == 0 && data.isPending(p.getUuid()) && p.currentScreenHandler == p.playerScreenHandler) {
                RaceMenu.open(p);
            }
            if (r == null) continue;
            if (scan) ignoreScan(p, r);
            if (full) apply(p, r, t);
        }
    }

    /** Aplica (de forma idempotente) tudo que a raça dá ao jogador. */
    public static void apply(ServerPlayerEntity p, Race r, int t) {
        ServerWorld w = p.getEntityWorld();
        boolean water = p.isTouchingWater();
        boolean overworld = w.getRegistryKey() == World.OVERWORLD;
        boolean day = overworld && w.isDay();
        boolean night = overworld && !w.isDay();
        boolean nether = w.getRegistryKey() == World.NETHER;
        boolean safe = p.isCreative() || p.isSpectator();

        double speed = 0, jump = 0, scale = 0, hAdd = 0, hMul = 0;

        switch (r) {
            case KITSUNE -> {
                if (!water) { speed = 0.15; jump = 0.30; hAdd = 4; }
            }
            case MEIO_BESTA_FERA -> {
                speed = 0.10; hAdd = 4;
                if (t % 200 == 0) {
                    HungerManager hm = p.getHungerManager();
                    hm.setSaturationLevel(Math.min(hm.getFoodLevel(), hm.getSaturationLevel() + 1.0f));
                }
            }
            case ULFHEDNAR -> {
                want(p, StatusEffects.STRENGTH, 0, night);
                want(p, StatusEffects.RESISTANCE, 0, night);
                want(p, StatusEffects.REGENERATION, 0, night);
                want(p, StatusEffects.HUNGER, 2, day);
            }
            case SELKIE -> {
                want(p, StatusEffects.REGENERATION, 0, water);
                want(p, StatusEffects.DOLPHINS_GRACE, 0, water);
            }
            case ENCANTADOS_AQUATICOS -> {
                want(p, StatusEffects.WATER_BREATHING, 0, water);
                want(p, StatusEffects.DOLPHINS_GRACE, 1, water);
                want(p, StatusEffects.STRENGTH, 0, water);
                want(p, StatusEffects.RESISTANCE, 0, water);
                want(p, StatusEffects.SLOWNESS, 0, !water);
                want(p, StatusEffects.HUNGER, 2, !water);
            }
            case SATIRO -> {
                speed = 0.10; jump = 0.35; hAdd = -4;
                BlockState below = w.getBlockState(p.getBlockPos().down());
                if (below.isIn(BlockTags.DIRT)) {
                    HungerManager hm = p.getHungerManager();
                    hm.setSaturationLevel(hm.getFoodLevel());
                }
            }
            case DVERGAR -> {
                speed = 0.15; scale = -0.35; hAdd = -4;
            }
            case BANSHEE -> p.setFrozenTicks(0);
            case LAMPADE -> {
                want(p, StatusEffects.NIGHT_VISION, 0, true);
                boolean sun = day && !water && !w.isRaining()
                    && w.isSkyVisible(p.getBlockPos())
                    && p.getEquippedStack(EquipmentSlot.HEAD).isEmpty();
                if (sun && !safe) p.setOnFireFor(4);
            }
            case SPARTOI -> {
                hAdd = 4;
                want(p, StatusEffects.FIRE_RESISTANCE, 0, true);
                want(p, StatusEffects.STRENGTH, 0, true);
                want(p, StatusEffects.RESISTANCE, 0, true);
                if (!safe && t % 20 == 0 && p.isTouchingWaterOrRain()) {
                    p.damage(w, w.getDamageSources().generic(), 1.0f);
                }
            }
            case MAYOIREI -> {
                hMul = 1.0;
                want(p, StatusEffects.SLOWNESS, 0, nether);
                want(p, StatusEffects.WEAKNESS, 0, nether);
            }
            case DEUSA -> {
                speed = 0.30; jump = 0.50; hAdd = 20;
                mod(p, "attack", EntityAttributes.ATTACK_DAMAGE, EntityAttributeModifier.Operation.ADD_VALUE, 6);
                mod(p, "armor", EntityAttributes.ARMOR, EntityAttributeModifier.Operation.ADD_VALUE, 10);
                want(p, StatusEffects.REGENERATION, 1, true);
                want(p, StatusEffects.RESISTANCE, 1, true);
                want(p, StatusEffects.NIGHT_VISION, 0, true);
                want(p, StatusEffects.WATER_BREATHING, 0, true);
                want(p, StatusEffects.FIRE_RESISTANCE, 0, true);
                HungerManager hm = p.getHungerManager();
                hm.setFoodLevel(20);
                hm.setSaturationLevel(20.0f);
                enableFlight(p);
            }
            case VAL -> {
                GameMode gm = p.interactionManager.getGameMode();
                if ((gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE) && !p.getAbilities().allowFlying) {
                    p.getAbilities().allowFlying = true;
                    p.sendAbilitiesUpdate();
                }
            }
        }

        mod(p, "speed", EntityAttributes.MOVEMENT_SPEED, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE, speed);
        mod(p, "jump", EntityAttributes.JUMP_STRENGTH, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE, jump);
        mod(p, "scale", EntityAttributes.SCALE, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE, scale);
        mod(p, "health_add", EntityAttributes.MAX_HEALTH, EntityAttributeModifier.Operation.ADD_VALUE, hAdd);
        mod(p, "health_mul", EntityAttributes.MAX_HEALTH, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE, hMul);
    }

    /** Remove tudo que uma raça aplicou (usado ao trocar ou retirar). */
    public static void clear(ServerPlayerEntity p, Race old) {
        mod(p, "speed", EntityAttributes.MOVEMENT_SPEED, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0);
        mod(p, "jump", EntityAttributes.JUMP_STRENGTH, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0);
        mod(p, "scale", EntityAttributes.SCALE, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0);
        mod(p, "health_add", EntityAttributes.MAX_HEALTH, EntityAttributeModifier.Operation.ADD_VALUE, 0);
        mod(p, "health_mul", EntityAttributes.MAX_HEALTH, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0);
        mod(p, "attack", EntityAttributes.ATTACK_DAMAGE, EntityAttributeModifier.Operation.ADD_VALUE, 0);
        mod(p, "armor", EntityAttributes.ARMOR, EntityAttributeModifier.Operation.ADD_VALUE, 0);

        // Efeitos aplicados por este mod são "ambient"; poções normais não são.
        for (StatusEffectInstance e : new ArrayList<>(p.getStatusEffects())) {
            if (e.isAmbient()) p.removeStatusEffect(e.getEffectType());
        }

        if (old == Race.VAL || old == Race.DEUSA) {
            GameMode gm = p.interactionManager.getGameMode();
            if (gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE) {
                p.getAbilities().allowFlying = false;
                p.getAbilities().flying = false;
                p.sendAbilitiesUpdate();
            }
        }
    }

    // ------------------------------------------------------------- helpers

    private static void want(ServerPlayerEntity p, RegistryEntry<StatusEffect> effect, int amp, boolean on) {
        StatusEffectInstance cur = p.getStatusEffect(effect);
        if (on) {
            boolean refresh = cur == null
                || (cur.isAmbient() && (cur.getDuration() < 250 || cur.getAmplifier() != amp));
            if (refresh) {
                p.addStatusEffect(new StatusEffectInstance(effect, 400, amp, true, false, false));
            }
        } else if (cur != null && cur.isAmbient()) {
            p.removeStatusEffect(effect);
        }
    }

    private static void mod(ServerPlayerEntity p, String key, RegistryEntry<EntityAttribute> attr,
                            EntityAttributeModifier.Operation op, double value) {
        EntityAttributeInstance inst = p.getAttributeInstance(attr);
        if (inst == null) return;
        Identifier id = Identifier.of(RacasMod.MOD_ID, key);
        EntityAttributeModifier cur = inst.getModifier(id);
        if (value == 0) {
            if (cur != null) inst.removeModifier(id);
            return;
        }
        if (cur != null && cur.value() == value) return;
        if (cur != null) inst.removeModifier(id);
        inst.addTemporaryModifier(new EntityAttributeModifier(id, value, op));
    }

    private static void ignoreScan(ServerPlayerEntity p, Race r) {
        boolean zombies = r == Race.KITSUNE || r == Race.LAMPADE || r == Race.MAYOIREI;
        boolean skeletons = r == Race.LAMPADE || r == Race.MAYOIREI;
        if (!zombies && !skeletons) return;
        Box box = p.getBoundingBox().expand(32);
        for (MobEntity mob : p.getEntityWorld().getEntitiesByClass(MobEntity.class, box, e -> e.getTarget() == p)) {
            if ((zombies && mob instanceof ZombieEntity) || (skeletons && mob instanceof AbstractSkeletonEntity)) {
                mob.setTarget(null);
            }
        }
    }

    // ------------------------------------------------------------- dano

    /** Retorna o novo dano, ou -1 para cancelar o dano completamente. */
    public static float modifyDamage(LivingEntity victim, DamageSource src, float amount) {
        Entity attacker = src.getAttacker();
        if (victim instanceof ServerPlayerEntity vp) {
            Race vr = RaceData.get().getRace(vp.getUuid());
            if (vr != null) {
                boolean fire = src.isIn(DamageTypeTags.IS_FIRE);
                switch (vr) {
                    case KITSUNE -> {
                        if (attacker instanceof WolfEntity) amount *= 2.0f;
                    }
                    case SELKIE -> {
                        if (fire) amount *= 1.052f;
                    }
                    case BANSHEE -> {
                        if (src.isIn(DamageTypeTags.IS_FREEZING)) return -1f;
                        if (isIronWeapon(attacker)) amount *= 1.12f;
                    }
                    case MAYOIREI -> {
                        if (src.isIn(DamageTypeTags.IS_FALL)) return -1f;
                        if (isIronWeapon(attacker)) amount *= 2.0f;
                        if (fire) amount *= 2.0f;
                    }
                    case DEUSA -> {
                        if (src.isIn(DamageTypeTags.IS_FALL)) return -1f;
                    }
                    default -> { }
                }
                if (vr == Race.KITSUNE && attacker instanceof ServerPlayerEntity ap
                    && RaceData.get().getRace(ap.getUuid()) == Race.ULFHEDNAR) {
                    amount *= 1.025f;
                }
            }
        }
        return amount;
    }

    private static boolean isIronWeapon(Entity attacker) {
        return attacker instanceof LivingEntity le && IRON_WEAPONS.contains(le.getMainHandStack().getItem());
    }

    // ------------------------------------------------------------- extras

    /** Lampade: tochas com shift + botão direito (mão vazia). */
    public static boolean giveTorches(ServerPlayerEntity p) {
        long now = p.getEntityWorld().getTime();
        Long last = TORCH_COOLDOWN.get(p.getUuid());
        if (last != null && now - last < 100) return false;
        TORCH_COOLDOWN.put(p.getUuid(), now);
        p.getInventory().offerOrDrop(new ItemStack(Items.TORCH, 4));
        p.sendMessage(Text.literal("Você recebeu 4 tochas."), true);
        return true;
    }

    /** Dvergar: minério bruto vira lingote. */
    public static void onBlockBroken(World world, PlayerEntity player, BlockPos pos, BlockState state, BlockEntity be) {
        if (!(world instanceof ServerWorld sw) || !(player instanceof ServerPlayerEntity sp)) return;
        if (RaceData.get().getRace(sp.getUuid()) != Race.DVERGAR) return;
        CONVERSIONS.add(new Conversion(sw, pos));
    }

    private static void convertDrops(Conversion c) {
        Box box = new Box(c.pos()).expand(1.5);
        for (ItemEntity ie : c.world().getEntitiesByClass(ItemEntity.class, box, e -> e.getItemAge() < 40)) {
            Item item = ie.getStack().getItem();
            Item out = null;
            if (item == Items.RAW_IRON) out = Items.IRON_INGOT;
            else if (item == Items.RAW_GOLD) out = Items.GOLD_INGOT;
            else if (item == Items.RAW_COPPER) out = Items.COPPER_INGOT;
            if (out != null) ie.setStack(new ItemStack(out, ie.getStack().getCount()));
        }
    }

    // ------------------------------------------------------------- buffs (/buff)

    public static void applyBuffs(ServerPlayerEntity p) {
        Map<String, Double> buffs = RaceData.get().getBuffs(p.getUuid());
        for (BuffType t : BuffType.values()) {
            double v = buffs.getOrDefault(t.id, 0.0);
            if (t.percent) v /= 100.0;
            mod(p, "buff_" + t.id, t.attribute, t.operation, v);
        }
    }

    // ------------------------------------------------------------- Deusa: laser

    private static final int DOUBLE_TAP_TICKS = 10;      // janela do toque duplo no Shift
    private static final long LASER_COOLDOWN = 200;      // ticks (10 s)
    private static final double LASER_RANGE = 64;        // blocos
    private static final int BEAM_RADIUS = 1;            // espessura do feixe
    private static final int NUKE_RADIUS = 8;            // raio da "nuke" no impacto

    private static final Map<UUID, Boolean> WAS_SNEAKING = new HashMap<>();
    private static final Map<UUID, Integer> LAST_TAP = new HashMap<>();
    private static final Map<UUID, Long> LASER_CD = new HashMap<>();

    /** Toque duplo no Shift = laser (evita disparar sem querer ao agachar uma vez). */
    private static void supremaSneak(ServerPlayerEntity p, int t) {
        boolean now = p.isSneaking();
        boolean was = WAS_SNEAKING.getOrDefault(p.getUuid(), false);
        WAS_SNEAKING.put(p.getUuid(), now);
        if (now && !was) {
            Integer last = LAST_TAP.get(p.getUuid());
            if (last != null && t - last <= DOUBLE_TAP_TICKS) {
                LAST_TAP.remove(p.getUuid());
                fireLaser(p);
            } else {
                LAST_TAP.put(p.getUuid(), t);
            }
        }
    }

    public static void fireLaser(ServerPlayerEntity p) {
        ServerWorld w = p.getEntityWorld();
        long now = w.getTime();
        Long last = LASER_CD.get(p.getUuid());
        if (last != null && now - last < LASER_COOLDOWN) {
            long left = (LASER_COOLDOWN - (now - last) + 19) / 20;
            p.sendMessage(Text.literal("Laser recarregando: " + left + "s"), true);
            return;
        }
        LASER_CD.put(p.getUuid(), now);

        Vec3d eye = p.getEyePos();
        Vec3d dir = p.getRotationVec(1.0f);
        Vec3d end = eye.add(dir.multiply(LASER_RANGE));
        BlockHitResult hit = w.raycast(new RaycastContext(eye, end,
            RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, p));
        Vec3d impact = hit.getType() == HitResult.Type.MISS ? end : hit.getPos();
        double dist = eye.distanceTo(impact);

        // feixe
        for (double d = 2.0; d <= dist; d += 0.5) {
            Vec3d pt = eye.add(dir.multiply(d));
            w.spawnParticles(ParticleTypes.END_ROD, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
            carve(w, BlockPos.ofFloored(pt), BEAM_RADIUS);
        }

        // impacto
        carve(w, BlockPos.ofFloored(impact), NUKE_RADIUS);
        w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, impact.x, impact.y, impact.z, 3, 2, 2, 2, 0);
        w.createExplosion(p, impact.x, impact.y, impact.z, 6.0f, World.ExplosionSourceType.NONE);
    }

    /** Remove blocos numa esfera (exceto os inquebráveis, como bedrock). */
    private static int carve(ServerWorld w, BlockPos center, int r) {
        int count = 0;
        int r2 = r * r;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + y * y + z * z > r2) continue;
                    m.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (!w.isChunkLoaded(m)) continue;
                    BlockState s = w.getBlockState(m);
                    if (s.isAir() || s.getHardness(w, m) < 0) continue;
                    w.setBlockState(m, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
                    count++;
                }
            }
        }
        return count;
    }

    // ------------------------------------------------------------- Deusa: voo e telecinese

    private static final double TK_RANGE = 30;   // alcance para agarrar
    private static final double TK_HOLD = 4.0;   // distância em que o alvo fica à sua frente
    private static final double TK_THROW = 2.5;  // força do arremesso

    private static final Map<UUID, UUID> GRABBED = new HashMap<>();

    private static void enableFlight(ServerPlayerEntity p) {
        GameMode gm = p.interactionManager.getGameMode();
        if ((gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE) && !p.getAbilities().allowFlying) {
            p.getAbilities().allowFlying = true;
            p.sendAbilitiesUpdate();
        }
    }

    /**
     * Botão direito com o cajado (graveto):
     * sem alvo agarrado = agarra a criatura que você olha; com alvo = arremessa
     * (agachado = só solta).
     */
    public static ActionResult wandUse(ServerPlayerEntity p) {
        ServerWorld w = p.getEntityWorld();
        UUID heldId = GRABBED.get(p.getUuid());
        Entity held = heldId == null ? null : w.getEntity(heldId);
        if (held != null && held.isAlive()) {
            GRABBED.remove(p.getUuid());
            if (p.isSneaking()) {
                p.sendMessage(Text.literal("Solto."), true);
            } else {
                held.setVelocity(p.getRotationVec(1.0f).multiply(TK_THROW));
                held.velocityModified = true;
                if (held instanceof ServerPlayerEntity tp) {
                    tp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(tp));
                }
                p.sendMessage(Text.literal("Arremessado!"), true);
            }
            return ActionResult.SUCCESS;
        }

        GRABBED.remove(p.getUuid());
        Vec3d eye = p.getEyePos();
        Vec3d dir = p.getRotationVec(1.0f);
        Box box = p.getBoundingBox().stretch(dir.multiply(TK_RANGE)).expand(2.0);
        Entity best = null;
        double bestDot = 0.96; // cone de ~16 graus
        for (Entity x : w.getOtherEntities(p, box, c -> c instanceof LivingEntity && c.isAlive())) {
            Vec3d to = x.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist > TK_RANGE || dist < 0.5) continue;
            double dot = to.normalize().dotProduct(dir);
            if (dot > bestDot) {
                bestDot = dot;
                best = x;
            }
        }
        if (best == null) {
            p.sendMessage(Text.literal("Nada na mira."), true);
            return ActionResult.SUCCESS;
        }
        GRABBED.put(p.getUuid(), best.getUuid());
        p.sendMessage(Text.literal("Telecinese: botão direito arremessa, Shift + botão direito solta."), true);
        return ActionResult.SUCCESS;
    }

    private static void telekinesisTick(ServerPlayerEntity p) {
        UUID heldId = GRABBED.get(p.getUuid());
        if (heldId == null) return;
        Entity target = p.getEntityWorld().getEntity(heldId);
        if (target == null || !target.isAlive() || target.distanceTo(p) > TK_RANGE + 10) {
            GRABBED.remove(p.getUuid());
            return;
        }
        Vec3d goal = p.getEyePos().add(p.getRotationVec(1.0f).multiply(TK_HOLD));
        double nx = target.getX() + (goal.x - target.getX()) * 0.4;
        double ny = target.getY() + (goal.y - target.getY()) * 0.4;
        double nz = target.getZ() + (goal.z - target.getZ()) * 0.4;
        target.requestTeleport(nx, ny, nz);
        target.setVelocity(Vec3d.ZERO);
        target.fallDistance = 0;
    }
}
