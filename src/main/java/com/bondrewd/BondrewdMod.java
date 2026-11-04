package com.bondrewd;

import com.bondrewd.effect.ModEffects;
import com.bondrewd.entity.ModEntities;
import com.bondrewd.item.ModItems;
import net.fabricmc.api.ModInitializer;

public class BondrewdMod implements ModInitializer {
    public static final String MOD_ID = "bondrewd";

    @Override
    public void onInitialize() {
        ModEffects.register();
        ModItems.register();
        ModEntities.register();
    }
}
