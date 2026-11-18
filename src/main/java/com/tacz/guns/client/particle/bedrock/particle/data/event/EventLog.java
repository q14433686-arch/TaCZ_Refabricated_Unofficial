// Derived from SimpleBedrockModel 71ad526 (LGPL-3.0); see LICENSES/SimpleBedrockModel.NOTICE.md.
package com.tacz.guns.client.particle.bedrock.particle.data.event;

/**
 * 日志事件节点。触发时输出日志消息。
 *
 * @param message 日志消息
 */
public record EventLog(String message) implements IEventNode {
}
