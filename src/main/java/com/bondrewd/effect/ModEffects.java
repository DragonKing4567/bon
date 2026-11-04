package com.bondrewd.effect;

import com.bondrewd.BondrewdMod;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModEffects {
    public static final StatusEffect ABYSS_CURSE = Registry.register(
            Registries.STATUS_EFFECT, new Identifier(BondrewdMod.MOD_ID, "abyss_curse"), new AbyssCurseEffect());

    public static void register() {}
}
