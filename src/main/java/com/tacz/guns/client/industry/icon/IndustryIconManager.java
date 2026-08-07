package com.tacz.guns.client.industry.icon;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.industry.item.IndustryItemDataAccessor;
import com.tacz.guns.industry.magazine.MagazineItemDataAccessor;
import com.tacz.guns.util.ItemNbtUtils;
import com.tacz.guns.util.ResourceScanner;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Client-resource mapping from one NBT-identified TACZ stack to a flat icon texture.
 *
 * <p>The mapping deliberately lives under
 * {@code assets/<namespace>/industry_icons/<path>.json}, rather than in Java
 * or in generated item registrations. A resource pack may add
 * a mapping file in its own namespace for a third-party gun pack; gameplay and
 * registries stay unchanged.  See {@code docs/INDUSTRY_ICON_MAPPING.md} for the
 * schema and precedence rules.</p>
 *
 * <p>Only stable identity fields are considered: item id, {@code AmmoId},
 * {@code MagazineFamily}, {@code MagazineAmmoId}, {@code MagazineCapacity}, {@code FeedDeviceKind},
 * {@code CartridgeCaliber}, {@code ProjectileType}, {@code IndustryPartKind},
 * {@code IndustryPlatform}, and {@code DieTargetKind}. Mutable round counts are
 * intentionally excluded so a loaded magazine does not force the GUI icon atlas
 * to allocate a new icon every reload tick.</p>
 */
