package com.bondrewd.entity;

import com.bondrewd.effect.ModEffects;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Bondrewd, the Lord of Dawn: a three-phase boss with a telegraphed moveset.
 *
 *  GRAB     Umbra Hand Grasp : glowing mark, then a shadow hand drags you to him
 *  SLAM     Umbra Hand Slam  : hands erupt from the ground at marked circles (more circles each phase)
 *  PULSE    Prayer Pulse     : expanding shockwave ring around him (jump it or get launched)
 *  COMPASS  Star Compass     : charges, LOCKS aim (dodge window), then fires a piercing beam
 *  STEP     Shadow Step      : vanishes, reappears behind you, strikes, and chains into PULSE/SLAM
 *
 *  Phase 2 (<50% HP) and Phase 3 (<25% HP) trigger a Prayer: buffs, shockwave, faster moves.
 */
public class BondrewdEntity extends HostileEntity {

    public enum Move {
        NONE(0, 0, 0),
        GRAB(22, 0, 100),
        SLAM(36, 2, 120),
        PULSE(26, 14, 140),
        COMPASS(46, 16, 200),
        STEP(14, 2, 90);

        public final int windup, active, cooldown;
        Move(int windup, int active, int cooldown) {
            this.windup = windup; this.active = active; this.cooldown = cooldown;
        }
        public static Move byId(int id) {
            Move[] v = values();
            return id >= 0 && id < v.length ? v[id] : NONE;
        }
    }

    private static final TrackedData<Integer> MOVE = DataTracker.registerData(BondrewdEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> PHASE = DataTracker.registerData(BondrewdEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private final ServerBossBar bossBar = new ServerBossBar(
            Text.literal("Bondrewd, the Lord of Dawn"), BossBar.Color.PURPLE, BossBar.Style.NOTCHED_10);

    private Move currentMove = Move.NONE;
    private int moveTick = 0;
    private int globalCooldown = 60;
    private final int[] cooldowns = new int[Move.values().length];
    private final Set<UUID> hitSet = new HashSet<>();
    private final List<Vec3d> slamPoints = new ArrayList<>();
    private Vec3d aimDir = new Vec3d(0, 0, 1);
    private Move chain = Move.NONE;
    private boolean forceAttack = false;
    private int phase = 1;

    public BondrewdEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 100;
    }

    public static DefaultAttributeContainer.Builder createBondrewdAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 240.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 8.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0)
                .add(EntityAttributes.GENERIC_ARMOR, 8.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(MOVE, 0);
        dataTracker.startTracking(PHASE, 1);
    }

