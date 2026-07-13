package my.noveldokusha.scraper.utils

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * JSON processing helper functions for scraper operations
 */

/**
 * Convert JSON blocks to HTML (for RanobeLib-style content)
 */
fun jsonBlocksToHtml(blocks: JsonArray?, attachments: JsonArray?): String {
    if (blocks == null) return ""

    val attachmentMap = buildAttachmentMap(attachments)
    val builder = StringBuilder()

    blocks.forEach { element ->
        val obj = element.asJsonObject
        val html = when (obj.get("type")?.asString) {
            "hardBreak" -> "<br>"
            "horizontalRule" -> "<hr>"
            "image" -> renderImage(obj, attachmentMap)
            "paragraph" -> "<p>${jsonBlocksToHtml(obj.getAsJsonArray("content"), attachments)}</p>"
            "orderedList" -> "<ol>${jsonBlocksToHtml(obj.getAsJsonArray("content"), attachments)}</ol>"
            "listItem" -> "<li>${jsonBlocksToHtml(obj.getAsJsonArray("content"), attachments)}</li>"
            "blockquote" -> "<blockquote>${jsonBlocksToHtml(obj.getAsJsonArray("content"), attachments)}</blockquote>"
            "italic" -> "<i>${jsonBlocksToHtml(obj.getAsJsonArray("content"), attachments)}</i>"
            "bold" -> "<b>${jsonBlocksToHtml(obj.getAsJsonArray("content"), attachments)}</b>"
            "underline" -> "<u>${jsonBlocksToHtml(obj.getAsJsonArray("content"), attachments)}</u>"
            "heading" -> "<h2>${jsonBlocksToHtml(obj.getAsJsonArray("content"), attachments)}</h2>"
            "text" -> obj.get("text")?.asString.orEmpty()
            else -> obj.toString()
        }
        builder.append(html)
    }

    return builder.toString()
}

/**
 * Build attachment map from JSON array
 */
private fun buildAttachmentMap(attachments: JsonArray?): Map<String, String> {
    if (attachments == null) return emptyMap()

    return attachments.flatMap { attach ->
        val obj = attach.asJsonObject
        val url = obj.get("url")?.asString ?: return@flatMap emptyList()
        listOfNotNull(
            obj.get("name")?.asString,
            obj.get("id")?.takeIf { !it.isJsonNull }?.asString
        ).map { key -> key to url }
    }.toMap()
}

/**
 * Render image from JSON block
 */
private fun renderImage(element: JsonObject, attachments: Map<String, String>): String {
    val attrs = element.getAsJsonObject("attrs")
    val images = attrs?.getAsJsonArray("images")
    val builder = StringBuilder()

    if (images != null && images.size() > 0) {
        images.forEach { image ->
            val value = image.asJsonObject.get("image")
            val key = value?.asString ?: value?.toString()
            val url = key?.let { attachments[it] }
            if (url != null) builder.append("<img src='$url'>")
        }
    } else if (attrs != null) {
        val attrList = attrs.entrySet()
            .mapNotNull { entry ->
                val value = entry.value
                if (value == null || value.isJsonNull) return@mapNotNull null
                "${entry.key}=\"${value.asString}\""
            }
        if (attrList.isNotEmpty()) {
            builder.append("<img ${attrList.joinToString(" ")}>")
        }
    }

    return builder.toString()
}
