package com.bondrewd.item;

import com.bondrewd.BondrewdMod;
import com.bondrewd.entity.ModEntities;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public class ModItems {
    public static final Item UMBRA_HAND = reg("umbra_hand",
            new UmbraHandItem(new Item.Settings().maxDamage(128).rarity(Rarity.EPIC)));
    public static final Item DAWN_CARTRIDGE = reg("dawn_cartridge",
            new DawnCartridgeItem(new Item.Settings().maxCount(16).rarity(Rarity.RARE)));
    public static final Item BONDREWD_SPAWN_EGG = reg("bondrewd_spawn_egg",
            new SpawnEggItem(ModEntities.BONDREWD, 0x111018, 0xb8a1ff, new Item.Settings()));

    private static Item reg(String name, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(BondrewdMod.MOD_ID, name), item);
    }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(e -> {
            e.add(UMBRA_HAND);
            e.add(DAWN_CARTRIDGE);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(e -> e.add(BONDREWD_SPAWN_EGG));
    }
}
