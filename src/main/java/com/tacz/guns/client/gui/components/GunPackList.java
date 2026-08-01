package com.tacz.guns.client.gui.components;

import com.google.common.collect.ImmutableList;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.PackInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.*;

public class GunPackList extends ContainerObjectSelectionList<GunPackList.Entry> {
    private final GunSmithTableScreen parent;
    private final List<Checkbox> gunPackList = new ArrayList<>();
    private final Set<String> selectedNamespaces = new HashSet<>();
    private final Checkbox byHandCheckbox;
    private final EditBox byName;

    public GunPackList(Minecraft pMinecraft, int pWidth, int pHeight, int pY0, int pY1, int pItemHeight,
                       Map<Identifier, List<Identifier>> recipes, GunSmithTableScreen parent) {
        // 【本轮修复：过滤器面板整个不可用】
        //
        // 26.2 的 AbstractSelectionList 构造签名是（字节码确认）：
        //     (Minecraft, int width, int height, int y, int itemHeight)
        // 最后一个参数是<b>每行的高度</b>，构造函数把它存进 defaultEntryHeight，
        // addEntry(Entry) 又直接拿它当行高用。
        //
        // 移植时错把 pY1（= topPos + imageHeight + 1，约 200 左右）当成了第 5 个参数，
        // 于是每一行的高度都变成了两百多像素：
        //   - getNextY() 逐行累加行高 -> 第 2 行往后的 y 直接飞到列表可视区之外；
        //   - extractListItems 有 `entry.getY() > getBottom() 就跳过` 的剔除，
        //     所以除第一行外<b>什么都画不出来</b>；
        //   - 每个 Entry 又会把自己的 x/y 同步给内部 widget，
        //     结果连点击热区也跟着跑偏 —— 勾选框根本点不中。
        // 表现就是「过滤器打开后没有实际作用」。
        //
        // 正确做法是传 pItemHeight（调用方给的是 15，与上游一致）。
        // 上游 1.21.1 的同名参数位置也正是 itemHeight，这里属于纯粹的参数错位。
        super(pMinecraft, pWidth, pHeight, pY0, pItemHeight);
        this.centerListVertically = false;
        this.parent = parent;
        Set<String> namespaces = new HashSet<>();
        for (List<Identifier> entry : recipes.values()) {
            entry.forEach((resourceLocation) -> namespaces.add(resourceLocation.getNamespace()));
        }

        this.byName = new EditBox(pMinecraft.font, 3, 0, 94, 10, Component.empty());
        this.byName.setHint(Component.translatable("gui.tacz.gun_smith_table.filter.search"));
        this.byName.setResponder((pText) -> {
            parent.init();
            parent.setIndexPage(0);
        });
        this.addEntry(new Entry(byName));

        this.byHandCheckbox = new Checkbox(0, 0, 10, 10, Component.translatable("gui.tacz.gun_smith_table.filter.handgun"), false) {
            @Override
            public void onPress(InputWithModifiers input) {
                super.onPress(input);
                parent.init();
                parent.setIndexPage(0);
            }
        };
        this.addEntry(new Entry(byHandCheckbox));

        Checkbox checkbox1 = new Checkbox(0, 0, 10, 10, Component.translatable("gui.tacz.gun_smith_table.filter.all"), true) {
            @Override
            public void onPress(InputWithModifiers input) {
                super.onPress(input);
                gunPackList.forEach((checkbox) -> checkbox.selected = this.selected);
                updateSelectedNamespaces();
            }
        };
        this.addEntry(new Entry(checkbox1));

        for (String namespace : namespaces) {
            PackInfo packInfo = ClientAssetsManager.INSTANCE.getPackInfo(namespace);
            Component name = packInfo == null ? Component.literal(namespace) : Component.translatable(packInfo.getName());

            Checkbox checkbox = new Checkbox(0, 0, 10, 10, name, namespace, true) {
                @Override
                public void onPress(InputWithModifiers input) {
                    super.onPress(input);
                    checkbox1.selected = gunPackList.stream().allMatch(Checkbox::selected);
                    updateSelectedNamespaces();
                }
            };
            gunPackList.add(checkbox);
            selectedNamespaces.add(namespace);
            this.addEntry(new Entry(checkbox));
        }
    }

    public String getSearchText() {
        return byName.getValue();
    }

    public boolean isByHandSelected() {
        return byHandCheckbox.selected;
    }

    public void setByHandSelected(boolean selected) {
        byHandCheckbox.selected = selected;
    }

    public Set<String> namespaceList() {
        return selectedNamespaces;
    }

    public void updateSelectedNamespaces() {
        selectedNamespaces.clear();
        gunPackList.forEach((checkbox) -> {
            if (checkbox.selected) {
                selectedNamespaces.add(checkbox.getId());
            }
        });
        parent.init();
        parent.setIndexPage(0);
    }

    protected int scrollBarX() {
        return this.getRight() - 2;
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        this.extractListBackground(pGuiGraphics);
        pGuiGraphics.fill(this.getX(), this.getY(), this.getRight(), this.getBottom(), 0x80000000);
        int i = this.scrollBarX();
        int j = i + 6;

        this.enableScissor(pGuiGraphics);
        this.extractListItems(pGuiGraphics, pMouseX, pMouseY, pPartialTick);

        int i2 = this.maxScrollAmount();
        if (i2 > 0) {
            int j2 = (int) ((float) ((this.getBottom() - this.getY()) * (this.getBottom() - this.getY())) / (float) this.contentHeight());
            j2 = Mth.clamp(j2, 32, this.getBottom() - this.getY() - 8);
            int k1 = (int) this.scrollAmount() * (this.getBottom() - this.getY() - j2) / i2 + this.getY();
            if (k1 < this.getY()) {
                k1 = this.getY();
            }
            pGuiGraphics.fill(i, k1, j, k1 + j2, -8355712);
            pGuiGraphics.fill(i, k1, j - 1, k1 + j2 - 1, -4144960);
        }
        this.extractListSeparators(pGuiGraphics);
    }

