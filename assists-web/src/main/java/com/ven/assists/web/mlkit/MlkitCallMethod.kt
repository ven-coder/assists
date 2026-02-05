package com.ven.assists.web.mlkit

/**
 * ML Kit 文字识别相关的方法常量定义
 * 支持识别屏幕中指定词组位置以及识别屏幕文字内容位置
 */
object MlkitCallMethod {
    /**
     * 识别屏幕中指定词组的位置（基于当前截图）
     * 参数：targetText 必填；region 可选 { left, top, right, bottom }；rotationDegrees 可选，默认 0
     */
    const val findPhrasePositions = "findPhrasePositions"

    /**
     * 识别屏幕中所有文字内容及其位置（基于当前截图）
     * 参数：region 可选 { left, top, right, bottom }；rotationDegrees 可选，默认 0
     */
    const val getScreenTextPositions = "getScreenTextPositions"

    /**
     * 识别屏幕中指定词组的位置，直接返回 JSON 字符串（基于当前截图）
     * 参数：targetText 必填；region 可选 { left, top, right, bottom }；rotationDegrees 可选，默认 0
     */
    const val findPhrasePositionsOnScreenAsJson = "findPhrasePositionsOnScreenAsJson"

    /**
     * 识别屏幕中所有文字及其位置，直接返回 JSON 字符串（基于当前截图）
     * 参数：region 可选 { left, top, right, bottom }；rotationDegrees 可选，默认 0
     */
    const val getScreenTextPositionsAsJson = "getScreenTextPositionsAsJson"
}
