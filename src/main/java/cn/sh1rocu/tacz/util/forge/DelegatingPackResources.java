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
        // 26.2 兼容：当查询 recipe 时，额外把 recipes（旧枪包布局）映射为 recipe，并对旧格式做即时转换
        // 同时对正常 recipe 的旧格式也做转换，避免因 result.item / nbt 导致解析失败
        boolean isRecipeQuery = type == PackType.SERVER_DATA && "recipe".equals(paths);

        if (isRecipeQuery) {
            // 先列出正常的 recipe/，带转换包装
            ResourceOutput transformingOutput = (location, supplier) -> {
                // 仅对 recipe 路径做转换包装
                var wrapped = cn.sh1rocu.tacz.util.RecipeCompat.wrapSupplierForRecipe(location, supplier);
                resourceOutput.accept(location, wrapped);
            };
            for (PackResources delegate : this.delegates) {
                try {
                    delegate.listResources(type, resourceNamespace, paths, transformingOutput);
                } catch (Exception ignored) {}
            }
            // 再列出旧目录 recipes/，重映射为 recipe/，并同样包装
            ResourceOutput legacyOutput = (location, supplier) -> {
                // location 可能是 recipes/xxx.json，需重映射为 recipe/xxx.json
                var remapped = cn.sh1rocu.tacz.util.RecipeCompat.remapLegacyToCurrent(location);
                // 仅保留原版配方，过滤掉 tacz:gun_smith_table_crafting
                // 通过预读 JSON 判断 type，若非原版则跳过，避免污染 vanilla RecipeManager（见 COMPAT_AND_ROADMAP 所述污染问题）
                try {
                    // 偷看一下内容，判断是否为原版类型
                    // 若不是原版（即自定义工作台配方），则不加入到 vanilla 的列表中
                    // TableRecipeManager 会自行扫描 recipes/，因此这里跳过不会丢失自定义配方
                    try (var in = supplier.get().get()) {
                        byte[] bytes = in.readAllBytes();
                        String txt = new String(bytes, java.nio.charset.StandardCharsets.UTF_8).trim();
                        if (!txt.isEmpty()) {
                            try {
                                var je = com.google.gson.JsonParser.parseString(txt);
                                if (je.isJsonObject()) {
                                    var obj = je.getAsJsonObject();
                                    // 若不是原版配方，跳过（避免 vanilla 解析失败刷屏）
                                    if (!cn.sh1rocu.tacz.util.RecipeCompat.isVanillaRecipeType(obj)) {
                                        return;
                                    }
                                }
                            } catch (Exception ignoreParse) {
                                // 解析失败则仍尝试列出，让后续转换处理
                            }
                        }
                        // 重新包装为转换流（因为上面已消耗）
                        var fixedSupplier = net.minecraft.server.packs.resources.IoSupplier.create(() -> {
                            try (var in2 = supplier.get().get()) {
                                return cn.sh1rocu.tacz.util.RecipeCompat.transformStreamIfNeeded(in2);
                            }
                        });
                        resourceOutput.accept(remapped, fixedSupplier);
                    }
                } catch (Exception e) {
                    // 回退：直接包装转换
                    var wrapped = cn.sh1rocu.tacz.util.RecipeCompat.wrapSupplierForRecipe(remapped, supplier);
                    resourceOutput.accept(remapped, wrapped);
                }
            };
            for (PackResources delegate : this.delegates) {
                try {
                    delegate.listResources(type, resourceNamespace, "recipes", legacyOutput);
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
        // Root resources do not make sense here
        return null;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier location) {
        for (PackResources pack : getCandidatePacks(type, location)) {
            IoSupplier<InputStream> ioSupplier = pack.getResource(type, location);
            if (ioSupplier != null) {
                // 对配方做即时转换
                if (type == PackType.SERVER_DATA && cn.sh1rocu.tacz.util.RecipeCompat.isRecipePath(location)) {
                    return cn.sh1rocu.tacz.util.RecipeCompat.wrapSupplierForRecipe(location, ioSupplier);
                }
                return ioSupplier;
            }
        }

        // 26.2 兼容：若请求的是 recipe/xxx 但实际在 recipes/xxx（旧枪包布局），尝试回退
        if (type == PackType.SERVER_DATA && location.getPath().startsWith("recipe/")) {
            Identifier legacy = cn.sh1rocu.tacz.util.RecipeCompat.remapCurrentToLegacy(location);
            for (PackResources pack : getCandidatePacks(type, legacy)) {
                IoSupplier<InputStream> ioSupplier = pack.getResource(type, legacy);
                if (ioSupplier != null) {
                    // 转换旧格式
                    return cn.sh1rocu.tacz.util.RecipeCompat.wrapSupplierForRecipe(location, ioSupplier);
                }
            }
            // 同时尝试在所有 delegates 中找（以防 namespace map 未包含旧路径）
            for (PackResources pack : this.delegates) {
                IoSupplier<InputStream> ioSupplier = pack.getResource(type, legacy);
                if (ioSupplier != null) {
                    return cn.sh1rocu.tacz.util.RecipeCompat.wrapSupplierForRecipe(location, ioSupplier);
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