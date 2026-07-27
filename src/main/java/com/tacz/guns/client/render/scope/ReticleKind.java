package com.tacz.guns.client.render.scope;

/**
 * 准星（分划）的形态分类。
 *
 * <p>按瞄具模型里实际存在的节点自动判定，不需要枪包新增任何字段：</p>
 * <table border="1">
 *   <tr><th>形态</th><th>节点构成</th><th>现实原型</th><th>默认枪包数量</th></tr>
 *   <tr><td>{@link #HOLOGRAPHIC}</td><td>只有 {@code *_illuminated}</td>
 *       <td>EOTech / Aimpoint 红点</td><td>—</td></tr>
 *   <tr><td>{@link #ETCHED}</td><td>只有 {@code division}（无发光子节点）</td>
 *       <td>98k / PU 镜蚀刻线</td><td>2（{@code scope_98k}、{@code scope_retro_2x}）</td></tr>
 *   <tr><td>{@link #HYBRID}</td><td>两者都有</td>
 *       <td>ACOG TA31（黑蚀刻线 + 夜间照明段）</td><td>31</td></tr>
 *   <tr><td>{@link #NONE}</td><td>都没有</td><td>非瞄具（激光/手电）</td><td>—</td></tr>
 * </table>
 */
public enum ReticleKind {
    /** 纯发光准星：全息 / 红点。整片准星都是 {@code *_illuminated}。 */
    HOLOGRAPHIC,
    /** 纯蚀刻分划：不发光，靠环境光照亮。 */
    ETCHED,
    /** 混合：黑色蚀刻线 + 发光段（现代高倍镜的主流形态）。 */
    HYBRID,
    /** 无准星（非瞄具配件，或模型没做分划）。 */
    NONE
}
