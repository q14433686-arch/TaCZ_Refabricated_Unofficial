#!/usr/bin/env python3
"""Exercise the target block, block entities, minecart and renderers with API doubles.

The doubles make placement callbacks, persistence, removal, name changes and
callback ordering testable without a game installation. Item consumption only
simulates vanilla's ordering; it does not run the real BlockItem implementation.
These tests do not replace a 26.3 build or in-game tests. Requires a JDK with
javac; TACZ_JAVAC and TACZ_JAVA can override the commands.
"""

from __future__ import annotations

import os
import shlex
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# Only the API surface used by these real classes is modelled here. Profile
# construction rejects nulls, as the 26.3 authlib record does.
STUBS = {
    "javax.annotation.Nullable": """
        import java.lang.annotation.*;
        @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD})
        public @interface Nullable {}
    """,
    "com.mojang.authlib.GameProfile": """
        import java.util.*;
        public record GameProfile(UUID id, String name, Map<String, String> properties) {
            public GameProfile { Objects.requireNonNull(id); Objects.requireNonNull(name); Objects.requireNonNull(properties); }
            public GameProfile(UUID id, String name) { this(id, name, Map.of()); }
        }
    """,
    "com.mojang.serialization.DataResult": """
        import java.util.Optional;
        public class DataResult<T> {
            private final T value;
            public DataResult(T value) { this.value = value; }
            public Optional<T> result() { return Optional.ofNullable(value); }
        }
    """,
    "com.mojang.serialization.Codec": """
        public class Codec<T> {
            public DataResult<T> parse(Object ops, Object value) { return new DataResult<>(null); }
            public DataResult<Object> encodeStart(Object ops, T value) { return new DataResult<>(value); }
        }
    """,
    "com.mojang.serialization.JsonOps": "public class JsonOps { public static final JsonOps INSTANCE = new JsonOps(); }",
    "com.google.gson.JsonParser": "public class JsonParser { public static Object parseString(String value) { return value; } }",
    "com.tacz.guns.GunMod": """
        import java.util.*;
        public class GunMod {
            public static final Logger LOGGER = new Logger();
            public static class Logger {
                public final List<String> warnings = new ArrayList<>();
                public void warn(String message, Object... arguments) { warnings.add(message); }
            }
        }
    """,
    "com.tacz.guns.init.ModBlocks": """
        import com.tacz.guns.block.TargetBlock;
        import com.tacz.guns.block.entity.TargetBlockEntity;
        import net.minecraft.world.level.block.Block;
        import net.minecraft.world.level.block.entity.BlockEntityType;
        public class ModBlocks {
            public static final Object STATUE = new Object();
            public static final TargetBlock TARGET = new TargetBlock(new Block.Properties());
            public static BlockEntityType<TargetBlockEntity> TARGET_BE;
        }
    """,
    "com.tacz.guns.init.ModSounds": "public class ModSounds { public static final Object TARGET_HIT = new Object(); }",
    "com.tacz.guns.config.common.OtherConfig": """
        public class OtherConfig {
            public static final Distance TARGET_SOUND_DISTANCE = new Distance();
            public static class Distance { public float get() { return 16; } }
        }
    """,
    "com.tacz.guns.block.StatueBlock": """
        import net.minecraft.core.Direction;
        import net.minecraft.world.level.block.state.properties.Property;
        public class StatueBlock { public static final Property<Direction> FACING = new Property<>(); }
    """,
    "net.minecraft.core.Direction": """
        public enum Direction {
            NORTH(0, 0, -1, Axis.Z), SOUTH(0, 0, 1, Axis.Z), EAST(1, 0, 0, Axis.X), WEST(-1, 0, 0, Axis.X),
            UP(0, 1, 0, Axis.Y), DOWN(0, -1, 0, Axis.Y);
            public enum Axis { X, Y, Z }
            private final int x, y, z; private final Axis axis;
            Direction(int x, int y, int z, Axis axis) { this.x = x; this.y = y; this.z = z; this.axis = axis; }
            public int getStepX() { return x; } public int getStepY() { return y; } public int getStepZ() { return z; }
            public int get2DDataValue() { return 0; }
            public Axis getAxis() { return axis; }
            public Direction getOpposite() { return switch (this) {
                case NORTH -> SOUTH; case SOUTH -> NORTH; case EAST -> WEST; case WEST -> EAST;
                case UP -> DOWN; case DOWN -> UP;
            }; }
        }
    """,
    "net.minecraft.core.BlockPos": """
        public record BlockPos(int x, int y, int z) {
            public int getX() { return x; } public int getY() { return y; } public int getZ() { return z; }
            public BlockPos relative(Direction d) { return new BlockPos(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ()); }
            public BlockPos above() { return new BlockPos(x, y + 1, z); }
            public BlockPos below() { return new BlockPos(x, y - 1, z); }
        }
    """,
    "net.minecraft.core.UUIDUtil": """
        import java.util.UUID;
        public class UUIDUtil {
            public static UUID uuidFromIntArray(int[] a) {
                return new UUID((long)a[0] << 32 | (long)a[1] & 0xffffffffL,
                                (long)a[2] << 32 | (long)a[3] & 0xffffffffL);
            }
        }
    """,
    "net.minecraft.core.HolderLookup": "public class HolderLookup { public interface Provider {} }",
    "net.minecraft.core.particles.ParticleTypes": "public class ParticleTypes { public static final Object END_ROD = new Object(); }",
    "net.minecraft.nbt.CompoundTag": """
        import java.util.*;
        public class CompoundTag {
            public final Map<String, Object> values = new HashMap<>();
            public void put(String key, Object value) { values.put(key, value); }
        }
    """,
    "net.minecraft.nbt.NbtOps": "public class NbtOps { public static final NbtOps INSTANCE = new NbtOps(); }",
    "net.minecraft.network.chat.Component": """
        public record Component(String text) {
            public static Component empty() { return new Component(\"\"); }
            public static Component literal(String value) { return new Component(value); }
            public static Component translatable(String key, Object... arguments) { return new Component(key); }
            public String getString() { return text; }
        }
    """,
    "net.minecraft.network.chat.ComponentSerialization": """
        import com.mojang.serialization.*;
        public class ComponentSerialization {
            public static final Codec<Component> CODEC = new Codec<>() {
                @Override public DataResult<Component> parse(Object ops, Object value) { return new DataResult<>(new Component(value.toString())); }
                @Override public DataResult<Object> encodeStart(Object ops, Component value) { return new DataResult<>(value.text()); }
            };
        }
    """,
    "net.minecraft.network.protocol.Packet": "public interface Packet<T> {}",
    "net.minecraft.network.protocol.game.ClientGamePacketListener": "public interface ClientGamePacketListener {}",
    "net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket": """
        import net.minecraft.network.protocol.Packet;
        import net.minecraft.world.level.block.entity.BlockEntity;
        public class ClientboundBlockEntityDataPacket implements Packet<ClientGamePacketListener> {
            public static ClientboundBlockEntityDataPacket create(BlockEntity entity) { return new ClientboundBlockEntityDataPacket(); }
        }
    """,
    "net.minecraft.sounds.SoundSource": "public enum SoundSource { BLOCKS }",
    "net.minecraft.util.StringUtil": """
        public class StringUtil {
            public static boolean isValidPlayerName(String name) {
                return name.length() <= 16 && name.chars().noneMatch(c -> c <= 32 || c >= 127);
            }
        }
    """,
    "net.minecraft.util.Util": """
        import java.util.*;
        import java.util.concurrent.Executor;
        public class Util {
            private static final Queue<Runnable> IO = new ArrayDeque<>();
            public static Executor nonCriticalIoPool() { return IO::add; }
            public static void runNext() { IO.remove().run(); }
            public static int pending() { return IO.size(); }
            public static void reset() { IO.clear(); }
        }
    """,
    "net.minecraft.world.Nameable": """
        import net.minecraft.network.chat.Component;
        public interface Nameable { Component getName(); Component getCustomName(); }
    """,
    "net.minecraft.world.item.ItemStack": """
        import com.mojang.serialization.Codec;
        import net.minecraft.network.chat.Component;
        import net.minecraft.world.entity.player.Player;
        import net.minecraft.world.level.block.Block;
        public class ItemStack {
            public static final ItemStack EMPTY = new ItemStack(0);
            public static final Codec<ItemStack> OPTIONAL_CODEC = new Codec<>();
            public int count;
            private Component customName;
            public ItemStack(int count) { this.count = count; }
            public ItemStack(Item item) { this.count = 1; }
            public ItemStack(Block block) { this.count = 1; }
            public boolean isEmpty() { return count == 0; }
            public ItemStack copy() { ItemStack copy = new ItemStack(count); copy.customName = customName; return copy; }
            public void set(Object key, Component value) { this.customName = value; }
            public Component getCustomName() { return customName; }
            public Component getHoverName() { return customName == null ? Component.literal(\"target\") : customName; }
            public void consume(int amount, Player player) { if (player == null || !player.hasInfiniteMaterials()) count = Math.max(0, count - amount); }
        }
    """,
    "net.minecraft.world.item.component.ResolvableProfile": """
        import com.mojang.authlib.GameProfile;
        import com.mojang.serialization.Codec;
        import java.nio.charset.StandardCharsets;
        import java.util.*;
        public abstract class ResolvableProfile {
            public static final Codec<ResolvableProfile> CODEC = new Codec<>();
            public abstract GameProfile partialProfile();
            public abstract Optional<String> name();
            public static ResolvableProfile createUnresolved(String name) { return new Dynamic(name, null); }
            public static ResolvableProfile createUnresolved(UUID id) { return new Dynamic(null, id); }
            public static ResolvableProfile createResolved(GameProfile profile) { return new Static(profile); }
            public static class Dynamic extends ResolvableProfile {
                private final String name; private final UUID id;
                Dynamic(String name, UUID id) { this.name = name; this.id = id; }
                public GameProfile partialProfile() {
                    return new GameProfile(id != null ? id : UUID.nameUUIDFromBytes((\"OfflinePlayer:\" + name).getBytes(StandardCharsets.UTF_8)),
                                           name == null ? \"\" : name);
                }
                public Optional<String> name() { return Optional.ofNullable(name); }
            }
            public static class Static extends ResolvableProfile {
                private final GameProfile profile;
                Static(GameProfile profile) { this.profile = profile; }
                public GameProfile partialProfile() { return profile; }
                public Optional<String> name() { return Optional.of(profile.name()); }
            }
        }
    """,
    "net.minecraft.world.level.block.state.properties.Property": "public class Property<T> {}",
    "net.minecraft.world.level.block.state.BlockState": """
        import java.util.*;
        import net.minecraft.core.BlockPos;
        import net.minecraft.world.item.context.BlockPlaceContext;
        import net.minecraft.world.level.LevelReader;
        import net.minecraft.world.level.block.Block;
        import net.minecraft.world.level.block.state.properties.Property;
        public class BlockState {
            private final Block block;
            private final Map<Property<?>, Object> values;
            public BlockState() { this(null, new HashMap<>()); }
            public BlockState(Block block) { this(block, new HashMap<>()); }
            private BlockState(Block block, Map<Property<?>, Object> values) { this.block = block; this.values = values; }
            @SuppressWarnings(\"unchecked\") public <T> T getValue(Property<T> key) { return (T) values.get(key); }
            public <T> BlockState setValue(Property<T> key, T value) {
                Map<Property<?>, Object> next = new HashMap<>(values); next.put(key, value); return new BlockState(block, next);
            }
            public boolean hasProperty(Property<?> key) { return values.containsKey(key); }
            public Block getBlock() { return block; }
            public boolean is(Object other) { return block == other; }
            public boolean canSurvive(LevelReader level, BlockPos pos) { return true; }
            public boolean canBeReplaced(BlockPlaceContext context) { return true; }
        }
    """,
    "net.minecraft.world.level.block.Block": """
        import net.minecraft.core.*;
        import net.minecraft.server.level.ServerLevel;
        import net.minecraft.util.RandomSource;
        import net.minecraft.world.entity.LivingEntity;
        import net.minecraft.world.entity.projectile.Projectile;
        import net.minecraft.world.item.ItemStack;
        import net.minecraft.world.item.context.BlockPlaceContext;
        import net.minecraft.world.level.*;
        import net.minecraft.world.level.block.entity.*;
        import net.minecraft.world.level.block.state.*;
        import net.minecraft.world.phys.BlockHitResult;
        import net.minecraft.world.phys.shapes.*;
        public class Block {
            public static final int UPDATE_ALL = 3, UPDATE_KNOWN_SHAPE = 16, UPDATE_CLIENTS = 2;
            public static class Properties {}
            protected final StateDefinition<Block, BlockState> stateDefinition;
            private BlockState defaultState;
            public Block() { this(new Properties()); }
            public Block(Properties props) { stateDefinition = new StateDefinition<>(this); defaultState = new BlockState(this); }
            protected void registerDefaultState(BlockState state) { defaultState = state; }
            public BlockState defaultBlockState() { return defaultState; }
            public static VoxelShape box(double x1, double y1, double z1, double x2, double y2, double z2) { return new VoxelShape(); }
            public static void popResource(Level level, BlockPos pos, ItemStack stack) {
                if (!level.isClientSide() && !stack.isEmpty()) level.drops.add(new Level.Drop(pos, stack));
            }
            protected static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
                BlockEntityType<A> given, BlockEntityType<E> expected, BlockEntityTicker<? super E> ticker) { return null; }
            public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) { return null; }
            protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {}
            public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return null; }
            protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) { return new VoxelShape(); }
            public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {}
            public void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {}
            protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess tickAccess, BlockPos pos,
                                             Direction facing, BlockPos facingPos, BlockState facingState, RandomSource random) { return state; }
            public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState(); }
            public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {}
            protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeEntity) { return new ItemStack(this); }
            public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) { return true; }
            public boolean isSignalSource(BlockState state) { return false; }
            protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) { return 0; }
            public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {}
            protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
        }
    """,
    "net.minecraft.world.level.Level": """
        import java.util.*;
        import net.minecraft.core.BlockPos;
        import net.minecraft.world.item.ItemStack;
        import net.minecraft.world.entity.Entity;
        import net.minecraft.world.level.block.entity.BlockEntity;
        import net.minecraft.world.level.block.entity.BlockEntityType;
        import net.minecraft.world.level.block.state.BlockState;
        import net.minecraft.world.level.gamerules.GameRules;
        public class Level implements LevelReader {
            private final boolean client;
            private final Map<BlockPos, BlockState> states = new HashMap<>();
            private final Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();
            public final List<Drop> drops = new ArrayList<>();
            public int updates;
            public BlockState state = new BlockState();
            public record Drop(BlockPos pos, ItemStack stack) {}
            public static class WorldBorder { public boolean isWithinBounds(BlockPos pos) { return true; } }
            public static class BlockTicks { public boolean hasScheduledTick(BlockPos pos, Object block) { return false; } }
            public Level(boolean client) { this.client = client; }
            public boolean isClientSide() { return client; }
            public Random getRandom() { return new Random(1); }
            public long getGameTime() { return 1; }
            public void addParticle(Object type, double x, double y, double z, double dx, double dy, double dz) {}
            public BlockState getBlockState(BlockPos pos) { return states.getOrDefault(pos, state); }
            public BlockEntity getBlockEntity(BlockPos pos) { return blockEntities.get(pos); }
            @SuppressWarnings(\"unchecked\") public <T extends BlockEntity> Optional<T> getBlockEntity(BlockPos pos, BlockEntityType<T> type) {
                return Optional.ofNullable((T) blockEntities.get(pos));
            }
            public void addBlockEntity(BlockPos pos, BlockEntity entity) { blockEntities.put(pos, entity); entity.setLevel(this); }
            public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) { updates++; }
            public void setBlock(BlockPos pos, BlockState state, int flags) { this.state = state; states.put(pos, state); }
            public void scheduleTick(BlockPos pos, Object block, int ticks) {}
            public void playSound(Object player, BlockPos pos, Object sound, Object source, float volume, float pitch) {}
            public void playSound(Object player, Entity entity, Object sound, Object source, float volume, float pitch) {}
            public GameRules getGameRules() { return new GameRules(); }
            public WorldBorder getWorldBorder() { return new WorldBorder(); }
            public BlockTicks getBlockTicks() { return new BlockTicks(); }
        }
    """,
    "net.minecraft.world.level.storage.ValueInput": """
        import java.util.*;
        import com.mojang.serialization.Codec;
        import net.minecraft.world.item.component.ResolvableProfile;
        public class ValueInput {
            private final Map<String, Object> values;
            public ValueInput(Map<String, Object> values) { this.values = values; }
            public String getStringOr(String key, String fallback) { Object v = values.get(key); return v instanceof String s ? s : fallback; }
            public Optional<int[]> getIntArray(String key) { Object v = values.get(key); return v instanceof int[] a ? Optional.of(a) : Optional.empty(); }
            public Optional<ValueInput> child(String key) { Object v = values.get(key); return v instanceof ValueInput i ? Optional.of(i) : Optional.empty(); }
            @SuppressWarnings(\"unchecked\") public <T> Optional<T> read(String key, Codec<T> codec) {
                Object v = values.get(key);
                if ((Object) codec == ResolvableProfile.CODEC && !(v instanceof ResolvableProfile)) return Optional.empty();
                return Optional.ofNullable((T) v);
            }
        }
    """,
    "net.minecraft.world.level.storage.ValueOutput": """
        import java.util.*;
        import com.mojang.serialization.Codec;
        public class ValueOutput {
            public final Map<String, Object> values = new HashMap<>();
            public void putString(String key, String value) { values.put(key, value); }
            public <T> void store(String key, Codec<T> codec, T value) { values.put(key, value); }
        }
    """,
    "net.minecraft.world.level.block.entity.BlockEntityType": """
        import java.util.Set;
        import net.minecraft.core.BlockPos;
        import net.minecraft.world.level.block.state.BlockState;
        public class BlockEntityType<T> {
            public interface Factory<T> { T create(BlockPos pos, BlockState state); }
            public BlockEntityType(Factory<T> factory, Set<?> blocks) {}
        }
    """,
    "net.minecraft.world.level.block.entity.BlockEntity": """
        import net.minecraft.core.*;
        import net.minecraft.nbt.CompoundTag;
        import net.minecraft.network.protocol.Packet;
        import net.minecraft.network.protocol.game.ClientGamePacketListener;
        import net.minecraft.world.level.Level;
        import net.minecraft.world.level.block.state.BlockState;
        import net.minecraft.world.level.storage.*;
        public class BlockEntity {
            protected Level level;
            protected final BlockPos worldPosition;
            private final BlockState state;
            private boolean removed;
            public int changes;
            public BlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { this.worldPosition = pos; this.state = state; }
            public void setLevel(Level level) { this.level = level; }
            public BlockState getBlockState() { return state; }
            public boolean isRemoved() { return removed; }
            public void setRemoved() { removed = true; }
            public void clearRemoved() { removed = false; }
            public void setChanged() { changes++; }
            public void preRemoveSideEffects(BlockPos pos, BlockState state) {}
            protected void loadAdditional(ValueInput input) {}
            protected void saveAdditional(ValueOutput output) {}
            public Packet<ClientGamePacketListener> getUpdatePacket() { return null; }
            public CompoundTag getUpdateTag(HolderLookup.Provider provider) { return new CompoundTag(); }
            public CompoundTag saveWithoutMetadata(HolderLookup.Provider provider) {
                ValueOutput output = new ValueOutput(); saveAdditional(output);
                CompoundTag result = new CompoundTag(); result.values.putAll(output.values); return result;
            }
        }
    """,
    "net.minecraft.server.players.ProfileResolver": """
        import com.mojang.authlib.GameProfile;
        import java.util.*;
        public class ProfileResolver {
            public final Map<String, GameProfile> names = new HashMap<>();
            public final Map<UUID, GameProfile> ids = new HashMap<>();
            public boolean fail;
            public Optional<GameProfile> fetchByName(String name) { if (fail) throw new IllegalStateException(); return Optional.ofNullable(names.get(name)); }
            public Optional<GameProfile> fetchById(UUID id) { if (fail) throw new IllegalStateException(); return Optional.ofNullable(ids.get(id)); }
        }
    """,
    "net.minecraft.server.Services": """
        import net.minecraft.server.players.ProfileResolver;
        public record Services(ProfileResolver profileResolver) {}
    """,
    "net.minecraft.server.MinecraftServer": """
        import java.util.*;
        import net.minecraft.server.players.ProfileResolver;
        public class MinecraftServer {
            private final Services services;
            private final Queue<Runnable> tasks = new ArrayDeque<>();
            public MinecraftServer(ProfileResolver resolver) { services = new Services(resolver); }
            public Services services() { return services; }
            public void execute(Runnable task) { tasks.add(task); }
            public void runNext() { tasks.remove().run(); }
            public int pending() { return tasks.size(); }
        }
    """,
    "net.minecraft.server.level.ServerLevel": """
        import net.minecraft.server.MinecraftServer;
        import net.minecraft.world.level.Level;
        public class ServerLevel extends Level {
            private final MinecraftServer server;
            public ServerLevel(MinecraftServer server) { super(false); this.server = server; }
            public MinecraftServer getServer() { return server; }
        }
    """,
    "net.minecraft.world.phys.BlockHitResult": """
        import net.minecraft.core.BlockPos;
        import net.minecraft.core.Direction;
        public class BlockHitResult {
            public BlockPos getBlockPos() { return new BlockPos(0, 0, 0); }
            public Direction getDirection() { return Direction.NORTH; }
            public Vec3 getLocation() { return new Vec3(); }
        }
    """,
}

