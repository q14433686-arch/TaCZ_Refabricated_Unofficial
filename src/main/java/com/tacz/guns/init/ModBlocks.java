package com.tacz.guns.init;

import com.tacz.guns.block.entity.AmmoStorageBlockEntity;
import com.tacz.guns.GunMod;
import com.tacz.guns.block.*;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import com.tacz.guns.block.entity.GunDisplayCaseBlockEntity;
import com.tacz.guns.block.entity.GunPegboardBlockEntity;
import com.tacz.guns.block.entity.StatueBlockEntity;
import com.tacz.guns.block.entity.TargetBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

public class ModBlocks {
    public static void init() {

    }

    // 旧方块就让他独占一个了
    public static Block GUN_SMITH_TABLE = registerBlock("gun_smith_table", new GunSmithTableBlockB(woodProps("gun_smith_table")));
    public static Block WORKBENCH_111 = registerBlock("workbench_a", new GunSmithTableBlockA(woodProps("workbench_a")));
    public static Block WORKBENCH_211 = registerBlock("workbench_b", new GunSmithTableBlockB(woodProps("workbench_b")));
    public static Block WORKBENCH_121 = registerBlock("workbench_c", new GunSmithTableBlockC(woodProps("workbench_c")));

    public static Block TARGET = registerBlock("target", new TargetBlock(woodProps("target")));
    public static Block STATUE = registerBlock("statue", new StatueBlock(BlockBehaviour.Properties.of().setId(blockKey("statue")).sound(SoundType.STONE).strength(2.0F, 3.0F).noOcclusion().pushReaction(PushReaction.DESTROY)));

    public static final Block AMMO_STORAGE = storage("ammo_storage", AmmoStorageBlock.IRON_CAPACITY_STACKS);
    public static final Block AMMO_STORAGE_GOLD = storage("ammo_storage_gold", AmmoStorageBlock.GOLD_CAPACITY_STACKS);
    public static final Block AMMO_STORAGE_DIAMOND = storage("ammo_storage_diamond", AmmoStorageBlock.DIAMOND_CAPACITY_STACKS);
    public static final BlockEntityType<AmmoStorageBlockEntity> AMMO_STORAGE_BE = registerBlockEntity("ammo_storage", AmmoStorageBlockEntity.TYPE);

    public static final Block GUN_DISPLAY_CASE = registerBlock("gun_display_case", new GunDisplayCaseBlock(displayProperties("gun_display_case", SoundType.GLASS).strength(0.5f, 1200f)));
    public static final Block GUN_PEGBOARD = registerBlock("gun_pegboard", new GunPegboardBlock(false, displayProperties("gun_pegboard", SoundType.METAL)));
    public static final Block GLOW_GUN_PEGBOARD = registerBlock("glow_gun_pegboard", new GunPegboardBlock(true, displayProperties("glow_gun_pegboard", SoundType.METAL)));
    public static final BlockEntityType<GunDisplayCaseBlockEntity> GUN_DISPLAY_CASE_BE = registerBlockEntity("gun_display_case", GunDisplayCaseBlockEntity.TYPE);
    public static final BlockEntityType<GunPegboardBlockEntity> GUN_PEGBOARD_BE = registerBlockEntity("gun_pegboard", GunPegboardBlockEntity.TYPE);

    private static BlockBehaviour.Properties displayProperties(String name, SoundType sound) {
        return BlockBehaviour.Properties.of().setId(blockKey(name)).sound(sound).strength(5f, 6f).noOcclusion().pushReaction(PushReaction.DESTROY);
    }

    private static Block storage(String name, int capacity) {
        return registerBlock(name, new AmmoStorageBlock(capacity, BlockBehaviour.Properties.of().setId(blockKey(name)).sound(SoundType.METAL)
                .strength(2f).pushReaction(PushReaction.DESTROY)));
    }

    public static BlockEntityType<GunSmithTableBlockEntity> GUN_SMITH_TABLE_BE = registerBlockEntity("gun_smith_table", GunSmithTableBlockEntity.TYPE);
    public static BlockEntityType<TargetBlockEntity> TARGET_BE = registerBlockEntity("target", TargetBlockEntity.TYPE);
    public static BlockEntityType<StatueBlockEntity> STATUE_BE = registerBlockEntity("statue", StatueBlockEntity.TYPE);

    public static final TagKey<Block> BULLET_IGNORE_BLOCKS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "bullet_ignore"));

    private static ResourceKey<Block> blockKey(String name) {
        return ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(GunMod.MOD_ID, name));
    }

    private static BlockBehaviour.Properties woodProps(String name) {
        return BlockBehaviour.Properties.of().setId(blockKey(name)).sound(SoundType.WOOD).strength(2.0F, 3.0F).noOcclusion().pushReaction(PushReaction.DESTROY);
    }

    private static Block registerBlock(String name, Block block) {
        return Registry.register(BuiltInRegistries.BLOCK, Identifier.fromNamespaceAndPath(GunMod.MOD_ID, name), block);
    }

    private static <T extends BlockEntity> BlockEntityType<T> registerBlockEntity(String name, BlockEntityType<T> blockEntity) {
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Identifier.fromNamespaceAndPath(GunMod.MOD_ID, name), blockEntity);
    }
}
