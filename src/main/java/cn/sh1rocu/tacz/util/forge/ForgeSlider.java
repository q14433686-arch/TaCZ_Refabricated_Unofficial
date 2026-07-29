package cn.sh1rocu.tacz.util.forge;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.text.DecimalFormat;

/**
 * Slider widget implementation which allows inputting values in a certain range with optional step size.
 */
public class ForgeSlider extends AbstractSliderButton {
    protected Component prefix;
    protected Component suffix;

    protected double minValue;
    protected double maxValue;

    /**
     * Allows input of discontinuous values with a certain step
     */
    protected double stepSize;

    protected boolean drawString;

    private final DecimalFormat format;

    /**
     * @param x            x position of upper left corner
     * @param y            y position of upper left corner
     * @param width        Width of the widget
     * @param height       Height of the widget
     * @param prefix       {@link Component} displayed before the value string
     * @param suffix       {@link Component} displayed after the value string
     * @param minValue     Minimum (left) value of slider
     * @param maxValue     Maximum (right) value of slider
     * @param currentValue Starting value when widget is first displayed
     * @param stepSize     Size of step used. Precision will automatically be calculated based on this value if this value is not 0.
     * @param precision    Only used when {@code stepSize} is 0. Limited to a maximum of 4 (inclusive).
     * @param drawString   Should text be displayed on the widget
     */
    public ForgeSlider(int x, int y, int width, int height, Component prefix, Component suffix, double minValue, double maxValue, double currentValue, double stepSize, int precision, boolean drawString) {
        super(x, y, width, height, Component.empty(), 0D);
        this.prefix = prefix;
        this.suffix = suffix;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.stepSize = Math.abs(stepSize);
        this.value = this.snapToNearest((currentValue - minValue) / (maxValue - minValue));
        this.drawString = drawString;

        if (stepSize == 0D) {
            precision = Math.min(precision, 4);

            StringBuilder builder = new StringBuilder("0");

            if (precision > 0)
                builder.append('.');

            while (precision-- > 0)
                builder.append('0');

            this.format = new DecimalFormat(builder.toString());
        } else if (Mth.equal(this.stepSize, Math.floor(this.stepSize))) {
            this.format = new DecimalFormat("0");
        } else {
            this.format = new DecimalFormat(Double.toString(this.stepSize).replaceAll("\\d", "0"));
        }

        this.updateMessage();
    }

    /**
     * Overload with {@code stepSize} set to 1, useful for sliders with whole number values.
     */
    public ForgeSlider(int x, int y, int width, int height, Component prefix, Component suffix, double minValue, double maxValue, double currentValue, boolean drawString) {
        this(x, y, width, height, prefix, suffix, minValue, maxValue, currentValue, 1D, 0, drawString);
    }

    /**
     * @return Current slider value as a double
     */
    public double getValue() {
        return this.value * (maxValue - minValue) + minValue;
    }

    /**
     * @return Current slider value as an long
     */
    public long getValueLong() {
        return Math.round(this.getValue());
    }

    /**
     * @return Current slider value as an int
     */
    public int getValueInt() {
        return (int) this.getValueLong();
    }

    /**
     * 按<b>真实值</b>（{@code minValue}~{@code maxValue} 区间）设置滑块。
     *
     * <h2>为什么叫 setValueReal 而不是 setValue</h2>
     * 26.2 的 {@code AbstractSliderButton} 有一个<b>同名同签名</b>的方法
     * {@code setValue(double)}，但语义完全不同 —— 它接收的是
     * <b>0~1 的比例</b>，并且内部会做两件关键的事（字节码确认）：
     * <pre>
     * this.value = Mth.clamp(value, 0.0, 1.0);
     * if (d != this.value) { this.applyValue(); }   // 值变了才回调
     * this.updateMessage();
     * </pre>
     *
     * <p>本类原先把它命名为 {@code setValue}，于是<b>意外覆写</b>了父类方法，
     * 而覆写版既按「真实值」解释入参、又<b>从不调用 {@code applyValue()}</b>。
     * 后果正是用户实测到的诡异现象：
     * <ul>
     *   <li><b>拖动滑块不生效</b> —— 拖动走的是 vanilla
     *       {@code AbstractSliderButton#onDrag → setValueFromMouse(event) → setValue(double)}，
     *       这里的多态分派落进了本类的覆写版，只改了数值、没有回调
     *       {@code applyValue()}，于是镭射颜色不更新；</li>
     *   <li><b>点击滑块不同位置却生效</b> —— 点击走的是本类自己的
     *       {@code onClick → setValueFromMouse(double) → setSliderValue}，
     *       那条路径显式调了 {@code applyValue()}，所以能改色也能保存。</li>
     * </ul>
     * 「点击有效、拖动无效」这个组合就是这次分派冲突的指纹。
     *
     * <p>改名后不再覆写父类，vanilla 的 {@code setValue(double)} 恢复原有语义
     * （含 {@code applyValue()} 回调），拖动路径自然被打通；
     * 本类原有的真实值语义则由本方法承担，供键盘左右键调整使用。
     *
     * @param value 新的滑块值（真实值，非比例）
     */
    public void setValueReal(double value) {
        this.value = this.snapToNearest((value - this.minValue) / (this.maxValue - this.minValue));
        this.updateMessage();
    }

