package com.bondrewd.item;

import com.bondrewd.effect.ModEffects;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/** Consumable that lifts the Abyss Curse and grants a short burst of protection. */
public class DawnCartridgeItem extends Item {
    public DawnCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!world.isClient) {
            player.removeStatusEffect(ModEffects.ABYSS_CURSE);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 400, 0));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 0));
            world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.0f, 1.5f);
        }
        player.getItemCooldownManager().set(this, 100);
        if (!player.getAbilities().creativeMode) stack.decrement(1);
        return TypedActionResult.success(stack, world.isClient());
    }
}
