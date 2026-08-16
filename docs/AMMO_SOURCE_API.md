# Ammunition source API (downstream integration)

TaCZ normally checks and consumes physical ammunition through a living entity's
`tacz$getItemHandler`. Mods whose shooters own a different inventory can replace that source
without mixing into TaCZ internals.

The public API is in `com.tacz.guns.api.item.ammo`:

- `AmmoSource` — read and consume operations supplied by the downstream mod;
- `AmmoSourceProvider` — chooses a source for an entity/gun pair;
- `AmmoSourceRegistry.EVENT` — provider registration and the common TaCZ dispatch point.

## Registering a source

Register during **common initialization**, not client-only initialization. Availability is queried on
both logical sides for prediction/animation, while consumption is authoritative gameplay logic.

```java
public final class MaidTaCZCompat {
    private static final AmmoSource MAID_BACKPACK = new AmmoSource() {
        @Override
        public boolean hasAmmo(LivingEntity shooter, ItemStack gunItem) {
            EntityMaid maid = (EntityMaid) shooter;
            return hasCompatibleAmmo(maid.getAllInv(), gunItem);
        }

        @Override
        public int consumeAmmo(LivingEntity shooter, ItemStack gunItem, int requestedAmount) {
            EntityMaid maid = (EntityMaid) shooter;
            return consumeCompatibleAmmo(maid.getAllInv(), gunItem, requestedAmount);
        }
    };

    public static void init() {
        AmmoSourceRegistry.EVENT.register((shooter, gunItem) ->
                shooter instanceof EntityMaid ? MAID_BACKPACK : null);
    }
}
```

The example deliberately leaves the downstream inventory type opaque. It does not have to implement
TaCZ's internal `IItemHandler`; the `AmmoSource` implementation can use its owning mod's native
inventory API.

Provider rules:

1. Return `null` when the provider does not own the entity's ammunition.
2. The first non-null provider in registration order wins.
3. `hasAmmo` must be read-only and must agree with what `consumeAmmo` can consume.
4. `consumeAmmo` returns a value in `0..requestedAmount`; TaCZ clamps invalid values defensively.
5. Register the provider on both physical sides if the custom shooter can be rendered client-side.

If no provider accepts the entity, behavior is unchanged: TaCZ uses the entity's normal item handler
and its existing `IAmmo` / `IAmmoBox` matching and extraction rules. Mods that already use TaCZ's
`IItemHandler` can reuse `AmmoSourceRegistry.hasAmmo(IItemHandler, ItemStack)` and
`AmmoSourceRegistry.consumeAmmo(IItemHandler, ItemStack, int)`.

## Covered paths

The registry is used by all inventory-ammunition paths that previously required downstream mixins:

- `AbstractGunItem#canReload` and `#hasInventoryAmmo`;
- `LivingEntityShoot#consumeAmmoFromPlayer`;
- `ModernKineticGunScriptAPI#consumeAmmoFromPlayer` and `#hasAmmoToConsume`;
- `GunAnimationStateContext#hasAmmoToConsume`.

The normal shoot and bolt checks already call `AbstractGunItem#hasInventoryAmmo`, so they inherit the
registered source as well. Dummy ammunition, infinite reload data, and creative-mode checks remain in
their original TaCZ call sites and run before source dispatch.

`GunAnimationStateContext` also exposes the named protected method
`hasAmmoToConsumeInEntity(Entity)`. It replaces the former synthetic
`lambda$hasAmmoToConsume$...` implementation detail, but new integrations should register an
`AmmoSource` rather than mix into that method.

## Compatibility notice

Changes to these contracts or to the legacy ammo call sites listed above must be called out in the
1.21.11 changelog/release notes. The registry is the supported downstream extension point; synthetic
lambda names and other compiler-generated methods are not API.
