package com.tacz.guns.block.entity;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.tacz.guns.block.TargetBlock;
import com.tacz.guns.entity.TargetMinecart;
import com.tacz.guns.init.ModBlocks;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Controls only the external profile-service replies. Uses the real server executor, native
 * profile codec, removal lifecycle and client update tags; no Mojang account/network required. */
public final class TargetIdentitySmoke {
    @GameTest
    public void namedTargetConsumesPlacementItemInSurvivalOnly(GameTestHelper h) {
        var world = h.getLevel();
        var support = h.absolutePos(BlockPos.ZERO);
        var foot = support.above();
        world.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
        var hit = new BlockHitResult(Vec3.atBottomCenterOf(foot), Direction.UP, support, false);
        // A legal anvil label, deliberately not an account: this test never contacts the service.
        var label = Component.literal("named placement fixture");
        for (var mode : new GameType[]{GameType.SURVIVAL, GameType.CREATIVE}) {
            var player = h.makeMockPlayer(mode);
            // The helper overrides gameMode() but never installs the mode's abilities.
            // Vanilla BlockItem.place -> ItemStack.consume consults instabuild, not gameMode().
            mode.updatePlayerAbilities(player.getAbilities());
            var hand = InteractionHand.MAIN_HAND;
            var stack = new ItemStack(ModBlocks.TARGET);
            stack.set(DataComponents.CUSTOM_NAME, label);
            player.setItemInHand(hand, stack);
            h.assertTrue(stack.useOn(new UseOnContext(player, hand, hit)).consumesAction(), "Vanilla placement refused: " + mode);
            h.assertTrue(stack.getCount() == (mode == GameType.SURVIVAL ? 0 : 1), "Wrong placement debit: " + mode);
            h.assertTrue(world.getBlockState(foot.above()).getValue(TargetBlock.HALF) == DoubleBlockHalf.UPPER
                    && world.getBlockEntity(foot) instanceof TargetBlockEntity target && label.equals(target.getCustomName())
                    && label.getString().equals(target.getGameProfile().name()), "Placement lost upper half or owner");
            world.removeBlock(foot, false);
        }
        h.succeed();
    }

    @GameTest
    public void minecartProfileFollowsName(GameTestHelper h) {
        var cart = new TargetMinecart(TargetMinecart.TYPE, h.getLevel());
        h.assertTrue(cart.getGameProfile() == null, "Unnamed minecart must not resolve a skin");
        cart.setCustomName(Component.literal("Alex"));
        var alex = cart.getOwnerProfile();
        h.assertTrue(alex instanceof ResolvableProfile.Dynamic && alex == cart.getOwnerProfile()
                && alex.partialProfile().equals(cart.getGameProfile()), "Name-only cart profile must be cached and readable");
        cart.setCustomName(Component.literal("Steve"));
        h.assertTrue(alex != cart.getOwnerProfile() && "Steve".equals(cart.getGameProfile().name()), "Stale minecart skin after renaming");
        cart.setCustomName(null);
        h.assertTrue(cart.getGameProfile() == null, "Clearing the name retained the last profile");
        h.succeed();
    }

    @GameTest
    public void explicitSkinAndIncompleteIdentityRoundTrip(GameTestHelper h) {
        var target = new TargetBlockEntity(BlockPos.ZERO, ModBlocks.TARGET.defaultBlockState());
        var id = UUID.randomUUID();
        for (String key : new String[]{"other", "textures"}) {
            var properties = new PropertyMap(ImmutableMultimap.of(key, new Property(key, "fixture", null)));
            var input = new GameProfile(id, "Alex", properties);
            target.setGameProfile(input);
            var registries = h.getLevel().registryAccess();
            target.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, registries, target.getUpdateTag(registries)));
            h.assertTrue(input.id().equals(target.getGameProfile().id()) && input.name().equals(target.getGameProfile().name()), "Identity lost on update");
            if (key.equals("textures")) h.assertTrue(target.getOwnerProfile() instanceof ResolvableProfile.Static
                    && input.equals(target.getGameProfile()), "Explicit texture lost");
            else h.assertTrue(target.getOwnerProfile() instanceof ResolvableProfile.Dynamic, "Profile without textures was frozen");
        }
        target.setOwner(null);
        h.assertTrue(target.getGameProfile() == null, "Native null setter no longer clears the owner");
        h.succeed();
    }

    @GameTest
    public void lateProfileLookupCannotReplaceChangedOwner(GameTestHelper h) {
        var world = h.getLevel();
        var pos = h.absolutePos(BlockPos.ZERO);
        var calls = new ArrayDeque<CompletableFuture<Optional<GameProfile>>>();
        var target = new TargetBlockEntity(pos, ModBlocks.TARGET.defaultBlockState()) {
            @Override CompletableFuture<Optional<GameProfile>> lookup(ServerLevel level, String name, UUID id) {
                var future = new CompletableFuture<Optional<GameProfile>>();
                calls.add(future);
                return future;
            }
        };
        target.setLevel(world);
        target.setOwner(ResolvableProfile.createUnresolved("Alice"));
        target.setOwner(ResolvableProfile.createUnresolved("Bob"));
        h.assertTrue(calls.size() == 2, "Both identities must resolve asynchronously");
        var alice = calls.remove(); var bob = calls.remove();
        var id = UUID.randomUUID();
        bob.complete(Optional.of(new GameProfile(id, "Bob")));
        alice.complete(Optional.of(new GameProfile(UUID.randomUUID(), "Alice")));
        h.runAfterDelay(2, () -> {
            h.assertTrue(target.getOwnerProfile() instanceof ResolvableProfile.Dynamic && target.getGameProfile().id().equals(id)
                    && "Bob".equals(target.getGameProfile().name()), "Stale identity applied or texture resolution frozen");
            var copy = new TargetBlockEntity(pos, ModBlocks.TARGET.defaultBlockState());
            copy.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, world.registryAccess(), target.getUpdateTag(world.registryAccess())));
            h.assertTrue(copy.getOwnerProfile() instanceof ResolvableProfile.Dynamic && target.getGameProfile().equals(copy.getGameProfile()),
                    "Native update lost UUID/name hint or froze the skin");
            target.setOwner(ResolvableProfile.createUnresolved("Removed"));
            target.setRemoved();
            calls.remove().complete(Optional.of(new GameProfile(UUID.randomUUID(), "Wrong")));
            h.runAfterDelay(2, () -> {
                h.assertTrue("Removed".equals(target.getGameProfile().name()), "Removed target accepted a delayed result");
                var invalid = "not a valid player name";
                copy.setOwner(ResolvableProfile.createUnresolved(invalid));
                var tag = copy.saveWithoutMetadata(world.registryAccess());
                copy.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, world.registryAccess(), tag));
                h.assertTrue(invalid.equals(copy.getGameProfile().name()), "Anvil name rejected by the profile codec was lost");
                h.succeed();
            });
        });
    }
}
