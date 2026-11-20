package com.tacz.guns.block.entity;

import com.mojang.authlib.GameProfile;
import com.tacz.guns.entity.TargetMinecart;
import com.tacz.guns.init.ModBlocks;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.storage.TagValueInput;

/** Controls only the external profile-service replies. Uses the real server executor, native
 * profile codec, removal lifecycle and client update tags; no Mojang account/network required. */
public final class TargetIdentitySmoke {
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
            h.assertTrue(target.getOwnerProfile() instanceof ResolvableProfile.Dynamic && target.getOwner().id().equals(id)
                    && "Bob".equals(target.getOwner().name()), "Stale identity applied or texture resolution frozen");
            var copy = new TargetBlockEntity(pos, ModBlocks.TARGET.defaultBlockState());
            copy.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, world.registryAccess(), target.getUpdateTag(world.registryAccess())));
            h.assertTrue(copy.getOwnerProfile() instanceof ResolvableProfile.Dynamic && target.getOwner().equals(copy.getOwner()),
                    "Native update lost UUID/name hint or froze the skin");
            target.setOwner(ResolvableProfile.createUnresolved("Removed"));
            target.setRemoved();
            calls.remove().complete(Optional.of(new GameProfile(UUID.randomUUID(), "Wrong")));
            h.runAfterDelay(2, () -> {
                h.assertTrue("Removed".equals(target.getOwner().name()), "Removed target accepted a delayed result");
                var invalid = "not a valid player name";
                copy.setOwner(ResolvableProfile.createUnresolved(invalid));
                var tag = copy.saveWithoutMetadata(world.registryAccess());
                copy.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, world.registryAccess(), tag));
                h.assertTrue(invalid.equals(copy.getOwner().name()), "Anvil name rejected by the profile codec was lost");
                h.succeed();
            });
        });
    }
}
