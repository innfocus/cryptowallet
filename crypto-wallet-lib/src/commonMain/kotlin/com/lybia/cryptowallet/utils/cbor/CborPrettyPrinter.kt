package com.lybia.cryptowallet.utils.cbor

/**
 * Pretty-prints [CborValue] into a structured, human-readable text string for debugging.
 */
class CborPrettyPrinter {

    /**
     * Format a [CborValue] as a structured text string.
     * @param indent number of spaces for the current indentation level
     */
    fun format(value: CborValue, indent: Int = 0): String {
        val sb = StringBuilder()
        formatValue(value, indent, sb)
        return sb.toString()
    }

    /**
     * Decode [bytes] as CBOR and format the result.
     */
    fun format(bytes: ByteArray): String {
        val decoded = CborDecoder().decode(bytes)
        return format(decoded)
    }

    // ---- Internal formatting ----

    private fun formatValue(value: CborValue, indent: Int, sb: StringBuilder) {
        val pad = " ".repeat(indent)
        when (value) {
            is CborValue.CborUInt -> sb.append("${pad}uint(${value.value})")
            is CborValue.CborNegInt -> sb.append("${pad}negint(${value.actualValue})")
            is CborValue.CborByteString -> {
                val hex = value.bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
                sb.append("${pad}bytes(${value.bytes.size}): h'$hex'")
            }
            is CborValue.CborTextString -> sb.append("${pad}text(\"${value.text}\")")
            is CborValue.CborArray -> formatArray(value, indent, sb)
            is CborValue.CborMap -> formatMap(value, indent, sb)
            is CborValue.CborTag -> formatTag(value, indent, sb)
            is CborValue.CborSimple -> formatSimple(value, indent, sb)
        }
    }

    private fun formatArray(arr: CborValue.CborArray, indent: Int, sb: StringBuilder) {
        val pad = " ".repeat(indent)
        val marker = if (arr.indefinite) "array*" else "array"
        if (arr.items.isEmpty()) {
            sb.append("${pad}${marker}(0) []")
            return
        }
        sb.append("${pad}${marker}(${arr.items.size}) [\n")
        for ((i, item) in arr.items.withIndex()) {
            formatValue(item, indent + 2, sb)
            if (i < arr.items.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("${pad}]")
    }

    private fun formatMap(map: CborValue.CborMap, indent: Int, sb: StringBuilder) {
        val pad = " ".repeat(indent)
        if (map.entries.isEmpty()) {
            sb.append("${pad}map(0) {}")
            return
        }
        sb.append("${pad}map(${map.entries.size}) {\n")
        for ((i, entry) in map.entries.withIndex()) {
            formatValue(entry.first, indent + 2, sb)
            sb.append(": ")
            // Inline the value (trim leading whitespace from value formatting)
            val valStr = StringBuilder()
            formatValue(entry.second, 0, valStr)
            sb.append(valStr.toString().trimStart())
            if (i < map.entries.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("${pad}}")
    }

    private fun formatTag(tag: CborValue.CborTag, indent: Int, sb: StringBuilder) {
        val pad = " ".repeat(indent)
        sb.append("${pad}tag(${tag.tag}) ")
        val contentStr = StringBuilder()
        formatValue(tag.content, 0, contentStr)
        sb.append(contentStr.toString().trimStart())
    }

    private fun formatSimple(simple: CborValue.CborSimple, indent: Int, sb: StringBuilder) {
        val pad = " ".repeat(indent)
        val label = when (simple.value) {
            20 -> "false"
            21 -> "true"
            22 -> "null"
            23 -> "undefined"
            else -> "simple(${simple.value})"
        }
        sb.append("${pad}$label")
    }
}
