package cn.sh1rocu.tacz.util.forge;

import com.google.common.collect.ImmutableList;
import net.minecraft.network.chat.Component;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class DelegatingPackResources extends AbstractPackResources {
    private final PackMetadataSection packMeta;
    private final List<PackResources> delegates;
    private final Map<String, List<PackResources>> namespacesAssets;
    private final Map<String, List<PackResources>> namespacesData;

    public DelegatingPackResources(String packId, boolean isBuiltin, PackMetadataSection packMeta, List<? extends PackResources> packs) {
        super(new PackLocationInfo(packId, Component.literal(packId), PackSource.DEFAULT, Optional.empty()));
        this.packMeta = packMeta;
        this.delegates = ImmutableList.copyOf(packs);
        this.namespacesAssets = this.buildNamespaceMap(PackType.CLIENT_RESOURCES, delegates);
        this.namespacesData = this.buildNamespaceMap(PackType.SERVER_DATA, delegates);
    }

    private Map<String, List<PackResources>> buildNamespaceMap(PackType type, List<PackResources> packList) {
        Map<String, List<PackResources>> map = new HashMap<>();
        for (PackResources pack : packList) {
            for (String namespace : pack.getNamespaces(type)) {
                map.computeIfAbsent(namespace, k -> new ArrayList<>()).add(pack);
            }
        }
        map.replaceAll((k, list) -> ImmutableList.copyOf(list));
        return ImmutableMap.copyOf(map);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T> T getMetadataSection(MetadataSectionType<T> deserializer) throws IOException {
        return deserializer.name().equals("pack") ? (T) this.packMeta : null;
    }

    @Override
    public void listResources(PackType type, String resourceNamespace, String paths, ResourceOutput resourceOutput) {
        boolean isRecipe = type == PackType.SERVER_DATA && "recipe".equals(paths);
        String legacyPath = type == PackType.SERVER_DATA ? cn.sh1rocu.tacz.util.RecipeCompat.getLegacyForCurrent(paths) : null;

        if (isRecipe) {
            ResourceOutput transformingOutput = (location, supplier) -> {
                var wrapped = cn.sh1rocu.tacz.util.RecipeCompat.wrapSupplierForRecipe(location, supplier);
                resourceOutput.accept(location, wrapped);
            };
            for (PackResources delegate : this.delegates) {
                try {
                    delegate.listResources(type, resourceNamespace, paths, transformingOutput);
                } catch (Exception ignored) {}
            }
            if (legacyPath != null) {
                // 列出 legacy recipes/ 下的所有配方文件（包括 tacz:gun_smith_table_crafting 等自定义类型）。
                // 绝不能在 listing 阶段按 type 丢弃非 minecraft: 配方——否则旧枪包的枪械工作台配方
                // 不会出现在 FileToIdConverter 的结果里，TableRecipeManager 扫不到，
                // 网络同步包缺数据，JEI/REI 的「枪械工作台」分类就空了。
                // 字段格式转换由 wrapSupplierForRecipe 惰性处理：内部对非 minecraft: 类型直接透传原字节。
                ResourceOutput legacyOutput = (location, supplier) -> {
                    var remapped = cn.sh1rocu.tacz.util.RecipeCompat.remapLegacyToCurrent(location);
                    var wrapped = cn.sh1rocu.tacz.util.RecipeCompat.wrapSupplierForRecipe(remapped, supplier);
                    resourceOutput.accept(remapped, wrapped);
                };
                for (PackResources delegate : this.delegates) {
                    try {
                        delegate.listResources(type, resourceNamespace, legacyPath, legacyOutput);
                    } catch (Exception ignored) {}
                }
            }
        } else if (legacyPath != null && type == PackType.SERVER_DATA) {
            // 非 recipe 的兼容路径（如 loot_table, tags/block）
            for (PackResources delegate : this.delegates) {
                try {
                    delegate.listResources(type, resourceNamespace, paths, resourceOutput);
                } catch (Exception ignored) {}
            }
            ResourceOutput legacyOutput = (location, supplier) -> {
                var remapped = cn.sh1rocu.tacz.util.RecipeCompat.remapLegacyToCurrent(location);
                resourceOutput.accept(remapped, supplier);
            };
            for (PackResources delegate : this.delegates) {
                try {
                    delegate.listResources(type, resourceNamespace, legacyPath, legacyOutput);
                } catch (Exception ignored) {}
            }
        } else {
            for (PackResources delegate : this.delegates) {
                delegate.listResources(type, resourceNamespace, paths, resourceOutput);
            }
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? namespacesAssets.keySet() : namespacesData.keySet();
    }

    @Override
    public void close() {
        for (PackResources pack : delegates) {
            pack.close();
        }
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... paths) {
        return null;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier location) {
        for (PackResources pack : getCandidatePacks(type, location)) {
            IoSupplier<InputStream> ioSupplier = pack.getResource(type, location);
            if (ioSupplier != null) {
                if (type == PackType.SERVER_DATA && cn.sh1rocu.tacz.util.RecipeCompat.isRecipePath(location)) {
                    return cn.sh1rocu.tacz.util.RecipeCompat.wrapSupplierForRecipe(location, ioSupplier);
                }
                return ioSupplier;
            }
        }

        if (type == PackType.SERVER_DATA) {
            Identifier legacy = cn.sh1rocu.tacz.util.RecipeCompat.remapCurrentToLegacy(location);
            if (!legacy.equals(location)) {
                for (PackResources pack : getCandidatePacks(type, legacy)) {
                    IoSupplier<InputStream> ioSupplier = pack.getResource(type, legacy);
                    if (ioSupplier != null) {
                        if (cn.sh1rocu.tacz.util.RecipeCompat.isRecipePath(location)) {
                            return cn.sh1rocu.tacz.util.RecipeCompat.wrapSupplierForRecipe(location, ioSupplier);
                        }
                        return ioSupplier;
                    }
                }
                for (PackResources pack : this.delegates) {
                    IoSupplier<InputStream> ioSupplier = pack.getResource(type, legacy);
                    if (ioSupplier != null) {
                        if (cn.sh1rocu.tacz.util.RecipeCompat.isRecipePath(location)) {
                            return cn.sh1rocu.tacz.util.RecipeCompat.wrapSupplierForRecipe(location, ioSupplier);
                        }
                        return ioSupplier;
                    }
                }
            }
        }

        return null;
    }

    @Nullable
    public Collection<PackResources> getChildren() {
        return delegates;
    }

    private List<PackResources> getCandidatePacks(PackType type, Identifier location) {
        Map<String, List<PackResources>> map = type == PackType.CLIENT_RESOURCES ? namespacesAssets : namespacesData;
        List<PackResources> packsWithNamespace = map.get(location.getNamespace());
        return packsWithNamespace == null ? Collections.emptyList() : packsWithNamespace;
    }
}
