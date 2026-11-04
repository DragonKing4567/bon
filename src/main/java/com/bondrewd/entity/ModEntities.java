package com.bondrewd.entity;

import com.bondrewd.BondrewdMod;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.BiomeKeys;

public class ModEntities {
    public static final EntityType<BondrewdEntity> BONDREWD = Registry.register(
            Registries.ENTITY_TYPE, new Identifier(BondrewdMod.MOD_ID, "bondrewd"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, BondrewdEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .trackRangeBlocks(64)
                    .build());

    public static void register() {
        FabricDefaultAttributeRegistry.register(BONDREWD, BondrewdEntity.createBondrewdAttributes());
        SpawnRestriction.register(BONDREWD, SpawnRestriction.Location.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, HostileEntity::canSpawnInDark);
        // Very rare natural spawn in the Deep Dark, our stand-in for the Abyss
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(BiomeKeys.DEEP_DARK),
                SpawnGroup.MONSTER, BONDREWD, 1, 1, 1);
    }
}
