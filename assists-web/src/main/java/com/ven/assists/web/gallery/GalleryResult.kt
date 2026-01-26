package com.ven.assists.web.gallery

import android.net.Uri

/**
 * 保存到相册的结果数据类
 * @param uri 媒体文件的URI
 * @param id 媒体文件的ID
 * @param type 媒体类型，"image" 或 "video"
 * @param success 是否保存成功
 */
data class GalleryResult(
    val uri: Uri?,
    val id: Long?,
    val type: String?,
    val success: Boolean
)
