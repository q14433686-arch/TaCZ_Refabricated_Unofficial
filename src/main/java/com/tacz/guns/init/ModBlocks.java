package com.tacz.guns.init;

import com.tacz.guns.GunMod;
import com.tacz.guns.block.*;
import com.tacz.guns.block.entity.CartridgeAssemblyMachineBlockEntity;
import com.tacz.guns.block.entity.IndustrialSalvageStationBlockEntity;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
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
    /** Dedicated GUI machine for the final case/projectile/primer/propellant assembly. */
    public static Block CARTRIDGE_ASSEMBLY_MACHINE = registerBlock("cartridge_assembly_machine",
            new CartridgeAssemblyMachineBlock(metalProps("cartridge_assembly_machine")));
    /** One-input recovery station for stripped guns, empty magazines and obsolete dies. */
    public static Block INDUSTRIAL_SALVAGE_STATION = registerBlock("industrial_salvage_station",
            new IndustrialSalvageStationBlock(metalProps("industrial_salvage_station")));

    public static Block TARGET = registerBlock("target", new TargetBlock(woodProps("target")));
    public static Block STATUE = registerBlock("statue", new StatueBlock(BlockBehaviour.Properties.of().setId(blockKey("statue")).sound(SoundType.STONE).strength(2.0F, 3.0F).noOcclusion().pushReaction(PushReaction.DESTROY)));

    public static BlockEntityType<GunSmithTableBlockEntity> GUN_SMITH_TABLE_BE = registerBlockEntity("gun_smith_table", GunSmithTableBlockEntity.TYPE);
    public static BlockEntityType<CartridgeAssemblyMachineBlockEntity> CARTRIDGE_ASSEMBLY_MACHINE_BE = registerBlockEntity(
            "cartridge_assembly_machine", CartridgeAssemblyMachineBlockEntity.TYPE
    );
    public static BlockEntityType<IndustrialSalvageStationBlockEntity> INDUSTRIAL_SALVAGE_STATION_BE = registerBlockEntity(
            "industrial_salvage_station", IndustrialSalvageStationBlockEntity.TYPE
    );
    public static BlockEntityType<TargetBlockEntity> TARGET_BE = registerBlockEntity("target", TargetBlockEntity.TYPE);
    public static BlockEntityType<StatueBlockEntity> STATUE_BE = registerBlockEntity("statue", StatueBlockEntity.TYPE);

    public static final TagKey<Block> BULLET_IGNORE_BLOCKS = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "bullet_ignore"));

    private static ResourceKey<Block> blockKey(String name) {
        return ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(GunMod.MOD_ID, name));
    }

    private static BlockBehaviour.Properties woodProps(String name) {
        return BlockBehaviour.Properties.of().setId(blockKey(name)).sound(SoundType.WOOD).strength(2.0F, 3.0F).noOcclusion().pushReaction(PushReaction.DESTROY);
    }

    private static BlockBehaviour.Properties metalProps(String name) {
        // Both current metal blocks use detailed, non-cubic Blockbench models.
        // Keeping the default full-block occlusion flag makes adjacent blocks
        // discard their shared face even where this model has a visible gap.
        // From an oblique angle that exposes the neighbour through the machine
        // (the reported "x-ray" effect). This affects render-face culling only;
        // collision, hardness and piston behaviour remain unchanged.
        return BlockBehaviour.Properties.of().setId(blockKey(name)).sound(SoundType.METAL).strength(4.0F, 6.0F)
                .noOcclusion().pushReaction(PushReaction.BLOCK);
    }

    private static Block registerBlock(String name, Block block) {
        return Registry.register(BuiltInRegistries.BLOCK, Identifier.fromNamespaceAndPath(GunMod.MOD_ID, name), block);
    }

    private static <T extends BlockEntity> BlockEntityType<T> registerBlockEntity(String name, BlockEntityType<T> blockEntity) {
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Identifier.fromNamespaceAndPath(GunMod.MOD_ID, name), blockEntity);
    }
}