    public Move getMove() { return Move.byId(dataTracker.get(MOVE)); }
    public int getPhase() { return dataTracker.get(PHASE); }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.1, false));
        this.goalSelector.add(7, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 16.0f));
        this.goalSelector.add(8, new LookAroundGoal(this));
        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    /** No ordinary swings while a move is being performed (Shadow Step forces its own). */
    @Override
    public boolean tryAttack(Entity target) {
        if (currentMove != Move.NONE && !forceAttack) return false;
        return super.tryAttack(target);
    }

    // =====================================================================
    //  Brain
    // =====================================================================
    @Override
    protected void mobTick() {
        super.mobTick();
        bossBar.setPercent(getHealth() / getMaxHealth());

        for (int i = 0; i < cooldowns.length; i++) if (cooldowns[i] > 0) cooldowns[i]--;
        if (globalCooldown > 0) globalCooldown--;

        // Curse aura
        if (age % 40 == 0) {
            for (PlayerEntity p : getWorld().getEntitiesByClass(PlayerEntity.class,
                    getBoundingBox().expand(12), pl -> !pl.isCreative() && !pl.isSpectator())) {
                p.addStatusEffect(new StatusEffectInstance(ModEffects.ABYSS_CURSE, 120, phase >= 3 ? 1 : 0));
            }
        }

        // Phase transitions
        int newPhase = getHealth() > getMaxHealth() * 0.5f ? 1 : getHealth() > getMaxHealth() * 0.25f ? 2 : 3;
        if (newPhase > phase) {
            phase = newPhase;
            onPhaseChange();
        }

        LivingEntity target = getTarget();
        if (currentMove != Move.NONE) {
            if ((target == null || !target.isAlive()) && currentMove != Move.PULSE) {
                endMove();
            } else {
                tickMove(target);
            }
        } else if (target != null && target.isAlive() && globalCooldown <= 0) {
            Move m = pickMove(target);
            if (m != Move.NONE) startMove(m);
        }
    }

    private float phaseMult() { return phase == 1 ? 1.0f : phase == 2 ? 0.75f : 0.55f; }

    private Move pickMove(LivingEntity t) {
        double d = distanceTo(t);
        boolean los = canSee(t);
        List<Move> opts = new ArrayList<>();
        for (Move m : Move.values()) {
            if (m == Move.NONE || cooldowns[m.ordinal()] > 0) continue;
            if (m == Move.GRAB && d > 5 && d < 18 && los) opts.add(m);
            else if (m == Move.SLAM && d < 20 && los) { opts.add(m); opts.add(m); }
            else if (m == Move.PULSE && d < 8) { opts.add(m); opts.add(m); }
            else if (m == Move.COMPASS && d > 6 && d < 28 && los) opts.add(m);
            else if (m == Move.STEP && d > 6 && d < 24) opts.add(m);
        }
        return opts.isEmpty() ? Move.NONE : opts.get(random.nextInt(opts.size()));
    }

    private void startMove(Move m) {
        currentMove = m;
        moveTick = 0;
        hitSet.clear();
        dataTracker.set(MOVE, m.ordinal());
        LivingEntity t = getTarget();
        switch (m) {
            case GRAB -> sound(SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, 1.2f, 0.5f);
            case SLAM -> {
                slamPoints.clear();
                if (t != null) {
                    slamPoints.add(t.getPos());
                    int extra = phase >= 3 ? 4 : phase == 2 ? 2 : 0;
                    for (int i = 0; i < extra; i++) {
                        double a = random.nextDouble() * Math.PI * 2;
                        double r = 3.5 + random.nextDouble() * 3;
                        slamPoints.add(t.getPos().add(Math.cos(a) * r, 0, Math.sin(a) * r));
                    }
                }
                sound(SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, 1.2f, 0.6f);
            }
            case PULSE -> sound(SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 1.0f, 1.2f);
            case COMPASS -> {
                aimDir = t != null ? aimAt(t) : getRotationVector();
                sound(SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, 1.3f, 0.7f);
            }
            default -> {}
        }
    }

    private void tickMove(LivingEntity t) {
        Move m = currentMove;
        getNavigation().stop();
        if (t != null) getLookControl().lookAt(t, 30f, 30f);

        if (moveTick < m.windup) telegraph(m, t);
        else if (moveTick == m.windup) execute(m, t);
        else sustain(m, t, moveTick - m.windup);

        moveTick++;
        if (moveTick > m.windup + m.active) endMove();
    }

    private void endMove() {
        Move ended = currentMove;
        cooldowns[ended.ordinal()] = (int) (ended.cooldown * phaseMult());
        globalCooldown = (int) (35 * phaseMult());
        currentMove = Move.NONE;
        dataTracker.set(MOVE, 0);

        if (chain != Move.NONE) {               // combo follow-up
            Move next = chain;
            chain = Move.NONE;
            LivingEntity t = getTarget();
            if (t != null && t.isAlive()) { startMove(next); }
        }
    }

    // =====================================================================
    //  Telegraphs (the "tell" before each move: this is what makes it dodgeable)
    // =====================================================================
    private void telegraph(Move m, LivingEntity t) {
        float p = moveTick / (float) Math.max(m.windup, 1);
        switch (m) {
            case GRAB -> {
                double a = moveTick * 0.9;
                for (int k = 0; k < 2; k++) {
                    double aa = a + k * Math.PI;
                    fx(ParticleTypes.SQUID_INK, getX() + Math.cos(aa) * 0.9, getY() + 1.0 + p * 0.8, getZ() + Math.sin(aa) * 0.9, 1, 0, 0, 0, 0);
                }
                if (moveTick == 2) t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, m.windup + 6, 0, false, false));
                if (moveTick % 4 == 0) line(getEyePos(), t.getPos().add(0, t.getHeight() / 2, 0), ParticleTypes.SMOKE, 0.8);
            }
            case SLAM -> {
                int lockAt = m.windup - 12;
                if (moveTick < lockAt && !slamPoints.isEmpty()) slamPoints.set(0, t.getPos());  // first circle tracks you
                boolean locked = moveTick >= lockAt;
                for (Vec3d c : slamPoints) {
                    if (moveTick % 2 == 0) {
                        for (int i = 0; i < 14; i++) {
                            double a = i * Math.PI * 2 / 14;
                            fx(locked ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SMOKE,
                                    c.x + Math.cos(a) * 3, c.y + 0.1, c.z + Math.sin(a) * 3, 1, 0, 0, 0, 0);
                        }
                    }
                    if (locked) fx(ParticleTypes.SQUID_INK, c.x, c.y + 0.2 + p * 2, c.z, 3, 0.4, 0.3, 0.4, 0.01);
                }
            }
            case PULSE -> {
                double r = 4 * (1 - p) + 0.5;
                for (int i = 0; i < 10; i++) {
                    double a = i * Math.PI * 2 / 10 + moveTick * 0.3;
                    fx(ParticleTypes.SOUL_FIRE_FLAME, getX() + Math.cos(a) * r, getY() + 1, getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
            }
            case COMPASS -> {
                Vec3d eye = getEyePos();
                int lockAt = m.windup - 12;
                if (moveTick < lockAt) aimDir = aimAt(t);          // tracks you, then LOCKS
                double r = 1.5 * (1 - p) + 0.15;
                double a = moveTick * 0.7;
                Vec3d c = eye.add(aimDir.multiply(0.8));
                fx(ParticleTypes.END_ROD, c.x + Math.cos(a) * r, c.y + Math.sin(a) * r, c.z + Math.cos(a + 1.3) * r, 1, 0, 0, 0, 0);
                fx(ParticleTypes.END_ROD, c.x - Math.cos(a) * r, c.y - Math.sin(a) * r, c.z - Math.cos(a + 1.3) * r, 1, 0, 0, 0, 0);
                if (moveTick >= lockAt && moveTick % 2 == 0) {
                    line(eye, beamEnd(eye, aimDir), ParticleTypes.END_ROD, 1.5);   // preview of the locked line
                }
            }
            case STEP -> {
                fx(ParticleTypes.PORTAL, getX(), getY() + 1, getZ(), 6, 0.3, 0.8, 0.3, 0.6);
                if (moveTick == m.windup - 6) addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 10, 0, false, false));
            }
            default -> {}
        }
    }

    // =====================================================================
    //  The moment of impact
    // =====================================================================
    private void execute(Move m, LivingEntity t) {
        switch (m) {
            case GRAB -> {
                if (canSee(t)) {
                    Vec3d to = t.getPos().add(0, t.getHeight() / 2, 0);
                    line(getEyePos(), to, ParticleTypes.SQUID_INK, 0.4);
                    line(getEyePos(), to, ParticleTypes.SOUL_FIRE_FLAME, 1.0);
                    Vec3d d = getPos().subtract(t.getPos());
                    Vec3d pull = new Vec3d(d.x, 0, d.z).normalize().multiply(1.7).add(0, 0.35, 0);
                    t.setVelocity(pull);
                    t.velocityModified = true;
                    t.damage(getDamageSources().mobAttack(this), 4 + phase);
                    curse(t, 200, phase >= 2 ? 1 : 0);
                    fx(ParticleTypes.SQUID_INK, t.getX(), t.getBodyY(0.5), t.getZ(), 20, 0.3, 0.5, 0.3, 0.05);
                    sound(SoundEvents.ENTITY_EVOKER_CAST_SPELL, 1.2f, 0.5f);
                }
            }
            case SLAM -> {
                for (Vec3d c : slamPoints) {
                    Box b = new Box(c.x - 3.2, c.y - 1, c.z - 3.2, c.x + 3.2, c.y + 3, c.z + 3.2);
                    for (LivingEntity e : getWorld().getEntitiesByClass(LivingEntity.class, b, this::canHit)) {
                        e.damage(getDamageSources().mobAttack(this), 8 + phase * 2);
                        e.setVelocity(e.getVelocity().add(0, 0.9, 0));
                        e.velocityModified = true;
                        curse(e, 200, 1);
                    }
                    fx(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
                    fx(ParticleTypes.SQUID_INK, c.x, c.y + 0.3, c.z, 60, 1.2, 0.2, 1.2, 0.25);
                    fx(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y + 0.3, c.z, 30, 0.8, 1.5, 0.8, 0.1);
                    for (int i = 0; i < 12; i++) fx(ParticleTypes.SQUID_INK, c.x, c.y + i * 0.35, c.z, 4, 0.25, 0.05, 0.25, 0.02);
                    getWorld().playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 0.8f, 0.6f);
                }
            }
            case PULSE -> {
                fx(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.5, getZ(), 1, 0, 0, 0, 0);
                sound(SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.6f);
            }
            case COMPASS -> sound(SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.7f);
            case STEP -> {
                Vec3d old = getPos();
                fx(ParticleTypes.PORTAL, old.x, old.y + 1, old.z, 60, 0.4, 1, 0.4, 0.8);
                Vec3d f = t.getRotationVec(1f);
                Vec3d flat = new Vec3d(f.x, 0, f.z);
                if (flat.lengthSquared() < 1e-4) flat = new Vec3d(0, 0, 1);
                flat = flat.normalize();
                Vec3d dest = t.getPos().subtract(flat.multiply(1.8));
                boolean ok = false;
                for (int dy = 0; dy <= 2 && !ok; dy++) {
                    Vec3d d2 = new Vec3d(dest.x, dest.y + dy, dest.z);
                    if (getWorld().isSpaceEmpty(this, getBoundingBox().offset(d2.subtract(getPos())))) {
                        refreshPositionAndAngles(d2.x, d2.y, d2.z, getYaw(), getPitch());
                        ok = true;
                    }
                }
                removeStatusEffect(StatusEffects.INVISIBILITY);
                if (ok) {
                    float yaw = (float) (MathHelper.atan2(t.getZ() - getZ(), t.getX() - getX()) * 180.0 / Math.PI) - 90f;
                    setYaw(yaw); setHeadYaw(yaw); setBodyYaw(yaw);
                    getNavigation().stop();
                    fx(ParticleTypes.PORTAL, getX(), getY() + 1, getZ(), 60, 0.4, 1, 0.4, 0.8);
                    fx(ParticleTypes.SQUID_INK, getX(), getY() + 1, getZ(), 25, 0.5, 0.8, 0.5, 0.05);
                    sound(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.2f, 0.5f);
                    forceAttack = true;
                    tryAttack(t);
                    forceAttack = false;
                    curse(t, 200, 1);
                    chain = random.nextFloat() < 0.65f ? Move.PULSE : Move.SLAM;
                }
            }
            default -> {}
        }
    }

    /** Ticks after impact (for moves with an "active" window). */
    private void sustain(Move m, LivingEntity t, int s) {
        switch (m) {
            case PULSE -> {
                double r = 1 + s * 0.9;
                int n = (int) (r * 5) + 8;
                for (int i = 0; i < n; i++) {
                    double a = i * Math.PI * 2 / n;
                    double px = getX() + Math.cos(a) * r, pz = getZ() + Math.sin(a) * r;
                    fx(ParticleTypes.SOUL_FIRE_FLAME, px, getY() + 0.15, pz, 1, 0, 0.05, 0, 0.01);
                    if (i % 2 == 0) fx(ParticleTypes.SQUID_INK, px, getY() + 0.5, pz, 1, 0, 0.3, 0, 0.02);
                }
                for (LivingEntity e : getWorld().getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(r + 2), this::canHit)) {
                    if (hitSet.contains(e.getUuid())) continue;
                    double dx = e.getX() - getX(), dz = e.getZ() - getZ();
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (Math.abs(d - r) < 1.4 && Math.abs(e.getY() - getY()) < 3) {   // jump to dodge!
                        hitSet.add(e.getUuid());
                        e.damage(getDamageSources().mobAttack(this), 6 + phase);
                        e.setVelocity(new Vec3d(dx, 0, dz).normalize().multiply(1.5).add(0, 0.5, 0));
                        e.velocityModified = true;
                        curse(e, 160, 1);
                    }
                }
            }
            case COMPASS -> {
                Vec3d eye = getEyePos();
                Vec3d end = beamEnd(eye, aimDir);
                line(eye, end, ParticleTypes.END_ROD, 0.5);
                line(eye, end, ParticleTypes.SOUL_FIRE_FLAME, 1.2);
                line(eye, end, ParticleTypes.SQUID_INK, 1.5);
                fx(ParticleTypes.END_ROD, end.x, end.y, end.z, 6, 0.2, 0.2, 0.2, 0.1);
                if (s % 4 == 1) {
                    Box box = new Box(eye, end).expand(1.5);
                    for (LivingEntity e : getWorld().getEntitiesByClass(LivingEntity.class, box, this::canHit)) {
                        Vec3d c = e.getPos().add(0, e.getHeight() / 2, 0);
                        if (distToSegment(c, eye, end) < 1.4 + e.getWidth() / 2) {
                            e.damage(getDamageSources().indirectMagic(this, this), 10 + phase * 2);
                            e.setVelocity(e.getVelocity().add(aimDir.multiply(0.5)).add(0, 0.15, 0));
                            e.velocityModified = true;
                            curse(e, 240, 1);
                        }
                    }
                }
            }
            default -> {}
        }
    }

    // =====================================================================
    //  Phases
    // =====================================================================
    private void onPhaseChange() {
        dataTracker.set(PHASE, phase);
        applyPhaseVisuals();
        if (phase == 2) {
            addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 1));
            addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 200, 1));
            announce("Bondrewd offers a prayer to the abyss...");
        } else {
            addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 400, 2));
            addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 400, 1));
            heal(30f);
            announce("The Lord of Dawn begins his final prayer...");
        }
        fx(ParticleTypes.PORTAL, getX(), getY() + 1, getZ(), 100, 0.5, 1, 0.5, 0.8);
        sound(SoundEvents.BLOCK_END_PORTAL_SPAWN, 0.6f, 0.5f);
        chain = Move.NONE;
        startMove(Move.PULSE);   // prayer shockwave
    }

    private void applyPhaseVisuals() {
        bossBar.setColor(phase >= 3 ? BossBar.Color.RED : phase == 2 ? BossBar.Color.PINK : BossBar.Color.PURPLE);
    }

    private void announce(String msg) {
        for (PlayerEntity p : getWorld().getEntitiesByClass(PlayerEntity.class, getBoundingBox().expand(40), pl -> true)) {
            p.sendMessage(Text.literal(msg).formatted(Formatting.DARK_PURPLE), true);
        }
    }

    // =====================================================================
    //  Client-side ambience: shadowy wisps, hotter in later phases
    // =====================================================================
    @Override
    public void tickMovement() {
        super.tickMovement();
        if (getWorld().isClient) {
            int ph = getPhase();
            if (random.nextInt(2) == 0) {
                getWorld().addParticle(ph >= 3 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SMOKE,
                        getX() + (random.nextDouble() - 0.5) * 0.9, getY() + random.nextDouble() * 2.0,
                        getZ() + (random.nextDouble() - 0.5) * 0.9, 0, 0.02, 0);
            }
            if (ph >= 2 && random.nextInt(4) == 0) {
                getWorld().addParticle(ParticleTypes.PORTAL, getX(), getY() + 1.0, getZ(),
                        (random.nextDouble() - 0.5), random.nextDouble() * 0.5, (random.nextDouble() - 0.5));
            }
        }
    }

    // =====================================================================
    //  Helpers
    // =====================================================================
    private Vec3d aimAt(LivingEntity t) {
        return t.getPos().add(0, t.getHeight() * 0.6, 0).subtract(getEyePos()).normalize();
    }

    private Vec3d beamEnd(Vec3d eye, Vec3d dir) {
        Vec3d end = eye.add(dir.multiply(28));
        var hit = getWorld().raycast(new RaycastContext(eye, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getPos();
    }

    private static double distToSegment(Vec3d p, Vec3d a, Vec3d b) {
        Vec3d ab = b.subtract(a);
        double len2 = ab.lengthSquared();
        double t = len2 == 0 ? 0 : MathHelper.clamp(p.subtract(a).dotProduct(ab) / len2, 0, 1);
        return p.distanceTo(a.add(ab.multiply(t)));
    }

    private void line(Vec3d a, Vec3d b, ParticleEffect type, double step) {
        int n = (int) (a.distanceTo(b) / step);
        for (int i = 0; i <= n; i++) {
            Vec3d p = a.lerp(b, i / (double) Math.max(n, 1));
            fx(type, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0);
        }
    }

    private void fx(ParticleEffect type, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        ((ServerWorld) getWorld()).spawnParticles(type, x, y, z, count, dx, dy, dz, speed);
    }

    private void sound(SoundEvent s, float vol, float pitch) {
        getWorld().playSound(null, getBlockPos(), s, SoundCategory.HOSTILE, vol, pitch);
    }

    private boolean canHit(LivingEntity e) {
        if (e == this || e instanceof BondrewdEntity || !e.isAlive()) return false;
        return !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator()));
    }

    private void curse(LivingEntity e, int dur, int amp) {
        e.addStatusEffect(new StatusEffectInstance(ModEffects.ABYSS_CURSE, dur, amp));
    }

    // =====================================================================
    //  Boss bar / persistence / sounds
    // =====================================================================
    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player) {
        super.onStartedTrackingBy(player);
        bossBar.addPlayer(player);
    }

    @Override
    public void onStoppedTrackingBy(ServerPlayerEntity player) {
        super.onStoppedTrackingBy(player);
        bossBar.removePlayer(player);
    }

    @Override
    public void setCustomName(Text name) {
        super.setCustomName(name);
        bossBar.setName(getDisplayName());
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) { return false; }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Phase", phase);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        phase = Math.max(1, nbt.getInt("Phase"));
        dataTracker.set(PHASE, phase);
        applyPhaseVisuals();
        if (hasCustomName()) bossBar.setName(getDisplayName());
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.ENTITY_PLAYER_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.ENTITY_PLAYER_DEATH; }
}
