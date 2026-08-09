package cn.sh1rocu.tacz.compat.meshloader.model;

import cn.sh1rocu.tacz.compat.meshloader.api.IPolyMeshBone;
import cn.sh1rocu.tacz.compat.meshloader.core.BedrockPartBoneAdapter;
import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshModel;
import cn.sh1rocu.tacz.compat.meshloader.core.PolyMeshSnapshot;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 支持 poly_mesh 的配件模型（瞄具/握把/弹匣等）。
 * 没有对应 geo.json 时行为与父类完全一致。
 *
 * <p>26.2 版：重写 9 参 {@code submit}（8 参由父类委托到本 9 参）。
 * 立方体部分走 {@code super.submit} —— 其中包含移植版自己的目镜掩码/
 * 镜身裁剪/准星流程；poly_mesh 部分作为普通几何追加提交。
 * <b>mesh 目镜的镜内裁剪（离屏掩码）留待 P3</b>，当前与移植版
 * 非光影回退行为一致（镜内可见镜筒内壁，不崩不黑）。</p>
 *
 * <p>注入：{@code ClientAttachmentIndex#checkTextureAndModel} /
 * {@code checkLod} 的 TAIL（见 mixin）。</p>
 *
 * <p>移植自 VellEagle/TacZMeshLoader 1.21.1_fabric (GPL-3.0)，
 * 删除了 26.2 不存在的 stencil 路径（restorePartVisibilityForPolyMesh /
 * renderPolyMeshThroughStencilHole / ocularPolyMeshBoneNames）。</p>
 */
public class TaczPolyMeshAttachmentModel extends BedrockAttachmentModel {

    private static final Logger LOGGER = LoggerFactory.getLogger("TacZMeshLoader");

    private PolyMeshModel polyMeshModel;
    private Identifier cachedTexture = null;
    private List<IPolyMeshBone> cachedRootChildren = null;

    public TaczPolyMeshAttachmentModel(BedrockModelPOJO pojo, BedrockVersion version) {
        super(pojo, version);
    }

    // =========================================================================
    // 26.2 submit 路径（8 参由 BedrockAttachmentModel 委托到本 9 参）
    // =========================================================================

    @Override
    public void submit(@Nullable ItemStack attachmentItem, ItemStack currentGunItem, PoseStack poseStack,
                       ItemDisplayContext transformType, SubmitNodeCollector collector,
                       RenderType renderType, @Nullable Identifier texture,
                       int light, int overlay) {
        if (!hasPolyMesh()) {
            super.submit(attachmentItem, currentGunItem, poseStack, transformType, collector,
                    renderType, texture, light, overlay);
            return;
        }

        // 立方体层正常提交（内部包含移植版自己的目镜掩码/镜身裁剪/准星流程）
        super.submit(attachmentItem, currentGunItem, poseStack, transformType, collector,
                renderType, texture, light, overlay);

        // poly_mesh 层：普通几何提交（P3 再接入离屏掩码裁剪）
        Identifier tex = texture != null ? texture : resolveTexture(attachmentItem);
        if (tex == null) {
            return;
        }
        PolyMeshSnapshot snapshot = polyMeshModel.capture(poseStack, light);
        if (snapshot.isEmpty()) {
            return;
        }
        PoseStack identity = new PoseStack();
        collector.submitCustomGeometry(identity, RenderTypes.entityCutout(tex),
                (entryPose, consumer) -> snapshot.writeCutout(consumer, overlay));
        if (snapshot.hasTranslucent()) {
            PoseStack identityTranslucent = new PoseStack();
            collector.submitCustomGeometry(identityTranslucent, RenderTypes.entityTranslucent(tex),
                    (entryPose, consumer) -> snapshot.writeTranslucent(consumer, overlay));
        }
    }

    /** 9 参版本未传贴图时的回退：从附件物品的 display 索引解析贴图。 */
    @Nullable
    private Identifier resolveTexture(@Nullable ItemStack attachmentItem) {
        if (cachedTexture != null) {
            return cachedTexture;
        }
        if (attachmentItem == null || attachmentItem.isEmpty()) {
            return null;
        }
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(attachmentItem);
        if (iAttachment != null) {
            Identifier attachmentId = iAttachment.getAttachmentId(attachmentItem);
            TimelessAPI.getClientAttachmentIndex(attachmentId)
                    .ifPresent(index -> cachedTexture = index.getModelTexture());
        }
        return cachedTexture;
    }

    // =========================================================================
    // poly_mesh 加载
    // =========================================================================

    public void loadPolyMesh(Identifier modelLocation) {
        try {
            Optional<net.minecraft.server.packs.resources.Resource> resource =
                    Minecraft.getInstance().getResourceManager().getResource(modelLocation);
            if (!resource.isPresent()) {
                return;
            }
            try (InputStreamReader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
                JsonObject rawJson = JsonParser.parseReader(reader).getAsJsonObject();

                this.cachedRootChildren = null;
                IPolyMeshBone adaptedRoot = new IPolyMeshBone() {
                    @Override public String getName()    { return "meshy_dummy_root"; }
                    @Override public float getPivotX()   { return 0; }
                    @Override public float getPivotY()   { return 0; }
                    @Override public float getPivotZ()   { return 0; }
                    @Override public float getRotX()     { return 0; }
                    @Override public float getRotY()     { return 0; }
                    @Override public float getRotZ()     { return 0; }
                    @Override public boolean isVisible() { return true; }
                    @Override public void applyTransform(PoseStack ps) { }
                    @Override
                    public List<? extends IPolyMeshBone> getChildren() {
                        if (cachedRootChildren != null) return cachedRootChildren;
                        cachedRootChildren = getShouldRender().stream()
                                .map(BedrockPartBoneAdapter::new).collect(Collectors.toList());
                        return cachedRootChildren;
                    }
                };

                this.polyMeshModel = new PolyMeshModel(adaptedRoot, rawJson);
                this.cachedTexture = null;

                LOGGER.info("[TacZMeshLoader] Loaded attachment poly_mesh from: {}", modelLocation);
            }
        } catch (Exception e) {
            LOGGER.error("[TacZMeshLoader] Failed to load attachment poly_mesh: {}", modelLocation, e);
        }
    }

    public boolean hasPolyMesh() {
        return polyMeshModel != null;
    }
}