# Additional doubles needed to compile the real target minecart alongside the
# block entities.
STUBS.update({
    "org.jetbrains.annotations.Nullable": """
        import java.lang.annotation.*;
        @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD})
        public @interface Nullable {}
    """,
    "org.jetbrains.annotations.NotNull": """
        import java.lang.annotation.*;
        @Target({ElementType.TYPE_USE, ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD})
        public @interface NotNull {}
    """,
    "cn.sh1rocu.tacz.api.LogicalSide": "public enum LogicalSide { SERVER }",
    "cn.sh1rocu.tacz.api.extension.IMinecart": "public interface IMinecart { boolean tacz$canBeRidden(); }",
    "com.tacz.guns.api.entity.ITargetEntity": """
        import net.minecraft.world.entity.Entity;
        import net.minecraft.world.damagesource.DamageSource;
        import net.minecraft.world.phys.EntityHitResult;
        public interface ITargetEntity { void onProjectileHit(Entity entity, EntityHitResult result, DamageSource source, float damage); }
    """,
    "com.tacz.guns.api.event.common.EntityHurtByGunEvent": """
        public class EntityHurtByGunEvent {
            public static final Event POST = new Event();
            public static class Post { public Post(Object... arguments) {} }
            public static class Event { public Invoker invoker() { return new Invoker(); } }
            public static class Invoker { public void post(Post event) {} }
        }
    """,
    "com.tacz.guns.config.client.RenderConfig": """
        public class RenderConfig {
            public static final Distance TARGET_RENDER_DISTANCE = new Distance();
            public static class Distance { public int get() { return 1; } }
        }
    """,
    "com.tacz.guns.init.ModItems": """
        import net.minecraft.world.item.Item;
        public class ModItems { public static final Item TARGET_MINECART = new Item(); }
    """,
    "com.tacz.guns.network.NetworkHandler": """
        import net.minecraft.world.entity.Entity;
        public class NetworkHandler { public static void sendToDimension(Object message, Entity entity) {} }
    """,
    "com.tacz.guns.network.message.event.ServerMessageGunHurt": """
        public class ServerMessageGunHurt { public ServerMessageGunHurt(Object... arguments) {} }
    """,
    "com.tacz.guns.entity.EntityKineticBullet": """
        import net.minecraft.world.entity.projectile.Projectile;
        import net.minecraft.world.level.Level;
        import net.minecraft.world.phys.Vec3;
        public class EntityKineticBullet extends Projectile {
            public EntityKineticBullet(Level level) { super(level); }
            public Object getGunId() { return null; }
            public Object getGunDisplayId() { return null; }
            public float getDamage(Vec3 position) { return 1; }
        }
    """,
    "net.minecraft.core.component.DataComponents": "public class DataComponents { public static final Object CUSTOM_NAME = new Object(); }",
    "net.minecraft.core.registries.Registries": "public class Registries { public static final Object ENTITY_TYPE = new Object(); }",
    "net.minecraft.resources.Identifier": """
        public record Identifier(String value) {
            public static Identifier fromNamespaceAndPath(String namespace, String path) { return new Identifier(namespace + \":\" + path); }
        }
    """,
    "net.minecraft.resources.ResourceKey": """
        public class ResourceKey<T> { public static <T> ResourceKey<T> create(Object registry, Identifier id) { return new ResourceKey<>(); } }
    """,
    "net.minecraft.tags.DamageTypeTags": "public class DamageTypeTags { public static final Object IS_EXPLOSION = new Object(); }",
    "net.minecraft.world.damagesource.DamageSource": """
        import net.minecraft.world.entity.Entity;
        public class DamageSource {
            public boolean isDirect() { return false; }
            public boolean is(Object tag) { return false; }
            public Entity getEntity() { return null; }
        }
    """,
    "net.minecraft.world.entity.MobCategory": "public enum MobCategory { MISC }",
    "net.minecraft.world.entity.EntityType": """
        import java.util.function.BiFunction;
        import net.minecraft.resources.ResourceKey;
        import net.minecraft.world.level.Level;
        public class EntityType<T extends Entity> {
            public static class Builder<T extends Entity> {
                public static <T extends Entity> Builder<T> of(BiFunction<EntityType<T>, Level, T> factory, MobCategory category) { return new Builder<>(); }
                public Builder<T> sized(float width, float height) { return this; }
                public Builder<T> clientTrackingRange(int range) { return this; }
                public EntityType<T> build(ResourceKey<EntityType<T>> key) { return new EntityType<>(); }
            }
        }
    """,
    "net.minecraft.world.entity.Entity": """
        import net.minecraft.network.chat.Component;
        import net.minecraft.server.level.ServerLevel;
        import net.minecraft.world.item.ItemStack;
        import net.minecraft.world.level.Level;
        import net.minecraft.world.phys.AABB;
        import net.minecraft.world.phys.Vec3;
        public class Entity {
            public enum RemovalReason { KILLED }
            private final Level level;
            private Component customName;
            public Entity(Level level) { this.level = level; }
            public Level level() { return level; }
            public Component getCustomName() { return customName; }
            public void setCustomName(Component name) { customName = name; }
            public boolean hasCustomName() { return customName != null; }
            public boolean isRemoved() { return false; }
            public void remove(RemovalReason reason) {}
            public int getId() { return 1; }
            public Vec3 position() { return new Vec3(); }
            public AABB getBoundingBox() { return new AABB(); }
            public double getViewScale() { return 1; }
            public void spawnAtLocation(ServerLevel level, ItemStack stack) {}
        }
    """,
    "net.minecraft.world.entity.player.Player": """
        import net.minecraft.network.chat.Component;
        import net.minecraft.world.entity.LivingEntity;
        import net.minecraft.world.level.Level;
        public class Player extends LivingEntity {
            private final boolean creative;
            public Player(Level level) { this(level, false); }
            public Player(Level level, boolean creative) { super(level); this.creative = creative; }
            public boolean hasInfiniteMaterials() { return creative; }
            public void sendSystemMessage(Component message) {}
            public void sendOverlayMessage(Component message) {}
        }
    """,
    "net.minecraft.world.entity.vehicle.minecart.AbstractMinecart": """
        import net.minecraft.server.level.ServerLevel;
        import net.minecraft.world.damagesource.DamageSource;
        import net.minecraft.world.entity.Entity;
        import net.minecraft.world.entity.EntityType;
        import net.minecraft.world.item.Item;
        import net.minecraft.world.item.ItemStack;
        import net.minecraft.world.level.Level;
        import net.minecraft.world.level.block.state.BlockState;
        public class AbstractMinecart extends Entity {
            public AbstractMinecart(EntityType<?> type, Level level) { super(level); }
            public AbstractMinecart(EntityType<?> type, Level level, double x, double y, double z) { super(level); }
            public void setHurtDir(int value) {}
            public void setHurtTime(int value) {}
            public void markHurt() {}
            public void setDamage(float value) {}
            protected boolean shouldSourceDestroy(DamageSource source) { return true; }
            public boolean shouldRenderAtSqrDistance(double distance) { return true; }
            protected void destroy(ServerLevel level, DamageSource source) {}
            protected Item getDropItem() { return new Item(); }
            public ItemStack getPickResult() { return ItemStack.EMPTY; }
            public BlockState getDefaultDisplayBlockState() { return new BlockState(); }
            protected double getMaxSpeed(ServerLevel level) { return 1; }
        }
    """,
    "net.minecraft.world.item.Item": "public class Item {}",
    "net.minecraft.world.level.gamerules.GameRules": """
        public class GameRules {
            public static final String ENTITY_DROPS = \"entity_drops\";
            public boolean get(String rule) { return true; }
        }
    """,
    "net.minecraft.world.phys.EntityHitResult": "public class EntityHitResult {}",
    "net.minecraft.world.phys.Vec3": "public class Vec3 { public double x, y, z; public double distanceTo(Vec3 other) { return 0; } }",
    "net.minecraft.world.phys.AABB": "public class AABB { public double getSize() { return 1; } }",
    "org.apache.commons.lang3.tuple.Pair": """
        public class Pair<L, R> { public static <L, R> Pair<L, R> of(L left, R right) { return new Pair<>(); } }
    """,
})

