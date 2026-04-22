package com.ven.assists.utils

import kotlin.DeprecationLevel

/**
 * 历史节点类型类名字符串常量（已过时）。
 *
 * 请改用 [AssistsNodeClassNames]；类型判断请使用同包下对 [android.view.accessibility.AccessibilityNodeInfo] 的 `isXxx()` 扩展（定义于 `AssistsNodeClassNames.kt`）。
 */
@Deprecated(
    message = "请使用 AssistsNodeClassNames",
    replaceWith = ReplaceWith("AssistsNodeClassNames", imports = ["com.ven.assists.utils.AssistsNodeClassNames"]),
    level = DeprecationLevel.WARNING,
)
object NodeClassValue {
    const val ImageView = AssistsNodeClassNames.ImageView
    const val TextView = AssistsNodeClassNames.TextView
    const val LinearLayout = AssistsNodeClassNames.LinearLayout
    const val RelativeLayout = AssistsNodeClassNames.RelativeLayout
    const val Button = AssistsNodeClassNames.Button
    const val ImageButton = AssistsNodeClassNames.ImageButton
    const val EditText = AssistsNodeClassNames.EditText
    const val View = AssistsNodeClassNames.View
    const val ViewGroup = AssistsNodeClassNames.ViewGroup
    const val FrameLayout = AssistsNodeClassNames.FrameLayout
}
