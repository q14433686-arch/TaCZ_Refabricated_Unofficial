// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.event;

/**
 * 音效事件节点。触发时播放音效。
 *
 * @param eventName 音效事件名称
 */
public record SoundEffectEvent(String eventName) implements IEventNode {
}