    public String getValueString() {
        return this.format.format(this.getValue());
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean b) {
        this.setValueFromMouse(event.x());
    }

    /**
     * 拖动时更新滑块值。
     *
     * <p><b>刻意不调用 {@code super.onDrag}</b>：vanilla 的
     * {@code AbstractSliderButton#onDrag} 内部是
     * {@code setValueFromMouse(event) → setValue(double)}，
     * 那条路径只做 {@code Mth.clamp(0,1)}、<b>不做 {@link #snapToNearest} 步进吸附</b>。
     * 若先调 super 再调本类的 {@code setValueFromMouse}，等于同一次拖动里
     * 先按「无吸附」写一次、再按「有吸附」写一次 —— 两次都可能触发
     * {@code applyValue()}，既做了无谓的重复回调，也让步进语义变得不确定。
     *
     * <p>因此这里直接走本类的 {@code setValueFromMouse(double)}，
     * 它最终落到 {@link #setSliderValue}：先 {@code snapToNearest}，
     * 且<b>仅在值真正变化时</b>才回调 {@code applyValue()} ——
     * 与 vanilla {@code setValue} 的「变了才回调」保持一致的语义，
     * 同时保留本类的步进能力。
     *
     * <p>父类 {@code onDrag} 除此之外没有其他副作用（字节码确认：
     * 它只有 {@code setValueFromMouse} 与一个空的
     * {@code WithInactiveMessage#onDrag}），故跳过是安全的。
     */
    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        this.setValueFromMouse(event.x());
    }

    public boolean handleKeyEvent(KeyEvent event) {
        boolean flag = event.key() == GLFW.GLFW_KEY_LEFT;
        if (flag || event.key() == GLFW.GLFW_KEY_RIGHT) {
            if (this.minValue > this.maxValue)
                flag = !flag;
            float f = flag ? -1F : 1F;
            if (stepSize <= 0D)
                this.setSliderValue(this.value + (f / (this.width - 8)));
            else
                this.setValueReal(this.getValue() + f * this.stepSize);
        }

        return false;
    }

    private void setValueFromMouse(double mouseX) {
        this.setSliderValue((mouseX - (this.getX() + 4)) / (this.width - 8));
    }

    /**
     * @param value Percentage of slider range
     */
    private void setSliderValue(double value) {
        double oldValue = this.value;
        this.value = this.snapToNearest(value);
        if (!Mth.equal(oldValue, this.value))
            this.applyValue();

        this.updateMessage();
    }

    /**
     * Snaps the value, so that the displayed value is the nearest multiple of {@code stepSize}.
     * If {@code stepSize} is 0, no snapping occurs.
     */
    private double snapToNearest(double value) {
        if (stepSize <= 0D)
            return Mth.clamp(value, 0D, 1D);

        value = Mth.lerp(Mth.clamp(value, 0D, 1D), this.minValue, this.maxValue);

        value = (stepSize * Math.round(value / stepSize));

        if (this.minValue > this.maxValue) {
            value = Mth.clamp(value, this.maxValue, this.minValue);
        } else {
            value = Mth.clamp(value, this.minValue, this.maxValue);
        }

        return Mth.map(value, this.minValue, this.maxValue, 0D, 1D);
    }

    @Override
    protected void updateMessage() {
        if (this.drawString) {
            this.setMessage(Component.literal("").append(prefix).append(this.getValueString()).append(suffix));
        } else {
            this.setMessage(Component.empty());
        }
    }

    @Override
    protected void applyValue() {
    }
}