    public int getRowLeft() {
        return this.getX() + 4;
    }

    public int getRowWidth() {
        return this.width;
    }

    public static class Entry extends ContainerObjectSelectionList.Entry<Entry> {
        private final AbstractWidget widget;

        public Entry(AbstractWidget widget) {
            this.widget = widget;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(widget);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor pGuiGraphics, int pMouseX, int pMouseY, boolean pHovering, float pPartialTick) {
            this.widget.setX(this.getX());
            this.widget.setY(this.getY());
            this.widget.extractRenderState(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return ImmutableList.of(widget);
        }
    }

    /**
     * 过滤器面板里的勾选框。
     *
     * <p><b>本轮修复：勾选框图案画不出来</b>。原先用
     * {@code textures/gui/checkbox.png} 这张<b>整图</b>手动切 UV（0/10 两档）。
     * 该文件在 26.2 已不存在 —— 勾选框改成了 GUI sprite atlas 里的四张独立精灵
     * （{@code widget/checkbox}、{@code checkbox_highlighted}、
     * {@code checkbox_selected}、{@code checkbox_selected_highlighted}，
     * 已在 26.2 jar 内逐一确认）。缺图时 {@code blit} 取到的是 missing texture，
     * 于是「打勾」与「未打勾」在视觉上无法区分，配合下面那个行高错位，
     * 就成了玩家说的「过滤器没有实际作用」。
     *
     * <p>这里改用 {@code blitSprite} + 四张精灵，选取逻辑与 vanilla
     * {@code Checkbox#extractContents} 完全一致（字节码逐条对照）。</p>
     */
    public static class Checkbox extends AbstractButton {
        private static final Identifier CHECKBOX_SPRITE =
                Identifier.withDefaultNamespace("widget/checkbox");
        private static final Identifier CHECKBOX_HIGHLIGHTED_SPRITE =
                Identifier.withDefaultNamespace("widget/checkbox_highlighted");
        private static final Identifier CHECKBOX_SELECTED_SPRITE =
                Identifier.withDefaultNamespace("widget/checkbox_selected");
        private static final Identifier CHECKBOX_SELECTED_HIGHLIGHTED_SPRITE =
                Identifier.withDefaultNamespace("widget/checkbox_selected_highlighted");
        protected boolean selected;
        protected final boolean showLabel;
        private String id;

        public Checkbox(int pX, int pY, int pWidth, int pHeight, Component pMessage, String id, boolean pSelected) {
            this(pX, pY, pWidth, pHeight, pMessage, pSelected, true);
            this.id = id;
        }

        public Checkbox(int pX, int pY, int pWidth, int pHeight, Component pMessage, boolean pSelected) {
            this(pX, pY, pWidth, pHeight, pMessage, pSelected, true);
        }

        public Checkbox(int pX, int pY, int pWidth, int pHeight, Component pMessage, boolean pSelected, boolean pShowLabel) {
            super(pX, pY, pWidth, pHeight, pMessage);
            this.selected = pSelected;
            this.showLabel = pShowLabel;
        }

        public String getId() {
            return id;
        }

        public void onPress(InputWithModifiers input) {
            this.selected = !this.selected;
        }

        public boolean selected() {
            return this.selected;
        }

        public void updateWidgetNarration(NarrationElementOutput pNarrationElementOutput) {
            pNarrationElementOutput.add(NarratedElementType.TITLE, this.createNarrationMessage());
            if (this.active) {
                if (this.isFocused()) {
                    pNarrationElementOutput.add(NarratedElementType.USAGE, Component.translatable("narration.checkbox.usage.focused"));
                } else {
                    pNarrationElementOutput.add(NarratedElementType.USAGE, Component.translatable("narration.checkbox.usage.hovered"));
                }
            }

        }

        @Override
        protected void extractContents(GuiGraphicsExtractor pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
            Minecraft minecraft = Minecraft.getInstance();
            Font font = minecraft.font;
            Identifier sprite;
            if (this.selected) {
                sprite = this.isFocused() ? CHECKBOX_SELECTED_HIGHLIGHTED_SPRITE : CHECKBOX_SELECTED_SPRITE;
            } else {
                sprite = this.isFocused() ? CHECKBOX_HIGHLIGHTED_SPRITE : CHECKBOX_SPRITE;
            }
            pGuiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, this.getX(), this.getY(), this.width, this.height);
            if (this.showLabel) {
                // 【本轮修复：勾选框标签（枪包名）不显示】
                // 14737632 = 0x00E0E0E0，alpha 为 0。26.2 的 GuiGraphicsExtractor#text 第一条指令就是
                //     if (ARGB.alpha(color) == 0) return;
                // （见 docs/PORTING_NOTES.md §1，本项目已因此栽过 4 次）。
                // 1.21.1 没有这个短路，所以上游照样能画出来。补上 0xFF。
                pGuiGraphics.text(font, this.getMessage(), this.getX() + 24, this.getY() + (this.height - 8) / 2, 0xFFE0E0E0);
            }
        }
    }
}