# Rendering doubles let the real renderers' state extraction and fallback be
# exercised without pretending to provide a GPU, texture download or game.
STUBS.update({
    "net.fabricmc.api.EnvType": "public enum EnvType { CLIENT }",
    "net.fabricmc.api.Environment": """
        import java.lang.annotation.*;
        @Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR})
        public @interface Environment { EnvType value(); }
    """,
    "com.mojang.math.Axis": """
        public enum Axis { ZN, YN, XP; public Object rotationDegrees(float degrees) { return new Object(); } }
    """,
    "com.mojang.blaze3d.vertex.PoseStack": """
        public class PoseStack {
            public static class Matrix { public Matrix set(Matrix other) { return this; } }
            public static class Pose { public Matrix pose() { return new Matrix(); } public Matrix normal() { return new Matrix(); } }
            public Pose last() { return new Pose(); }
            public void pushPose() {} public void popPose() {}
            public void translate(double x, double y, double z) {}
            public void scale(float x, float y, float z) {}
            public void rotate(Object rotation) {}
        }
    """,
    "com.tacz.guns.client.model.bedrock.BedrockPart": """
        import com.mojang.blaze3d.vertex.PoseStack;
        import net.minecraft.world.item.ItemDisplayContext;
        public class BedrockPart {
            public boolean visible; public float xRot;
            public void render(PoseStack pose, ItemDisplayContext context, Object consumer, int light, int overlay) {}
        }
    """,
    "com.tacz.guns.client.model.bedrock.BedrockModel": """
        import com.mojang.blaze3d.vertex.PoseStack;
        import net.minecraft.client.renderer.SubmitNodeCollector;
        import net.minecraft.client.renderer.rendertype.RenderType;
        import net.minecraft.world.item.ItemDisplayContext;
        public class BedrockModel {
            public BedrockPart getNode(String name) { return new BedrockPart(); }
            public void submit(PoseStack pose, ItemDisplayContext context, SubmitNodeCollector collector, RenderType type, int light, int overlay) {}
        }
    """,
    "com.tacz.guns.client.resource.InternalAssetLoader": """
        import java.util.Optional;
        import com.tacz.guns.client.model.bedrock.BedrockModel;
        import net.minecraft.resources.Identifier;
        public class InternalAssetLoader {
            public static final Identifier TARGET_MODEL_LOCATION = new Identifier(\"target_model\");
            public static final Identifier TARGET_TEXTURE_LOCATION = new Identifier(\"target_texture\");
            public static final Identifier TARGET_MINECART_MODEL_LOCATION = new Identifier(\"cart_model\");
            public static final Identifier TARGET_MINECART_TEXTURE_LOCATION = new Identifier(\"cart_texture\");
            public static final Identifier ENTITY_EMPTY_TEXTURE = new Identifier(\"empty\");
            public static Optional<BedrockModel> getBedrockModel(Identifier id) { return Optional.of(new BedrockModel()); }
        }
    """,
    "net.minecraft.client.renderer.PlayerSkinRenderCache": """
        import net.minecraft.client.resources.DefaultPlayerSkin;
        import net.minecraft.resources.Identifier;
        import net.minecraft.world.entity.player.PlayerSkin;
        import net.minecraft.world.item.component.ResolvableProfile;
        public class PlayerSkinRenderCache {
            public boolean fail, pending;
            public int requests;
            public ResolvableProfile lastProfile;
            public RenderInfo getOrDefault(ResolvableProfile profile) {
                requests++; lastProfile = profile;
                if (fail) throw new IllegalStateException(\"skin lookup failed\");
                if (pending) return new RenderInfo(DefaultPlayerSkin.get(profile.partialProfile()));
                String name = profile.partialProfile().name();
                return new RenderInfo(new PlayerSkin(new Identifier(\"skin:\" + (name.isEmpty() ? profile.partialProfile().id() : name))));
            }
            public record RenderInfo(PlayerSkin playerSkin) {}
        }
    """,
    "net.minecraft.client.resources.DefaultPlayerSkin": """
        import com.mojang.authlib.GameProfile;
        import net.minecraft.resources.Identifier;
        import net.minecraft.world.entity.player.PlayerSkin;
        public class DefaultPlayerSkin {
            public static PlayerSkin get(GameProfile profile) { return new PlayerSkin(new Identifier(\"default:\" + profile.name())); }
        }
    """,
    "net.minecraft.world.entity.player.PlayerSkin": """
        import net.minecraft.resources.Identifier;
        public record PlayerSkin(Identifier texture) {
            public Body body() { return new Body(texture); }
            public record Body(Identifier texturePath) {}
        }
    """,
    "net.minecraft.client.renderer.SubmitNodeCollector": """
        import java.util.function.BiConsumer;
        import com.mojang.blaze3d.vertex.PoseStack;
        import net.minecraft.client.renderer.rendertype.RenderType;
        public class SubmitNodeCollector {
            public int geometryCount;
            public void submitCustomGeometry(PoseStack pose, RenderType type, BiConsumer<PoseStack.Pose, Object> geometry) { geometryCount++; }
        }
    """,
    "net.minecraft.client.renderer.rendertype.RenderType": "public class RenderType {}",
    "net.minecraft.client.renderer.rendertype.RenderTypes": """
        import net.minecraft.resources.Identifier;
        public class RenderTypes {
            public static RenderType entityTranslucent(Identifier skin) { return new RenderType(); }
            public static RenderType entityCutout(Identifier skin) { return new RenderType(); }
        }
    """,
    "net.minecraft.client.renderer.texture.OverlayTexture": "public class OverlayTexture { public static final int NO_OVERLAY = 0; }",
    "net.minecraft.client.renderer.feature.ModelFeatureRenderer": "public class ModelFeatureRenderer { public static class CrumblingOverlay {} }",
    "net.minecraft.client.renderer.state.level.CameraRenderState": "public class CameraRenderState {}",
    "net.minecraft.client.renderer.block.BlockModelRenderState": "public class BlockModelRenderState {}",
    "net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState": """
        import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
        import net.minecraft.world.level.block.entity.BlockEntity;
        public class BlockEntityRenderState {
            public int lightCoords;
            public static void extractBase(BlockEntity entity, BlockEntityRenderState state, CrumblingOverlay overlay) {}
        }
    """,
    "net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider": """
        import net.minecraft.client.renderer.PlayerSkinRenderCache;
        public class BlockEntityRendererProvider {
            public record Context(PlayerSkinRenderCache playerSkinRenderCache) {}
        }
    """,
    "net.minecraft.client.renderer.blockentity.BlockEntityRenderer": """
        import com.mojang.blaze3d.vertex.PoseStack;
        import net.minecraft.client.renderer.SubmitNodeCollector;
        import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
        import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
        import net.minecraft.client.renderer.state.level.CameraRenderState;
        import net.minecraft.world.level.block.entity.BlockEntity;
        import net.minecraft.world.phys.Vec3;
        public interface BlockEntityRenderer<T extends BlockEntity, S extends BlockEntityRenderState> {
            S createRenderState();
            void extractRenderState(T entity, S state, float tick, Vec3 camera, CrumblingOverlay overlay);
            void submit(S state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera);
            int getViewDistance();
            boolean shouldRenderOffScreen();
        }
    """,
    "net.minecraft.client.model.geom.ModelLayers": "public class ModelLayers { public static final Object TNT_MINECART = new Object(); }",
    "net.minecraft.client.renderer.entity.state.MinecartRenderState": "public class MinecartRenderState {}",
    "net.minecraft.client.renderer.entity.EntityRendererProvider": """
        import net.minecraft.client.renderer.PlayerSkinRenderCache;
        public class EntityRendererProvider {
            public record Context(PlayerSkinRenderCache skinCache) {
                public PlayerSkinRenderCache getPlayerSkinRenderCache() { return skinCache; }
            }
        }
    """,
    "net.minecraft.client.renderer.entity.AbstractMinecartRenderer": """
        import com.mojang.blaze3d.vertex.PoseStack;
        import net.minecraft.client.renderer.SubmitNodeCollector;
        import net.minecraft.client.renderer.block.BlockModelRenderState;
        import net.minecraft.client.renderer.entity.state.MinecartRenderState;
        import net.minecraft.world.entity.Entity;
        public class AbstractMinecartRenderer<T extends Entity, S extends MinecartRenderState> {
            protected float shadowRadius;
            public AbstractMinecartRenderer(EntityRendererProvider.Context context, Object layer) {}
            public S createRenderState() { return null; }
            public void extractRenderState(T entity, S state, float partialTicks) {}
            protected void submitMinecartContents(S state, BlockModelRenderState blockState, PoseStack pose, SubmitNodeCollector collector, int light) {}
        }
    """,
    "net.minecraft.world.item.ItemDisplayContext": "public enum ItemDisplayContext { NONE }",
})

