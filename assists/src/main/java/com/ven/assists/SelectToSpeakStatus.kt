package com.ven.assists

/** 随选朗读快捷方式的检测结果；未匹配或读取失败均不能视为关闭。 */
enum class SelectToSpeakShortcutStatus { DETECTED, UNKNOWN }

/** Google 随选朗读服务及快捷方式的快照。 */
data class SelectToSpeakStatus(
    val googleServiceEnabled: Boolean,
    val matchingServiceIds: List<String>,
    val enabledServiceIds: List<String>,
    val accessibilityButton: SelectToSpeakShortcutStatus,
    val volumeKeys: SelectToSpeakShortcutStatus,
    val quickSettings: SelectToSpeakShortcutStatus,
    val shortcutSettings: Map<String, String?>,
)
