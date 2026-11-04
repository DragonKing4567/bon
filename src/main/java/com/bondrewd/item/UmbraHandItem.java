package com.bondrewd.item;

import com.bondrewd.effect.ModEffects;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/**
 * Two moves:
 *  - Right-click:        Umbra Grasp: seize the target in your crosshairs and drag it to you.
 *  - Sneak + right-click: Umbra Slam : shadow hands erupt where you're looking (20 blocks).
 */
public class UmbraHandItem extends Item {
    private static final double RANGE = 16.0;

    public UmbraHandItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.pass(stack);
        return user.isSneaking() ? slam(world, user, stack, hand) : grasp(world, user, stack, hand);
    }

    private TypedActionResult<ItemStack> grasp(World world, PlayerEntity user, ItemStack stack, Hand hand) {
        Vec3d start = user.getEyePos();
        Vec3d look = user.getRotationVec(1.0f);
        Vec3d end = start.add(look.multiply(RANGE));
        Box box = user.getBoundingBox().stretch(look.multiply(RANGE)).expand(1.0);
        EntityHitResult hit = ProjectileUtil.raycast(user, start, end, box,
                e -> e instanceof LivingEntity && !e.isSpectator() && e.isAttackable(), RANGE * RANGE);
        if (hit == null) return TypedActionResult.fail(stack);

        Entity target = hit.getEntity();
        ServerWorld sw = (ServerWorld) world;
        Vec3d to = target.getPos().add(0, target.getHeight() / 2, 0);
        int steps = (int) start.distanceTo(to) * 2;
        for (int i = 0; i <= steps; i++) {
            Vec3d p = start.lerp(to, i / (double) Math.max(steps, 1));
            sw.spawnParticles(ParticleTypes.SQUID_INK, p.x, p.y - 0.2, p.z, 1, 0.05, 0.05, 0.05, 0.0);
            if (i % 3 == 0) sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y - 0.2, p.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
        Vec3d pull = user.getPos().subtract(target.getPos()).normalize().multiply(1.6).add(0, 0.35, 0);
        target.setVelocity(pull);
        target.velocityModified = true;
        target.damage(user.getDamageSources().playerAttack(user), 5.0f);
        if (target instanceof LivingEntity living) {
            living.addStatusEffect(new StatusEffectInstance(ModEffects.ABYSS_CURSE, 160, 0));
        }
        world.playSound(null, user.getBlockPos(), SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.PLAYERS, 1.0f, 0.6f);
        user.getItemCooldownManager().set(this, 50);
        stack.damage(1, user, p -> p.sendToolBreakStatus(hand));
        return TypedActionResult.success(stack, false);
    }

    private TypedActionResult<ItemStack> slam(World world, PlayerEntity user, ItemStack stack, Hand hand) {
        HitResult hr = user.raycast(20.0, 1.0f, false);
        Vec3d c = hr.getPos();
        ServerWorld sw = (ServerWorld) world;

        Box b = new Box(c.x - 3, c.y - 1, c.z - 3, c.x + 3, c.y + 3, c.z + 3);
        for (LivingEntity t : world.getEntitiesByClass(LivingEntity.class, b, x -> x != user && x.isAlive())) {
            t.damage(user.getDamageSources().playerAttack(user), 7.0f);
            t.setVelocity(t.getVelocity().add(0, 0.8, 0));
            t.velocityModified = true;
            t.addStatusEffect(new StatusEffectInstance(ModEffects.ABYSS_CURSE, 160, 0));
        }
        sw.spawnParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
        sw.spawnParticles(ParticleTypes.SQUID_INK, c.x, c.y + 0.3, c.z, 50, 1.0, 0.2, 1.0, 0.2);
        sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y + 0.3, c.z, 25, 0.7, 1.2, 0.7, 0.1);
        for (int i = 0; i < 10; i++) sw.spawnParticles(ParticleTypes.SQUID_INK, c.x, c.y + i * 0.35, c.z, 4, 0.25, 0.05, 0.25, 0.02);
        world.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.7f, 0.6f);

        user.getItemCooldownManager().set(this, 80);
        stack.damage(2, user, p -> p.sendToolBreakStatus(hand));
        return TypedActionResult.success(stack, false);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("Right-click: Umbra Grasp (pull a target)").formatted(Formatting.DARK_PURPLE));
        tooltip.add(Text.literal("Sneak + right-click: Umbra Slam (AoE at crosshair)").formatted(Formatting.DARK_PURPLE));
        tooltip.add(Text.literal("Inflicts Curse of the Abyss").formatted(Formatting.GRAY));
    }
}