# The real TargetBlock is compiled as well, so placement tests exercise its
# actual setPlacedBy implementation instead of a source-text imitation.
STUBS.update({
    "net.minecraft.util.RandomSource": "public class RandomSource {}",
    "net.minecraft.util.Mth": """
        public class Mth {
            public static double frac(double value) { return value - Math.floor(value); }
            public static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
            public static int ceil(double value) { return (int) Math.ceil(value); }
        }
    """,
    "net.minecraft.world.entity.LivingEntity": """
        import net.minecraft.world.level.Level;
        public class LivingEntity extends Entity { public LivingEntity(Level level) { super(level); } }
    """,
    "net.minecraft.world.entity.projectile.Projectile": """
        import net.minecraft.world.entity.Entity;
        import net.minecraft.world.level.Level;
        public class Projectile extends Entity {
            public Projectile(Level level) { super(level); }
            public Entity getOwner() { return null; }
        }
    """,
    "net.minecraft.world.item.context.BlockPlaceContext": """
        import net.minecraft.core.BlockPos;
        import net.minecraft.core.Direction;
        import net.minecraft.world.level.Level;
        public class BlockPlaceContext {
            public Direction getHorizontalDirection() { return Direction.NORTH; }
            public BlockPos getClickedPos() { return new BlockPos(0, 0, 0); }
            public Level getLevel() { return new Level(false); }
        }
    """,
    "net.minecraft.world.level.BlockGetter": """
        import net.minecraft.core.BlockPos;
        import net.minecraft.world.level.block.entity.BlockEntity;
        import net.minecraft.world.level.block.state.BlockState;
        public interface BlockGetter {
            BlockState getBlockState(BlockPos pos);
            BlockEntity getBlockEntity(BlockPos pos);
        }
    """,
    "net.minecraft.world.level.LevelReader": "public interface LevelReader extends BlockGetter {}",
    "net.minecraft.world.level.ScheduledTickAccess": "public interface ScheduledTickAccess {}",
    "net.minecraft.world.level.block.BaseEntityBlock": """
        public class BaseEntityBlock extends Block { public BaseEntityBlock(Properties properties) { super(properties); } }
    """,
    "net.minecraft.world.level.block.Blocks": "public class Blocks { public static final Block AIR = new Block(); }",
    "net.minecraft.world.level.block.RenderShape": "public enum RenderShape { MODEL, INVISIBLE }",
    "net.minecraft.world.level.block.entity.BlockEntityTicker": """
        import net.minecraft.core.BlockPos;
        import net.minecraft.world.level.Level;
        import net.minecraft.world.level.block.state.BlockState;
        public interface BlockEntityTicker<T extends BlockEntity> { void tick(Level level, BlockPos pos, BlockState state, T entity); }
    """,
    "net.minecraft.world.level.block.state.StateDefinition": """
        import net.minecraft.world.level.block.Block;
        import net.minecraft.world.level.block.state.properties.Property;
        public class StateDefinition<O, S> {
            private final O owner;
            public StateDefinition(O owner) { this.owner = owner; }
            @SuppressWarnings(\"unchecked\") public S any() { return (S) new BlockState((Block) owner); }
            public static class Builder<O, S> { public Builder<O, S> add(Property<?>... properties) { return this; } }
        }
    """,
    "net.minecraft.world.level.block.state.properties.EnumProperty": "public class EnumProperty<T> extends Property<T> {}",
    "net.minecraft.world.level.block.state.properties.IntegerProperty": "public class IntegerProperty extends Property<Integer> {}",
    "net.minecraft.world.level.block.state.properties.BooleanProperty": """
        public class BooleanProperty extends Property<Boolean> { public static BooleanProperty create(String name) { return new BooleanProperty(); } }
    """,
    "net.minecraft.world.level.block.state.properties.DoubleBlockHalf": "public enum DoubleBlockHalf { LOWER, UPPER }",
    "net.minecraft.world.level.block.state.properties.BlockStateProperties": """
        import net.minecraft.core.Direction;
        public class BlockStateProperties {
            public static final IntegerProperty POWER = new IntegerProperty();
            public static final EnumProperty<Direction> HORIZONTAL_FACING = new EnumProperty<>();
            public static final EnumProperty<DoubleBlockHalf> DOUBLE_BLOCK_HALF = new EnumProperty<>();
        }
    """,
    "net.minecraft.world.phys.shapes.VoxelShape": "public class VoxelShape {}",
    "net.minecraft.world.phys.shapes.CollisionContext": "public class CollisionContext {}",
    "net.minecraft.world.phys.shapes.Shapes": """
        public class Shapes {
            public static VoxelShape empty() { return new VoxelShape(); }
            public static VoxelShape or(VoxelShape... shapes) { return new VoxelShape(); }
        }
    """,
})

