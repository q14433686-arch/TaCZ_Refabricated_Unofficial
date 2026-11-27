package com.tacz.guns.block.entity;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.tacz.guns.init.ModBlocks;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.storage.TagValueInput;

/** Controls only the external profile-service replies. Uses the real server executor, native
 * profile codec, removal lifecycle and client update tags; no Mojang account/network required. */
public final class TargetIdentitySmoke {




    public static void explicitSkinAndIncompleteIdentityRoundTrip(GameTestHelper h) {
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
        var registries = h.getLevel().registryAccess();
        var legacy = new net.minecraft.nbt.CompoundTag();
        legacy.putString("owner_uuid", id.toString());
        legacy.putString("owner_name", "Alex");
        legacy.putString("CustomName", "{\"text\":\"legacy label\"}");
        target.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, registries, legacy));
        h.assertTrue(id.equals(target.getGameProfile().id()) && "Alex".equals(target.getGameProfile().name())
                && "legacy label".equals(target.getCustomName().getString()), "Flat UUID/name or JSON name migration lost data");
        var owner = new net.minecraft.nbt.CompoundTag();
        owner.putString("Name", "Alex");
        owner.putIntArray("Id", net.minecraft.core.UUIDUtil.uuidToIntArray(id));
        legacy = new net.minecraft.nbt.CompoundTag();legacy.put("Owner", owner);
        target.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, registries, legacy));
        h.assertTrue(id.equals(target.getGameProfile().id()) && "Alex".equals(target.getGameProfile().name()), "Legacy Owner compound migration lost identity");
        var canonical = target.saveWithoutMetadata(registries);
        var alternate = new net.minecraft.nbt.CompoundTag();
        alternate.put("OwnerProfile", canonical.get("profile").copy());
        target.loadAdditional(TagValueInput.create(ProblemReporter.DISCARDING, registries, alternate));
        h.assertTrue(id.equals(target.getGameProfile().id()), "Alternate profile codec migration lost UUID");
        target.setOwner(null);
        h.assertTrue(target.getGameProfile() == null, "Native null setter no longer clears the owner");
        System.out.println("PORT_TARGET_PROFILE: explicit/native profile identity and texture update roundtrip passed");
        h.succeed();
    }

    public static void lateProfileLookupCannotReplaceChangedOwner(GameTestHelper h) {
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
                System.out.println("PORT_TARGET_IDENTITY: controlled asynchronous server replies honored latest-owner/removal generations and native UUID/name-hint persistence; no external account service tested");
                h.succeed();
            });
        });
    }
}