@Environment(EnvType.CLIENT)
public final class IndustryIconManager extends SimplePreparableReloadListener<List<IndustryIconManager.LoadedEntry>>
        implements IdentifiableResourceReloadListener {
    public static final IndustryIconManager INSTANCE = new IndustryIconManager();

    private static final Identifier RELOAD_ID = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "industry_icon_manager");
    /**
     * A dismantled, durable component deliberately has a separate registry id
     * so it cannot satisfy an ordinary production-assembly ingredient. Its
     * stable platform/kind NBT identity is nevertheless the same physical part
     * identity as the upstream production component, so it may reuse that
     * component's authored icon when no service-specific icon is supplied.
     */
    private static final Identifier SERVICE_COMPONENT_ITEM = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "service_component");
    private static final Identifier GUN_COMPONENT_ITEM = Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "gun_component");
    private static final FileToIdConverter ICON_FILES = FileToIdConverter.json("industry_icons");
    /** Mapping JSON stores model-style ids such as {@code tacz_extra:item/ammo_9mm}. */
    private static final FileToIdConverter TEXTURE_FILES = new FileToIdConverter("textures", ".png");

    /** Entries are sorted once at reload time, so render-time lookup is deterministic and allocation-free. */
    private volatile List<LoadedEntry> entries = List.of();

    private IndustryIconManager() {
    }

    @Override
    protected List<LoadedEntry> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<Identifier, List<JsonElement>> files = ResourceScanner.scanDirectoryAll(
                manager, ICON_FILES, ClientAssetsManager.GSON
        );
        List<Map.Entry<Identifier, List<JsonElement>>> orderedFiles = new ArrayList<>(files.entrySet());
        orderedFiles.sort(Comparator.comparing(entry -> entry.getKey().toString()));

        List<LoadedEntry> loaded = new ArrayList<>();
        for (Map.Entry<Identifier, List<JsonElement>> file : orderedFiles) {
            int packLayer = 0;
            for (JsonElement element : file.getValue()) {
                try {
                    IconFile iconFile = ClientAssetsManager.GSON.fromJson(element, IconFile.class);
                    if (iconFile == null || iconFile.entries == null) {
                        GunMod.LOGGER.warn("Ignoring empty industry icon mapping file {}", file.getKey());
                        packLayer++;
                        continue;
                    }
                    for (int index = 0; index < iconFile.entries.size(); index++) {
                        IconEntry source = iconFile.entries.get(index);
                        LoadedEntry entry = LoadedEntry.create(source, file.getKey(), packLayer, index, manager);
                        if (entry != null) {
                            loaded.add(entry);
                        }
                    }
                } catch (JsonParseException | IllegalArgumentException exception) {
                    GunMod.LOGGER.warn("Ignoring invalid industry icon mapping file {}", file.getKey(), exception);
                }
                packLayer++;
            }
        }

        loaded.sort(Comparator.comparingInt(LoadedEntry::priority).reversed()
                .thenComparing(Comparator.comparingInt(LoadedEntry::specificity).reversed())
                .thenComparing(LoadedEntry::stableId));
        return List.copyOf(loaded);
    }

    @Override
    protected void apply(List<LoadedEntry> prepared, ResourceManager manager, ProfilerFiller profiler) {
        entries = prepared;
    }

    /**
     * Resolve the highest-priority icon texture that matches this stack.
     *
     * <p>Service components may declare an explicit {@code service_component}
     * mapping, which always wins.  When they do not, they fall through to the
     * exact {@code gun_component} mapping with the same platform/kind identity.
     * The durable registry item remains distinct for recipe matching; this is
     * only a client-resource visual alias and prevents a missing service-only
     * texture from becoming the purple/black missing-texture sprite.</p>
     */
    public Optional<Identifier> resolveTexture(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        IconIdentity identity = IconIdentity.of(stack);
        Optional<Identifier> direct = resolve(identity);
        if (direct.isPresent() || !SERVICE_COMPONENT_ITEM.equals(identity.item())) {
            return direct;
        }
        return resolve(identity.withItem(GUN_COMPONENT_ITEM));
    }

    /** Resolve an identity directly; useful for renderer diagnostics and future REI integration. */
    public Optional<Identifier> resolve(IconIdentity identity) {
        for (LoadedEntry entry : entries) {
            if (entry.matches(identity)) {
                return Optional.of(entry.texture());
            }
        }
        return Optional.empty();
    }

    /**
     * Convert a model-style icon id to the direct PNG path required by
     * {@code RenderTypes.entityTranslucent}. Vanilla item models perform this
     * conversion through their texture atlas; our flat special renderer bypasses
     * that model path and must do it explicitly.
     */
    static Identifier toTextureFile(Identifier iconTextureId) {
        return TEXTURE_FILES.idToFile(iconTextureId);
    }

    /**
     * A value-semantic visual key for the 26.2 GUI item atlas.
     *
     * <p>The dynamic item model must not use the ItemStack object itself as an
     * atlas identity because ItemStack has identity equality.  This key changes
     * exactly when an icon selector can change and never when only ammo count
     * changes.</p>
     */
    public static String visualIdentity(ItemStack stack) {
        return stack.isEmpty() ? "empty" : IconIdentity.of(stack).key();
    }

    @Override
    public Identifier getFabricId() {
        return RELOAD_ID;
    }

    /** Public, immutable selector input shared by the runtime resolver and model-cache key. */
    public record IconIdentity(Identifier item,
                               String ammoId,
                               String magazineFamily,
                               String magazineAmmoId,
                               int magazineCapacity,
                               String feedDeviceKind,
                               String cartridgeCaliber,
                               String projectileType,
                               String industryPartKind,
                               String industryPlatform,
                               String dieTargetKind) {
        private static final String EMPTY = "";

        public static IconIdentity of(ItemStack stack) {
            Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId == null) {
                itemId = Identifier.fromNamespaceAndPath("minecraft", "air");
            }

            CompoundTag tag = ItemNbtUtils.getTag(stack);
            String ammoId = EMPTY;
            if (stack.getItem() instanceof IAmmo ammo) {
                Identifier resolvedAmmo = ammo.getAmmoId(stack);
                ammoId = resolvedAmmo == null ? EMPTY : resolvedAmmo.toString();
            } else {
                ammoId = tag.getStringOr("AmmoId", EMPTY);
            }

            // Reading the common tag fields directly keeps mappings useful for
            // third-party generic stacks which intentionally reuse TACZ's data
            // contract without extending one of the concrete TACZ item classes.
            String magazineFamily = tag.getStringOr(MagazineItemDataAccessor.MAGAZINE_FAMILY_TAG, EMPTY);
            String magazineAmmoId = tag.getStringOr(MagazineItemDataAccessor.MAGAZINE_AMMO_ID_TAG, EMPTY);
            int magazineCapacity = Math.max(0, tag.getIntOr(MagazineItemDataAccessor.MAGAZINE_CAPACITY_TAG, 0));
            String feedDeviceKind = tag.getStringOr(MagazineItemDataAccessor.FEED_DEVICE_KIND_TAG, EMPTY);
            String cartridgeCaliber = tag.getStringOr(IndustryItemDataAccessor.CARTRIDGE_CALIBER_TAG, EMPTY);
            String projectileType = tag.getStringOr(IndustryItemDataAccessor.PROJECTILE_TYPE_TAG, EMPTY);
            String partKind = tag.getStringOr(IndustryItemDataAccessor.PART_KIND_TAG, EMPTY);
            String platform = tag.getStringOr(IndustryItemDataAccessor.PLATFORM_TAG, EMPTY);
            String dieTargetKind = tag.getStringOr(IndustryItemDataAccessor.DIE_TARGET_KIND_TAG, EMPTY);
            return new IconIdentity(itemId, ammoId, magazineFamily, magazineAmmoId, magazineCapacity, feedDeviceKind,
                    cartridgeCaliber, projectileType, partKind, platform, dieTargetKind);
        }

        /** Return the same NBT visual identity under another registry-item selector. */
        private IconIdentity withItem(Identifier replacementItem) {
            return new IconIdentity(replacementItem, ammoId, magazineFamily, magazineAmmoId, magazineCapacity,
                    feedDeviceKind, cartridgeCaliber, projectileType, industryPartKind, industryPlatform,
                    dieTargetKind);
        }

        public String key() {
            return item + "|" + ammoId + "|" + magazineFamily + "|" + magazineAmmoId + "|"
                    + magazineCapacity + "|" + feedDeviceKind + "|" + cartridgeCaliber + "|" + projectileType + "|"
                    + industryPartKind + "|" + industryPlatform + "|" + dieTargetKind;
        }
    }

    /** Raw JSON root. Extra documentation fields are harmlessly ignored by Gson. */
    private static final class IconFile {
        @SerializedName("entries")
        private List<IconEntry> entries = List.of();
    }

    /** Raw JSON entry. The optional {@code coverage} field belongs to the author catalog, not the renderer. */
    private static final class IconEntry {
        @SerializedName("id")
        private String id;
        @SerializedName("item")
        private String item;
        @SerializedName("texture")
        private String texture;
        @SerializedName("priority")
        private int priority;
        @SerializedName("match")
        private IconMatch match;
    }

    /** All supported selectors; every declared field is an AND condition. */
    private static final class IconMatch {
        @SerializedName("ammo_id")
        private String ammoId;
        @SerializedName("magazine_family")
        private String magazineFamily;
        @SerializedName("magazine_ammo_id")
        private String magazineAmmoId;
        @SerializedName("magazine_capacity")
        private Integer magazineCapacity;
        @SerializedName("feed_device_kind")
        private String feedDeviceKind;
        @SerializedName("cartridge_caliber")
        private String cartridgeCaliber;
        @SerializedName("projectile_type")
        private String projectileType;
        @SerializedName("industry_part_kind")
        private String industryPartKind;
        @SerializedName("industry_platform")
        private String industryPlatform;
        @SerializedName("die_target_kind")
        private String dieTargetKind;

        private boolean matches(IconIdentity identity) {
            return equalsOrBlank(ammoId, identity.ammoId())
                    && equalsOrBlank(magazineFamily, identity.magazineFamily())
                    && equalsOrBlank(magazineAmmoId, identity.magazineAmmoId())
                    && equalsOrNull(magazineCapacity, identity.magazineCapacity())
                    && equalsOrBlank(feedDeviceKind, identity.feedDeviceKind())
                    && equalsOrBlank(cartridgeCaliber, identity.cartridgeCaliber())
                    && equalsOrBlank(projectileType, identity.projectileType())
                    && equalsOrBlank(industryPartKind, identity.industryPartKind())
                    && equalsOrBlank(industryPlatform, identity.industryPlatform())
                    && equalsOrBlank(dieTargetKind, identity.dieTargetKind());
        }

        private int specificity() {
            int result = 0;
            result += isPresent(ammoId) ? 1 : 0;
            result += isPresent(magazineFamily) ? 1 : 0;
            result += isPresent(magazineAmmoId) ? 1 : 0;
            result += magazineCapacity == null ? 0 : 1;
            result += isPresent(feedDeviceKind) ? 1 : 0;
            result += isPresent(cartridgeCaliber) ? 1 : 0;
            result += isPresent(projectileType) ? 1 : 0;
            result += isPresent(industryPartKind) ? 1 : 0;
            result += isPresent(industryPlatform) ? 1 : 0;
            result += isPresent(dieTargetKind) ? 1 : 0;
            return result;
        }
    }

    /**
     * Public only because this type appears in the generic superclass signature.
     * Its constructor and all state remain private implementation details; icon
     * mappings are exposed through {@link IndustryIconManager#resolveTexture(ItemStack)} instead.
     */
    public static final class LoadedEntry {
        private final String stableId;
        private final Identifier item;
        private final Identifier texture;
        private final int priority;
        private final IconMatch match;
        private final int specificity;

        private LoadedEntry(String stableId, Identifier item, Identifier texture, int priority, IconMatch match,
                            int specificity) {
            this.stableId = stableId;
            this.item = item;
            this.texture = texture;
            this.priority = priority;
            this.match = match;
            this.specificity = specificity;
        }

        private String stableId() {
            return stableId;
        }

        private Identifier texture() {
            return texture;
        }

        private int priority() {
            return priority;
        }

        private int specificity() {
            return specificity;
        }

        @Nullable
        private static LoadedEntry create(@Nullable IconEntry source, Identifier file, int packLayer, int index,
                                          ResourceManager manager) {
            if (source == null || isBlank(source.id) || isBlank(source.item) || isBlank(source.texture)) {
                GunMod.LOGGER.warn("Ignoring incomplete industry icon entry {}#{}:{}", file, packLayer, index);
                return null;
            }
            Identifier item = Identifier.tryParse(source.item);
            Identifier logicalTexture = Identifier.tryParse(source.texture);
            if (item == null || logicalTexture == null) {
                GunMod.LOGGER.warn("Ignoring industry icon entry {} in {}: invalid item or texture id", source.id, file);
                return null;
            }
            Identifier textureFile = toTextureFile(logicalTexture);
            if (manager.getResource(textureFile).isEmpty()) {
                // Ignore a bad external reference so the renderer reaches its
                // normal TACZ fallback instead of submitting a missing texture.
                GunMod.LOGGER.warn("Ignoring industry icon entry {} in {}: texture {} ({}) is absent",
                        source.id, file, logicalTexture, textureFile);
                return null;
            }
            IconMatch match = source.match == null ? new IconMatch() : source.match;
            String stableId = source.id + "@" + file + "#" + packLayer + ":" + index;
            // RenderTypes consumes a direct resource path (textures/...png),
            // not the concise model-style id stored in the mapping JSON.
            return new LoadedEntry(stableId, item, textureFile, source.priority, match, match.specificity());
        }

        private boolean matches(IconIdentity identity) {
            return item.equals(identity.item()) && match.matches(identity);
        }
    }

    private static boolean equalsOrBlank(@Nullable String expected, String actual) {
        return !isPresent(expected) || expected.equals(actual);
    }

    private static boolean equalsOrNull(@Nullable Integer expected, int actual) {
        return expected == null || expected == actual;
    }

    private static boolean isPresent(@Nullable String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isBlank(@Nullable String value) {
        return !isPresent(value);
    }
}