HARNESS = r"""
package com.tacz.guns.block.entity;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.GunMod;
import com.tacz.guns.block.StatueBlock;
import com.tacz.guns.block.TargetBlock;
import com.tacz.guns.client.renderer.block.TargetRenderer;
import com.tacz.guns.client.renderer.entity.TargetMinecartRenderer;
import com.tacz.guns.entity.TargetMinecart;
import com.tacz.guns.init.ModBlocks;
import java.util.*;
import net.minecraft.client.renderer.PlayerSkinRenderCache;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.MinecartRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.ProfileResolver;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

public class BlockEntityRegressionTest {
    private static final BlockPos POS = new BlockPos(4, 5, 6);
    private static void check(boolean result, String message) { if (!result) throw new AssertionError(message); }
    private static void checkDynamicIdentity(TargetBlockEntity entity, GameProfile profile, String message) {
        check(entity.getOwnerProfile() instanceof ResolvableProfile.Dynamic
              && entity.getOwner().id().equals(profile.id()) && entity.getOwner().name().equals(profile.name()), message);
    }
    private static TargetBlockEntity target() { return new TargetBlockEntity(POS, new BlockState()); }
    private static ValueOutput save(TargetBlockEntity target) { ValueOutput out = new ValueOutput(); target.saveAdditional(out); return out; }
    private static TargetBlockEntity load(Map<String, Object> data) { TargetBlockEntity result = target(); result.loadAdditional(new ValueInput(data)); return result; }
    private static int[] ints(UUID u) {
        return new int[]{(int)(u.getMostSignificantBits() >> 32), (int)u.getMostSignificantBits(),
                         (int)(u.getLeastSignificantBits() >> 32), (int)u.getLeastSignificantBits()};
    }

    private static TargetBlockEntity prepareTarget(Level level, TargetBlock block, BlockState state) {
        level.setBlock(POS, state, Block.UPDATE_ALL);
        TargetBlockEntity entity = (TargetBlockEntity) block.newBlockEntity(POS, state);
        level.addBlockEntity(POS, entity);
        return entity;
    }
    private static void placeLikeBlockItem(TargetBlock block, Level level, BlockState state, Player player, ItemStack stack) {
        // Vanilla BlockItem.place calls setPlacedBy before consuming the item.
        block.setPlacedBy(level, POS, state, player, stack);
        stack.consume(1, player);
    }
    private static void namedTargetPlacement() {
        Util.reset();
        ProfileResolver resolver = new ProfileResolver();
        MinecraftServer server = new MinecraftServer(resolver);
        ServerLevel level = new ServerLevel(server);
        TargetBlock block = ModBlocks.TARGET;
        BlockState lower = block.defaultBlockState();
        TargetBlockEntity entity = prepareTarget(level, block, lower);
        ItemStack named = new ItemStack(block); named.set(DataComponents.CUSTOM_NAME, Component.literal("Alice"));
        GameProfile alice = new GameProfile(UUID.randomUUID(), "Alice", Map.of("textures", "alice-skin"));
        resolver.names.put("Alice", alice);
        placeLikeBlockItem(block, level, lower, new Player(level), named);
        check(named.isEmpty(), "survival placement must reach vanilla's later item consumption");
        check(level.getBlockState(POS).getValue(TargetBlock.HALF) == DoubleBlockHalf.LOWER
              && level.getBlockState(POS.above()).getValue(TargetBlock.HALF) == DoubleBlockHalf.UPPER,
              "named target must place both halves");
        check(entity.getOwnerProfile() instanceof ResolvableProfile.Dynamic && entity.getOwner().id() != null
              && "Alice".equals(entity.getOwner().name()) && "Alice".equals(entity.getCustomName().getString()),
              "named placement must retain a non-null-UUID dynamic profile and custom name");
        check(Util.pending() == 1 && server.pending() == 0, "placement must not wait for the server profile lookup");
        Util.runNext(); server.runNext();
        checkDynamicIdentity(entity, alice, "server identity lookup must leave the client profile dynamic");

        ServerLevel invalidLevel = new ServerLevel(server);
        TargetBlockEntity invalid = prepareTarget(invalidLevel, block, lower);
        ItemStack invalidName = new ItemStack(block); invalidName.set(DataComponents.CUSTOM_NAME, Component.literal("not a player name"));
        placeLikeBlockItem(block, invalidLevel, lower, new Player(invalidLevel), invalidName);
        check(invalidName.isEmpty() && invalid.getOwner().id() != null && Util.pending() == 0,
              "an invalid player name must not crash or prevent item consumption");

        Level plainLevel = new Level(false);
        TargetBlockEntity plain = prepareTarget(plainLevel, block, lower);
        ItemStack unnamed = new ItemStack(block);
        placeLikeBlockItem(block, plainLevel, lower, new Player(plainLevel), unnamed);
        check(unnamed.isEmpty() && plain.getOwnerProfile() == null, "unnamed placement should not create an owner");
        Level emptyLevel = new Level(false); TargetBlockEntity empty = prepareTarget(emptyLevel, block, lower);
        ItemStack emptyName = new ItemStack(block); emptyName.set(DataComponents.CUSTOM_NAME, Component.empty());
        placeLikeBlockItem(block, emptyLevel, lower, new Player(emptyLevel), emptyName);
        check(emptyName.isEmpty() && empty.getOwnerProfile() == null, "an empty custom name must not create an owner");

        Level missingLevel = new Level(false); missingLevel.setBlock(POS, lower, Block.UPDATE_ALL);
        ItemStack missing = new ItemStack(block); missing.set(DataComponents.CUSTOM_NAME, Component.literal("Alice"));
        placeLikeBlockItem(block, missingLevel, lower, new Player(missingLevel), missing);
        check(missing.isEmpty(), "a missing block entity must not block item consumption");

        Level creativeLevel = new Level(false); prepareTarget(creativeLevel, block, lower);
        ItemStack creative = new ItemStack(block); creative.set(DataComponents.CUSTOM_NAME, Component.literal("Alice"));
        placeLikeBlockItem(block, creativeLevel, lower, new Player(creativeLevel, true), creative);
        check(!creative.isEmpty(), "creative placement should retain the item");

        Level clientLevel = new Level(true); TargetBlockEntity client = prepareTarget(clientLevel, block, lower);
        ItemStack clientItem = new ItemStack(block); clientItem.set(DataComponents.CUSTOM_NAME, Component.literal("Alice"));
        block.setPlacedBy(clientLevel, POS, lower, new Player(clientLevel), clientItem);
        check(client.getOwnerProfile() == null && client.getCustomName() == null,
              "client placement must not set a server-owned profile or custom name");
    }

    private static void persistence() {
        TargetBlockEntity named = target();
        named.setOwnerName("Notch");
        check(named.getOwner().id() != null, "name-only profile needs a non-null temporary UUID");
        ValueOutput namedSave = save(named);
        check("".equals(namedSave.values.get("owner_uuid")), "temporary UUID must not be saved as authoritative");
        check("Notch".equals(load(namedSave.values).getOwner().name()), "name-only owner must survive reload");

        UUID id = UUID.fromString("11111111-2222-3333-4444-555555555555");
        TargetBlockEntity flat = load(Map.of("owner_uuid", id.toString(), "owner_name", "Notch"));
        check(id.equals(flat.getOwner().id()) && "Notch".equals(flat.getOwner().name()), "flat UUID/name migration");
        TargetBlockEntity flatAgain = load(save(flat).values);
        check(id.equals(flatAgain.getOwner().id()) && "Notch".equals(flatAgain.getOwner().name()), "UUID and name survive another save");
        TargetBlockEntity badUuid = load(Map.of("owner_uuid", "not-a-uuid", "owner_name", "Notch"));
        check("Notch".equals(badUuid.getOwner().name()), "bad UUID must not discard the name");
        check(load(Map.of("owner_uuid", "bad")).getOwner() == null, "bad UUID without a name must not crash");

        ValueInput old = new ValueInput(Map.of("Name", "Notch", "Id", ints(id)));
        TargetBlockEntity legacy = load(Map.of("Owner", old));
        check(id.equals(legacy.getOwner().id()) && "Notch".equals(legacy.getOwner().name()), "legacy Owner compound");
        TargetBlockEntity legacyNameOnly = load(Map.of("Owner", new ValueInput(Map.of("Name", "Notch", "Id", new int[]{1, 2}))));
        check("Notch".equals(legacyNameOnly.getOwner().name()), "malformed legacy UUID should fall back to name");

        String invalid = "a name longer than sixteen characters";
        TargetBlockEntity invalidName = target(); invalidName.setOwnerName(invalid);
        ValueOutput invalidSave = save(invalidName);
        check(!invalidSave.values.containsKey("OwnerProfile"), "invalid name must not be passed to the profile codec");
        check(invalid.equals(load(invalidSave.values).getOwner().name()), "invalid player names must still persist");
        check("Notch".equals(load(Map.of("OwnerProfile", "malformed", "owner_name", "Notch")).getOwner().name()), "corrupt profile falls back to flat keys");
        named.loadAdditional(new ValueInput(Map.of()));
        check(named.getOwner() == null, "missing owner data must clear a previously loaded owner");
    }

    private static void asyncProfiles() {
        Util.reset(); GunMod.LOGGER.warnings.clear();
        ProfileResolver resolver = new ProfileResolver();
        MinecraftServer server = new MinecraftServer(resolver);
        ServerLevel level = new ServerLevel(server);
        GameProfile alice = new GameProfile(UUID.randomUUID(), "Alice", Map.of("textures", "alice-skin"));
        GameProfile bob = new GameProfile(UUID.randomUUID(), "Bob", Map.of("textures", "bob-skin"));
        resolver.names.put("Alice", alice); resolver.names.put("Bob", bob); resolver.ids.put(bob.id(), bob);
        TargetBlockEntity entity = target(); entity.setLevel(level);
        entity.setOwnerName("Alice"); entity.setOwnerName("Bob");
        check(Util.pending() == 2 && server.pending() == 0, "lookups must be queued off the server thread");
        Util.runNext(); server.runNext();
        check("Bob".equals(entity.getOwner().name()), "stale Alice result must not replace Bob");
        Util.runNext(); server.runNext();
        checkDynamicIdentity(entity, bob, "server must preserve Bob's identity while leaving skin lookup dynamic");
        ResolvableProfile update = (ResolvableProfile) entity.getUpdateTag(null).values.get("OwnerProfile");
        check(update instanceof ResolvableProfile.Dynamic && update.partialProfile().id().equals(bob.id()), "client update must contain a dynamic UUID profile");
        checkDynamicIdentity(load(save(entity).values), bob, "dynamic UUID and name must survive reload");

        TargetBlockEntity byId = load(Map.of("owner_uuid", bob.id().toString(), "owner_name", "old name"));
        byId.setLevel(level); Util.runNext(); server.runNext();
        checkDynamicIdentity(byId, bob, "saved UUID must be resolved by ID without freezing the skin");
        TargetBlockEntity external = target(); external.setLevel(level);
        external.setOwner(new GameProfile(bob.id(), "Bob", Map.of("other", "not-textures")));
        check(external.getOwnerProfile() instanceof ResolvableProfile.Dynamic, "properties other than textures must not suppress resolution");
        Util.runNext(); server.runNext();
        checkDynamicIdentity(external, bob, "public GameProfile setter resolves an incomplete profile without freezing the skin");
        GameProfile noTextures = new GameProfile(UUID.randomUUID(), "NoTextures", Map.of("other", "not-textures"));
        resolver.names.put("NoTextures", noTextures);
        TargetBlockEntity incomplete = target(); incomplete.setLevel(level); incomplete.setOwnerName("NoTextures");
        Util.runNext(); server.runNext();
        checkDynamicIdentity(incomplete, noTextures, "a server result without textures must remain resolvable by clients");
        TargetBlockEntity explicit = target(); explicit.setLevel(level); explicit.setOwner(alice);
        check(explicit.getOwnerProfile() instanceof ResolvableProfile.Static && explicit.getOwner().equals(alice),
              "explicit full GameProfiles must retain custom texture properties");
        check(load(save(explicit).values).getOwner().equals(alice), "explicit static profiles remain compatible with older saves");

        TargetBlockEntity removed = target(); removed.setLevel(level); removed.setOwnerName("Alice");
        Util.runNext(); removed.setRemoved(); server.runNext();
        check(removed.getOwnerProfile() instanceof ResolvableProfile.Dynamic, "removed entity must ignore a delayed result");
        TargetBlockEntity cleared = target(); cleared.setLevel(level); cleared.setOwnerName("Alice");
        Util.runNext(); cleared.setOwnerName(null); server.runNext();
        check(cleared.getOwner() == null, "a delayed result must not restore a cleared owner");
        TargetBlockEntity moved = target(); moved.setLevel(level); moved.setOwnerName("Alice");
        Util.runNext();
        ServerLevel otherLevel = new ServerLevel(server); moved.setLevel(otherLevel);
        server.runNext();
        check(moved.getOwnerProfile() instanceof ResolvableProfile.Dynamic && Util.pending() == 1, "old-level result must be ignored and a new lookup started");
        Util.runNext(); server.runNext();
        checkDynamicIdentity(moved, alice, "lookup on the new level should resolve the identity dynamically");

        TargetBlockEntity roundTrip = target(); roundTrip.setLevel(level); roundTrip.setOwnerName("Alice");
        Util.runNext(); // first level-A result is queued
        roundTrip.setLevel(otherLevel); roundTrip.setLevel(level);
        server.runNext();
        check(roundTrip.getOwnerProfile() instanceof ResolvableProfile.Dynamic, "A-to-B-to-A must not accept an old A lookup");
        Util.runNext(); server.runNext();
        check(roundTrip.getOwnerProfile() instanceof ResolvableProfile.Dynamic, "the intermediate B result is stale too");
        Util.runNext(); server.runNext();
        checkDynamicIdentity(roundTrip, alice, "the newest A lookup should resolve");

        TargetBlockEntity reloaded = target(); reloaded.setLevel(level); reloaded.setOwnerName("Alice");
        Util.runNext(); reloaded.setRemoved(); reloaded.setLevel(level); reloaded.clearRemoved();
        server.runNext();
        check(reloaded.getOwnerProfile() instanceof ResolvableProfile.Dynamic, "a result from before removal must not survive reload");
        Util.runNext(); server.runNext();
        checkDynamicIdentity(reloaded, alice, "reloaded entity must resolve again");

        TargetBlockEntity unknown = target(); unknown.setLevel(level); unknown.setOwnerName("Missing");
        Util.runNext(); server.runNext();
        check("Missing".equals(unknown.getOwner().name()) && !GunMod.LOGGER.warnings.isEmpty(), "missing name must remain usable and be logged");
        unknown.setLevel(level);
        check(Util.pending() == 0, "missing profile should not retry every time setLevel receives the same level");
        resolver.fail = true;
        TargetBlockEntity failed = target(); failed.setLevel(level); failed.setOwnerName("Alice");
        Util.runNext(); server.runNext();
        resolver.fail = false; failed.setLevel(level);
        check(Util.pending() == 1, "a failed lookup must be retryable");
        Util.runNext(); server.runNext();
        checkDynamicIdentity(failed, alice, "retry should resolve after a transient failure");

        TargetBlockEntity invalid = target(); invalid.setLevel(level); invalid.setOwnerName("bad name with spaces");
        check(Util.pending() == 0, "invalid names must not start a lookup");
        TargetBlockEntity client = target(); client.setLevel(new Level(true)); client.setOwnerName("Alice");
        check(Util.pending() == 0, "client block entities must not start server lookups");
    }

    private static void statueDrops() {
        MinecraftServer server = new MinecraftServer(new ProfileResolver());
        ServerLevel level = new ServerLevel(server);
        BlockState oldState = new BlockState().setValue(StatueBlock.FACING, Direction.EAST);
        StatueBlockEntity statue = new StatueBlockEntity(POS, oldState); statue.setLevel(level);
        statue.setGun(new ItemStack(1));
        int updatesBeforeRemoval = level.updates;
        statue.preRemoveSideEffects(POS, oldState);
        check(level.drops.size() == 1 && level.drops.get(0).pos().equals(POS.relative(Direction.EAST).above()), "server removal should drop one gun using the old facing");
        check(statue.getGunItem().isEmpty() && level.updates == updatesBeforeRemoval, "removal should clear once without a redundant update");
        statue.preRemoveSideEffects(POS, oldState);
        check(level.drops.size() == 1, "repeated removal must not duplicate the gun");

        StatueBlockEntity fallback = new StatueBlockEntity(POS, new BlockState()); fallback.setLevel(level); fallback.setGun(new ItemStack(1));
        fallback.preRemoveSideEffects(POS, new BlockState());
        check(level.drops.get(1).pos().equals(POS.relative(Direction.NORTH).above()), "missing facing must safely fall back to north");
        Level client = new Level(true);
        StatueBlockEntity clientStatue = new StatueBlockEntity(POS, oldState); clientStatue.setLevel(client); clientStatue.setGun(new ItemStack(1));
        clientStatue.preRemoveSideEffects(POS, oldState);
        check(client.drops.isEmpty() && !clientStatue.getGunItem().isEmpty(), "client removal must not spawn or clear a gun");

        StatueBlockEntity interact = new StatueBlockEntity(POS, oldState); interact.setLevel(level); interact.setGun(new ItemStack(1));
        int before = level.updates; interact.dropItem();
        check(level.updates == before + 1 && interact.getGunItem().isEmpty(), "interaction drop must notify the client");
    }

    private static void minecartProfiles() {
        TargetMinecart cart = new TargetMinecart(new Level(true), 0, 0, 0);
        check(cart.getOwnerProfile() == null && cart.getGameProfile() == null, "unnamed minecart has no owner profile");
        cart.setCustomName(Component.literal("Alex"));
        ResolvableProfile first = cart.getOwnerProfile();
        check(first instanceof ResolvableProfile.Dynamic && "Alex".equals(first.partialProfile().name()), "named minecart uses a dynamic profile");
        check(cart.getGameProfile().id() != null && first == cart.getOwnerProfile(), "partial profile has a UUID and is cached for the same name");
        cart.setCustomName(Component.literal("Steve"));
        ResolvableProfile renamed = cart.getOwnerProfile();
        check(renamed != first && "Steve".equals(cart.getGameProfile().name()), "changing a name must replace the cached profile");
        cart.setCustomName(Component.empty());
        check(cart.getOwnerProfile() == null, "an empty name clears the profile");
        cart.setCustomName(Component.literal("invalid player name"));
        check(cart.getGameProfile().id() != null, "non-player names must still render safely");
        cart.setCustomName(null);
        check(cart.getOwnerProfile() == null && cart.getGameProfile() == null, "removing the name clears the profile");
    }

    private static void skinRenderStates() {
        PlayerSkinRenderCache cache = new PlayerSkinRenderCache();
        TargetRenderer blockRenderer = new TargetRenderer(new BlockEntityRendererProvider.Context(cache));
        TargetRenderer.TargetRenderState blockState = blockRenderer.createRenderState();
        TargetBlockEntity block = new TargetBlockEntity(POS, new BlockState().setValue(TargetBlock.FACING, Direction.NORTH));
        blockRenderer.extractRenderState(block, blockState, 0, new Vec3(), null);
        check(!blockState.hasOwner && blockState.skinTexture == null && cache.requests == 0, "unnamed block must clear the render state");
        block.setOwnerName("Alex"); cache.pending = true;
        blockRenderer.extractRenderState(block, blockState, 0, new Vec3(), null);
        check(blockState.hasOwner && "default:Alex".equals(blockState.skinTexture.value()), "pending block skin lookup should use the default");
        cache.pending = false; blockRenderer.extractRenderState(block, blockState, 0, new Vec3(), null);
        check(blockState.hasOwner && "skin:Alex".equals(blockState.skinTexture.value()) && cache.lastProfile == block.getOwnerProfile(),
              "block renderer must use the dynamically resolved skin from the cache");
        SubmitNodeCollector blockCollector = new SubmitNodeCollector();
        blockRenderer.submit(blockState, new PoseStack(), blockCollector, new CameraRenderState());
        check(blockCollector.geometryCount == 1, "named block should submit its head geometry");
        cache.fail = true;
        blockRenderer.extractRenderState(block, blockState, 0, new Vec3(), null);
        check("default:Alex".equals(blockState.skinTexture.value()), "failed block lookup should use the default skin");
        GameProfile byId = new GameProfile(UUID.randomUUID(), "ById");
        block.setOwner(byId); cache.fail = false; cache.pending = true;
        blockRenderer.extractRenderState(block, blockState, 0, new Vec3(), null);
        check("default:".equals(blockState.skinTexture.value()) && cache.lastProfile.partialProfile().id().equals(byId.id()),
              "a pending UUID skin must default using the correct UUID profile");
        cache.pending = false; blockRenderer.extractRenderState(block, blockState, 0, new Vec3(), null);
        check(("skin:" + byId.id()).equals(blockState.skinTexture.value()), "a UUID owner must use the skin when the cache completes");
        block.setOwnerName(null);
        blockRenderer.extractRenderState(block, blockState, 0, new Vec3(), null);
        check(!blockState.hasOwner && blockState.skinTexture == null, "cleared block owner must not reuse a previous skin");

        class CartRenderer extends TargetMinecartRenderer {
            CartRenderer(EntityRendererProvider.Context context) { super(context); }
            void submitContents(MinecartRenderState state, SubmitNodeCollector collector) {
                super.submitMinecartContents(state, new BlockModelRenderState(), new PoseStack(), collector, 0);
            }
        }
        CartRenderer cartRenderer = new CartRenderer(new EntityRendererProvider.Context(cache));
        TargetMinecartRenderer.TargetMinecartRenderState cartState = (TargetMinecartRenderer.TargetMinecartRenderState) cartRenderer.createRenderState();
        TargetMinecart cart = new TargetMinecart(new Level(true), 0, 0, 0);
        cartRenderer.extractRenderState(cart, cartState, 0);
        check(cartState.skinTexture == null, "unnamed cart must clear the render state");
        cart.setCustomName(Component.literal("Alex")); cache.pending = true; cartRenderer.extractRenderState(cart, cartState, 0);
        check("default:Alex".equals(cartState.skinTexture.value()), "pending cart skin lookup should use the default");
        cache.pending = false; cartRenderer.extractRenderState(cart, cartState, 0);
        check("skin:Alex".equals(cartState.skinTexture.value()) && cache.lastProfile == cart.getOwnerProfile(),
              "cart renderer must use the dynamically resolved skin from the cache");
        SubmitNodeCollector cartCollector = new SubmitNodeCollector(); cartRenderer.submitContents(cartState, cartCollector);
        check(cartCollector.geometryCount == 2, "named cart should submit both head layers");
        cart.setCustomName(Component.literal("Steve")); cartRenderer.extractRenderState(cart, cartState, 0);
        check("skin:Steve".equals(cartState.skinTexture.value()), "renamed cart must use the new skin");
        cache.fail = true; cartRenderer.extractRenderState(cart, cartState, 0);
        check("default:Steve".equals(cartState.skinTexture.value()), "failed cart lookup should use the default skin");
        cart.setCustomName(null); cache.fail = false; cartRenderer.extractRenderState(cart, cartState, 0);
        check(cartState.skinTexture == null, "cleared cart name must not reuse a previous skin");
    }

    public static void main(String[] args) {
        persistence(); namedTargetPlacement(); asyncProfiles(); statueDrops(); minecartProfiles(); skinRenderStates();
        System.out.println("Block, target-minecart and renderer regressions: OK (Minecraft API doubles; not an in-game test)");
    }
}
"""


