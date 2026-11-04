package com.bondrewd.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;

/** Ticks once a second: drains health, hunger, and (at level II+) clouds the vision. */
public class AbyssCurseEffect extends StatusEffect {
    public AbyssCurseEffect() {
        super(StatusEffectCategory.HARMFUL, 0x2a1245);
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public void applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (entity.getWorld().isClient) return;
        // Never kills outright, only wears down (like a curse "to the brink")
        if (entity.getHealth() > 2.0f) {
            entity.damage(entity.getDamageSources().magic(), 1.0f + amplifier * 0.5f);
        }
        if (entity instanceof PlayerEntity player) {
            player.addExhaustion(0.6f + amplifier * 0.3f);
        }
        if (amplifier >= 1) {
            entity.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0, false, false));
        }
        entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 0, false, false));
    }
}
