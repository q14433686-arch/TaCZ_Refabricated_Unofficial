package com.tacz.guns.client.resource.index;

import com.google.common.base.Preconditions;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.display.block.BlockDisplay;
import com.tacz.guns.client.resource.pojo.display.block.BlockTransformParser;
import net.minecraft.client.resources.model.cuboid.ItemTransforms;
import com.tacz.guns.client.resource.pojo.model.BedrockModelPOJO;
import com.tacz.guns.client.resource.pojo.model.BedrockVersion;
import com.tacz.guns.resource.pojo.BlockIndexPOJO;
import net.minecraft.resources.Identifier;
import org.apache.commons.lang3.StringUtils;

public class ClientBlockIndex {
    private BedrockModel model;
    private Identifier texture;
    private String name;
    private ItemTransforms transforms = ItemTransforms.NO_TRANSFORMS;
    private String tooltipKey;

    public static ClientBlockIndex getInstance(BlockIndexPOJO pojo) {
        ClientBlockIndex index = new ClientBlockIndex();
        checkIndex(pojo, index);
        BlockDisplay display = checkDisplay(pojo, index);
        checkModel(display, index);
        checkName(pojo, index);
        checkTransforms(display, index);
        return index;
    }

    private static void checkIndex(BlockIndexPOJO blockIndexPOJO, ClientBlockIndex index) {
        Preconditions.checkArgument(blockIndexPOJO != null, "index object file is empty");
        index.tooltipKey = blockIndexPOJO.getTooltip();
    }

    private static void checkName(BlockIndexPOJO blockIndexPOJO, ClientBlockIndex index) {
        index.name = blockIndexPOJO.getName();
        if (StringUtils.isBlank(index.name)) {
            index.name = "custom.tacz.error.no_name";
        }
    }

    private static BlockDisplay checkDisplay(BlockIndexPOJO pojo, ClientBlockIndex index) {
        Identifier display = pojo.getDisplay();
        Preconditions.checkArgument(display != null, "index object missing display field");
        BlockDisplay blockDisplay = ClientAssetsManager.INSTANCE.getBlockDisplay(pojo.getDisplay());
        Preconditions.checkArgument(blockDisplay != null, "there is no corresponding display file");
        return blockDisplay;
    }

    private static void checkModel(BlockDisplay display, ClientBlockIndex index) {
        Identifier modelLocation = display.getModelLocation();
        Preconditions.checkArgument(modelLocation != null, "display object missing model field");
        BedrockModelPOJO modelPOJO = ClientAssetsManager.INSTANCE.getBedrockModelPOJO(modelLocation);
        Preconditions.checkArgument(modelPOJO != null, "there is no corresponding model file");

        // 先判断是不是 1.10.0 版本基岩版模型文件
        if (BedrockVersion.isLegacyVersion(modelPOJO) && modelPOJO.getGeometryModelLegacy() != null) {
            index.model = new BedrockModel(modelPOJO, BedrockVersion.LEGACY);
        }
        // 判定是不是 1.12.0 版本基岩版模型文件
        if (BedrockVersion.isNewVersion(modelPOJO) && modelPOJO.getGeometryModelNew() != null) {
            index.model = new BedrockModel(modelPOJO, BedrockVersion.NEW);
        }
        Preconditions.checkArgument(index.model != null, "there is no model data in the model file");

        Identifier textureLocation = display.getModelTexture();
        Preconditions.checkArgument(textureLocation != null, "missing default texture");
        index.texture = display.getModelTexture();
    }

    /**
     * 26.2 修复：移植时这段被整体删除，导致工作台/装配台手持模型不缩放（默认包声明 scale 0.25，
     * 实际按 1.0 渲染 => 大 4 倍）。这里恢复上游行为，只是把解析换成 26.2 可用的实现，
     * 详见 {@link BlockTransformParser}。
     *
     * <p>与上游的一处刻意差异：上游用 {@code Preconditions.checkArgument(transforms != null)}
     * 硬性要求枪包提供 transforms，缺失即抛异常导致整个 index 加载失败。这里改为回退到
     * {@code NO_TRANSFORMS}，避免第三方枪包因缺该字段而整包加载不出来。</p>
     */
    private static void checkTransforms(BlockDisplay display, ClientBlockIndex index) {
        index.transforms = BlockTransformParser.parse(display.getTransforms());
    }

    public ItemTransforms getTransforms() {
        return transforms;
    }

    public BedrockModel getModel() {
        return model;
    }

    public Identifier getTexture() {
        return texture;
    }

    public String getName() {
        return name;
    }

    public String getTooltipKey() {
        return tooltipKey;
    }
}
