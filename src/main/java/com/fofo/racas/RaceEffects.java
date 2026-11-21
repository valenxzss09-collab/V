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
import net.minecraft.entity.projectile.ProjectileEntity;
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
        currentTick = t;
        runScheduled(t);

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
            if (r == Race.O) {
                immortalTick(p);
                shieldTick(p);
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
            case O -> {
                want(p, StatusEffects.WATER_BREATHING, 0, true);
                want(p, StatusEffects.FIRE_RESISTANCE, 0, true);
                want(p, StatusEffects.DOLPHINS_GRACE, 0, water);
                HungerManager hm = p.getHungerManager();
                hm.setFoodLevel(20);
                hm.setSaturationLevel(20.0f);
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

        if (old == Race.O) {
            SHIELD_UNTIL.remove(p.getUuid());
            GRABBED.remove(p.getUuid());
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
                    case O -> {
                        return -1f; // imortal: cancela qualquer dano
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

    // =========================================================== Raça O
    // Imortal + Cetro do O (vara de blaze). Botão direito = usa o poder atual,
    // Shift + botão direito = troca de poder.

    private enum OPower {
        EXPLOSAO("Explosão", 400),
        CONGELAR("Congelar", 200),
        FOGO("Fogo", 100),
        TELECINESE("Telecinese", 0),
        ESCUDO("Escudo", 100),
        AGUA("Dobrar Água", 100);

        final String label;
        final long cooldown; // ticks

        OPower(String label, long cooldown) {
            this.label = label;
            this.cooldown = cooldown;
        }
    }

    // ---- constantes (mexa aqui para balancear) ----
    private static final int EXPL_STEPS = 40;          // quantas explosões na linha
    private static final double EXPL_START = 3.0;      // distância da 1ª explosão
    private static final double EXPL_SPACING = 3.0;    // distância entre explosões (40 x 3 = 120 blocos)
    private static final float EXPL_POWER = 5.0f;      // força de cada explosão
    private static final int EXPL_CARVE = 4;           // raio de blocos destruídos em cada uma
    private static final float EXPL_FINAL_POWER = 12.0f; // explosão final
    private static final int EXPL_FINAL_CARVE = 12;
    private static final double FREEZE_RADIUS = 20;    // alcance do congelamento
    private static final int FREEZE_TICKS = 200;       // 10 s
    private static final int ICE_RADIUS = 10;          // água vira gelo em volta
    private static final int FIRE_TICKS = 40;          // 2 s de lança-chamas
    private static final double FIRE_RANGE = 28;
    private static final int WATER_TICKS = 40;         // 2 s de jato d'água
    private static final double WATER_RANGE = 32;
    private static final double SHIELD_RADIUS = 5.0;
    private static final long SHIELD_DURATION = 300;   // 15 s

    private static final Map<UUID, Integer> O_MODE = new HashMap<>();
    private static final Map<String, Long> O_CD = new HashMap<>();
    private static final Map<UUID, Long> SHIELD_UNTIL = new HashMap<>();

    // ---- agendador simples (executa tarefas daqui a N ticks) ----
    private record Scheduled(int at, Runnable task) {}
    private static final List<Scheduled> SCHEDULED = new ArrayList<>();
    private static int currentTick = 0;

    private static void schedule(int delay, Runnable r) {
        SCHEDULED.add(new Scheduled(currentTick + delay, r));
    }

    private static void runScheduled(int t) {
        if (SCHEDULED.isEmpty()) return;
        List<Scheduled> due = new ArrayList<>();
        SCHEDULED.removeIf(s -> {
            if (s.at() <= t) {
                due.add(s);
                return true;
            }
            return false;
        });
        for (Scheduled s : due) {
            try {
                s.task().run();
            } catch (Exception ex) {
                RacasMod.LOGGER.error("Erro em tarefa agendada", ex);
            }
        }
    }

    // ---- imortalidade ----
    private static void immortalTick(ServerPlayerEntity p) {
        if (!p.isAlive() || p.isSpectator()) return;
        ServerWorld w = p.getEntityWorld();
        if (p.getHealth() < p.getMaxHealth()) p.setHealth(p.getMaxHealth());
        p.setAir(p.getMaxAir());
        p.fallDistance = 0;
        if (p.isOnFire()) p.extinguish();
        // caiu no void: volta pro alto do mundo (o dano já é cancelado)
        if (p.getY() < w.getBottomY() - 16) {
            p.setVelocity(Vec3d.ZERO);
            p.requestTeleport(p.getX(), w.getBottomY() + w.getHeight() - 2, p.getZ());
        }
    }

    public static int oMode(UUID id) {
        return O_MODE.getOrDefault(id, 0);
    }

    // ---- uso do cetro ----
    public static ActionResult oWandUse(ServerPlayerEntity p) {
        ServerWorld w = p.getEntityWorld();
        OPower[] powers = OPower.values();
        int idx = O_MODE.getOrDefault(p.getUuid(), 0);

        if (p.isSneaking()) {
            GRABBED.remove(p.getUuid());
            idx = (idx + 1) % powers.length;
            O_MODE.put(p.getUuid(), idx);
            RaceSync.send(p);
            p.sendMessage(Text.literal("Poder: " + powers[idx].label), true);
            return ActionResult.SUCCESS;
        }

        OPower power = powers[idx];
        if (power == OPower.TELECINESE) {
            return wandUse(p); // agarra / arremessa
        }

        long now = w.getTime();
        String key = p.getUuid() + ":" + power.name();
        Long last = O_CD.get(key);
        if (last != null && now - last < power.cooldown) {
            long left = (power.cooldown - (now - last) + 19) / 20;
            p.sendMessage(Text.literal(power.label + " recarregando: " + left + "s"), true);
            return ActionResult.SUCCESS;
        }
        O_CD.put(key, now);

        switch (power) {
            case EXPLOSAO -> bigExplosion(p);
            case CONGELAR -> freezeBlast(p);
            case FOGO -> flamethrower(p);
            case ESCUDO -> activateShield(p);
            case AGUA -> waterJet(p);
            default -> { }
        }
        return ActionResult.SUCCESS;
    }

    // ---- 1) explosão gigante e longa ----
    private static void bigExplosion(ServerPlayerEntity p) {
        ServerWorld w = p.getEntityWorld();
        Vec3d eye = p.getEyePos();
        Vec3d dir = p.getRotationVec(1.0f);
        p.sendMessage(Text.literal("EXPLOSÃO!"), true);
        for (int i = 1; i <= EXPL_STEPS; i++) {
            final int step = i;
            schedule(step, () -> {
                Vec3d c = eye.add(dir.multiply(EXPL_START + step * EXPL_SPACING));
                explodeAt(w, p, c, EXPL_POWER, EXPL_CARVE);
            });
        }
        schedule(EXPL_STEPS + 5, () -> {
            Vec3d c = eye.add(dir.multiply(EXPL_START + EXPL_STEPS * EXPL_SPACING));
            explodeAt(w, p, c, EXPL_FINAL_POWER, EXPL_FINAL_CARVE);
        });
    }

    private static void explodeAt(ServerWorld w, Entity source, Vec3d c, float power, int carveRadius) {
        BlockPos bp = BlockPos.ofFloored(c);
        if (!w.isChunkLoaded(bp)) return;
        carve(w, bp, carveRadius);
        w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0.5, 0.5, 0.5, 0);
        w.createExplosion(source, c.x, c.y, c.z, power, World.ExplosionSourceType.NONE);
    }

    // ---- 2) congelar ----
    private static void freezeBlast(ServerPlayerEntity p) {
        ServerWorld w = p.getEntityWorld();
        p.sendMessage(Text.literal("CONGELAR!"), true);

        Box box = p.getBoundingBox().expand(FREEZE_RADIUS);
        for (Entity e : w.getOtherEntities(p, box, c -> c instanceof LivingEntity && c.isAlive())) {
            if (e.distanceTo(p) > FREEZE_RADIUS) continue;
            LivingEntity le = (LivingEntity) e;
            le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, FREEZE_TICKS, 255, false, true, true));
            le.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, FREEZE_TICKS, 10, false, true, true));
            le.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, FREEZE_TICKS, 10, false, true, true));
            le.setFrozenTicks(300);
        }

        // água em volta vira gelo
        BlockPos c = p.getBlockPos();
        BlockPos.Mutable m = new BlockPos.Mutable();
        int r = ICE_RADIUS;
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + y * y + z * z > r * r) continue;
                    m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
                    if (!w.isChunkLoaded(m)) continue;
                    if (w.getBlockState(m).isOf(Blocks.WATER)) {
                        w.setBlockState(m, Blocks.ICE.getDefaultState(), Block.NOTIFY_ALL);
                    }
                }
            }
        }

        w.spawnParticles(ParticleTypes.SNOWFLAKE, p.getX(), p.getY() + 1.0, p.getZ(),
            400, FREEZE_RADIUS / 3.0, 2.0, FREEZE_RADIUS / 3.0, 0.05);
        w.spawnParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.5, p.getZ(),
            80, FREEZE_RADIUS / 4.0, 0.5, FREEZE_RADIUS / 4.0, 0.02);
    }

    // ---- 3) fogo (lança-chamas) ----
    private static void flamethrower(ServerPlayerEntity p) {
        ServerWorld w = p.getEntityWorld();
        p.sendMessage(Text.literal("FOGO!"), true);
        for (int i = 0; i < FIRE_TICKS; i++) {
            final int k = i;
            schedule(i, () -> flameTick(p, w, k));
        }
    }

    private static void flameTick(ServerPlayerEntity p, ServerWorld w, int k) {
        if (p.isRemoved()) return;
        Vec3d eye = p.getEyePos();
        Vec3d dir = p.getRotationVec(1.0f);
        for (double d = 1.5; d <= FIRE_RANGE; d += 1.0) {
            Vec3d pt = eye.add(dir.multiply(d));
            BlockPos bp = BlockPos.ofFloored(pt);
            if (!w.isChunkLoaded(bp)) break;
            BlockState s = w.getBlockState(bp);
            if (!s.isAir() && !s.isOf(Blocks.FIRE)) {
                igniteAround(w, bp.up());
                break;
            }
            double spread = 0.15 + d * 0.04;
            w.spawnParticles(ParticleTypes.FLAME, pt.x, pt.y, pt.z, 2, spread, spread, spread, 0.01);
            if (k % 8 == 0 && ((int) d) % 3 == 0) igniteGround(w, bp);

            Box b = new Box(pt, pt).expand(1.5 + d * 0.05);
            for (Entity e : w.getOtherEntities(p, b, c -> c instanceof LivingEntity && c.isAlive())) {
                e.setOnFireFor(12);
                if (k % 5 == 0) e.damage(w, w.getDamageSources().playerAttack(p), 3.0f);
            }
        }
    }

    private static void igniteGround(ServerWorld w, BlockPos from) {
        for (int dy = 0; dy <= 3; dy++) {
            BlockPos pos = from.down(dy);
            if (w.getBlockState(pos).isAir() && w.getBlockState(pos.down()).isSolidBlock(w, pos.down())) {
                w.setBlockState(pos, Blocks.FIRE.getDefaultState(), Block.NOTIFY_ALL);
                return;
            }
        }
    }

    private static void igniteAround(ServerWorld w, BlockPos center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = center.add(dx, 0, dz);
                if (w.isChunkLoaded(pos) && w.getBlockState(pos).isAir()
                    && w.getBlockState(pos.down()).isSolidBlock(w, pos.down())) {
                    w.setBlockState(pos, Blocks.FIRE.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
        }
    }

    // ---- 4) escudo ----
    private static void activateShield(ServerPlayerEntity p) {
        SHIELD_UNTIL.put(p.getUuid(), p.getEntityWorld().getTime() + SHIELD_DURATION);
        p.sendMessage(Text.literal("ESCUDO ativado!"), true);
    }

    private static void shieldTick(ServerPlayerEntity p) {
        Long until = SHIELD_UNTIL.get(p.getUuid());
        if (until == null) return;
        ServerWorld w = p.getEntityWorld();
        long now = w.getTime();
        if (now > until) {
            SHIELD_UNTIL.remove(p.getUuid());
            p.sendMessage(Text.literal("Escudo acabou."), true);
            return;
        }
        double cx = p.getX();
        double cy = p.getY() + 1.0;
        double cz = p.getZ();
        double R = SHIELD_RADIUS;

        if (now % 2 == 0) {
            for (int i = 0; i < 20; i++) {
                double dx = p.getRandom().nextGaussian();
                double dy = p.getRandom().nextGaussian();
                double dz = p.getRandom().nextGaussian();
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (len < 1e-4) continue;
                w.spawnParticles(ParticleTypes.END_ROD,
                    cx + dx / len * R, cy + dy / len * R, cz + dz / len * R, 1, 0, 0, 0, 0);
            }
        }

        Box box = p.getBoundingBox().expand(R);
        for (Entity e : w.getOtherEntities(p, box, c -> c.isAlive())) {
            double ex = e.getX() - cx;
            double ey = (e.getY() + e.getHeight() / 2.0) - cy;
            double ez = e.getZ() - cz;
            double d = Math.sqrt(ex * ex + ey * ey + ez * ez);
            if (d > R || d < 1e-4) continue;
            if (e instanceof ProjectileEntity) {
                e.discard();
                continue;
            }
            if (!(e instanceof LivingEntity)) continue;
            double k = 0.9 / d;
            e.setVelocity(ex * k, 0.25, ez * k);
            if (e instanceof ServerPlayerEntity tp) {
                tp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(tp));
            }
        }
    }

    // ---- 5) dobrar água (jato) ----
    private static void waterJet(ServerPlayerEntity p) {
        ServerWorld w = p.getEntityWorld();
        p.sendMessage(Text.literal("ÁGUA!"), true);
        for (int i = 0; i < WATER_TICKS; i++) {
            final int k = i;
            schedule(i, () -> waterTick(p, w, k));
        }
    }

    private static void waterTick(ServerPlayerEntity p, ServerWorld w, int k) {
        if (p.isRemoved()) return;
        Vec3d eye = p.getEyePos();
        Vec3d dir = p.getRotationVec(1.0f);
        BlockPos last = null;
        for (double d = 1.5; d <= WATER_RANGE; d += 1.0) {
            Vec3d pt = eye.add(dir.multiply(d));
            BlockPos bp = BlockPos.ofFloored(pt);
            if (!w.isChunkLoaded(bp)) break;
            BlockState s = w.getBlockState(bp);
            if (!s.isAir() && !s.isOf(Blocks.WATER)) break;
            last = bp;
            double spread = 0.12 + d * 0.03;
            w.spawnParticles(ParticleTypes.SPLASH, pt.x, pt.y, pt.z, 4, spread, spread, spread, 0.1);
            w.spawnParticles(ParticleTypes.FALLING_WATER, pt.x, pt.y, pt.z, 2, spread, spread, spread, 0.0);

            Box b = new Box(pt, pt).expand(1.5 + d * 0.05);
            for (Entity e : w.getOtherEntities(p, b, c -> c instanceof LivingEntity && c.isAlive())) {
                e.setVelocity(dir.x * 1.2, dir.y * 1.2 + 0.15, dir.z * 1.2);
                e.extinguish();
                if (e instanceof ServerPlayerEntity tp) {
                    tp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(tp));
                }
                if (k % 5 == 0) e.damage(w, w.getDamageSources().playerAttack(p), 2.0f);
            }
        }

        // no último tick, deixa uma poça de água que some depois
        if (k == WATER_TICKS - 1 && last != null && w.getBlockState(last).isAir()) {
            final BlockPos pool = last;
            w.setBlockState(pool, Blocks.WATER.getDefaultState(), Block.NOTIFY_ALL);
            schedule(100, () -> {
                if (w.isChunkLoaded(pool) && w.getBlockState(pool).isOf(Blocks.WATER)) {
                    w.setBlockState(pool, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                }
            });
        }
    }
}
