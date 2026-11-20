// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.event;

import java.util.List;

/**
 * 顺序事件节点。按顺序执行子事件列表。
 *
 * @param nodes 子事件节点列表
 */
public record EventSequence(List<IEventNode> nodes) implements IEventNode {
}