def main() -> None:
    javac = shlex.split(os.environ.get("TACZ_JAVAC", "javac"))
    java = shlex.split(os.environ.get("TACZ_JAVA", "java"))
    with tempfile.TemporaryDirectory(prefix="tacz-block-test-") as tmp:
        temp = Path(tmp)
        sources = []
        for name, body in STUBS.items():
            package, _, classname = name.rpartition(".")
            path = temp / "src" / Path(*package.split(".")) / f"{classname}.java"
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(f"package {package};\n{body}\n", encoding="utf-8")
            sources.append(str(path))
        harness = temp / "src/com/tacz/guns/block/entity/BlockEntityRegressionTest.java"
        harness.parent.mkdir(parents=True, exist_ok=True)
        harness.write_text(HARNESS, encoding="utf-8")
        sources.extend((
            str(harness),
            str(ROOT / "src/main/java/com/tacz/guns/block/TargetBlock.java"),
            str(ROOT / "src/main/java/com/tacz/guns/block/entity/StatueBlockEntity.java"),
            str(ROOT / "src/main/java/com/tacz/guns/block/entity/TargetBlockEntity.java"),
            str(ROOT / "src/main/java/com/tacz/guns/entity/TargetMinecart.java"),
            str(ROOT / "src/main/java/com/tacz/guns/client/renderer/block/TargetRenderer.java"),
            str(ROOT / "src/main/java/com/tacz/guns/client/renderer/entity/TargetMinecartRenderer.java"),
        ))
        output = temp / "classes"
        output.mkdir()
        subprocess.run(javac + ["-source", "17", "-target", "17", "-encoding", "UTF-8", "-d", str(output)] + sources, check=True)
        subprocess.run(java + ["-cp", str(output), "com.tacz.guns.block.entity.BlockEntityRegressionTest"], check=True)


if __name__ == "__main__":
    main()
