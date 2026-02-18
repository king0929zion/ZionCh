package com.zionchat.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zionchat.app.ui.icons.AppIcons
import com.zionchat.app.ui.theme.AccentBlue
import com.zionchat.app.ui.theme.GrayLight
import com.zionchat.app.ui.theme.GrayLighter
import com.zionchat.app.ui.theme.TextPrimary
import com.zionchat.app.ui.theme.TextSecondary
import coil3.compose.AsyncImage
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Document
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.Image
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text as MarkdownTextNode
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.Parser

private sealed class MarkdownSegment {
    data class Text(val markdown: String) : MarkdownSegment()
    data class Table(
        val headers: List<String>,
        val aligns: List<TextAlign>,
        val rows: List<List<String>>
    ) : MarkdownSegment()
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, color = TextPrimary),
    linkColor: Color = AccentBlue
) {
    val parser = remember { Parser.builder().build() }
    val segments = remember(markdown) { splitMarkdownSegments(markdown) }

    Column(modifier = modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.Text -> {
                    if (segment.markdown.isNotBlank()) {
                        val document = parser.parse(segment.markdown) as Document
                        renderChildren(document, textStyle, linkColor)
                    }
                }

                is MarkdownSegment.Table -> {
                    MarkdownTableBlock(segment, textStyle)
                }
            }
        }
    }
}

@Composable
private fun MarkdownTableBlock(
    table: MarkdownSegment.Table,
    textStyle: TextStyle
) {
    val horizontalState = rememberScrollState()
    val columnCount = maxOf(table.headers.size, table.rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
    val minCellWidth = 120.dp
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(width = 1.dp, color = GrayLight, shape = shape)
            .horizontalScroll(horizontalState)
    ) {
        Row(modifier = Modifier.background(GrayLighter)) {
            repeat(columnCount) { index ->
                MarkdownTableCell(
                    text = table.headers.getOrNull(index).orEmpty(),
                    textStyle = textStyle.copy(fontWeight = FontWeight.SemiBold),
                    textAlign = table.aligns.getOrNull(index) ?: TextAlign.Start,
                    minWidth = minCellWidth
                )
            }
        }

        table.rows.forEachIndexed { rowIndex, row ->
            Row(
                modifier =
                    if (rowIndex % 2 == 0) {
                        Modifier.background(Color.White)
                    } else {
                        Modifier.background(Color(0xFFF9F9FB))
                    }
            ) {
                repeat(columnCount) { index ->
                    MarkdownTableCell(
                        text = row.getOrNull(index).orEmpty(),
                        textStyle = textStyle,
                        textAlign = table.aligns.getOrNull(index) ?: TextAlign.Start,
                        minWidth = minCellWidth
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownTableCell(
    text: String,
    textStyle: TextStyle,
    textAlign: TextAlign,
    minWidth: Dp
) {
    Text(
        text = text,
        style = textStyle.copy(textAlign = textAlign, lineHeight = 21.sp),
        modifier = Modifier
            .width(minWidth)
            .border(width = 0.5.dp, color = GrayLight)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

@Composable
private fun renderChildren(
    parent: Node,
    textStyle: TextStyle,
    linkColor: Color,
    spacing: Dp = 8.dp
) {
    var child = parent.firstChild
    if (child == null) return
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing)) {
        while (child != null) {
            MarkdownBlock(child, textStyle, linkColor)
            child = child.next
        }
    }
}

@Composable
private fun MarkdownBlock(
    node: Node,
    textStyle: TextStyle,
    linkColor: Color
) {
    when (node) {
        is Paragraph -> MarkdownParagraph(node, textStyle, linkColor)
        is Heading -> MarkdownHeading(node, textStyle, linkColor)
        is BulletList -> MarkdownBulletList(node, textStyle, linkColor)
        is OrderedList -> MarkdownOrderedList(node, textStyle, linkColor)
        is FencedCodeBlock -> MarkdownCodeBlock(node.literal, textStyle, node.info)
        is IndentedCodeBlock -> MarkdownCodeBlock(node.literal, textStyle)
        is BlockQuote -> MarkdownBlockQuote(node, textStyle, linkColor)
        is ThematicBreak -> Divider(color = GrayLight, thickness = 1.dp)
        else -> {
            val literal = (node as? MarkdownTextNode)?.literal.orEmpty()
            if (literal.isNotBlank()) {
                Text(text = literal, style = textStyle)
            }
        }
    }
}

@Composable
private fun MarkdownParagraph(
    paragraph: Paragraph,
    textStyle: TextStyle,
    linkColor: Color
) {
    val images = remember(paragraph) { collectInlineImages(paragraph) }
    val isOnlyImages = remember(paragraph) { isParagraphOnlyImages(paragraph) }
    val inline = buildInlineAnnotatedString(paragraph, linkColor)

    if (!isOnlyImages && inline.text.isNotBlank()) {
        AnnotatedMarkdownText(text = inline, style = textStyle)
    }

    if (images.isNotEmpty()) {
        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            images.forEach { img ->
                val url = img.destination.orEmpty()
                if (url.isNotBlank()) {
                    val modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                    val dataBitmap = remember(url) { decodeBitmapFromDataUrl(url) }
                    if (dataBitmap != null) {
                        Image(
                            bitmap = dataBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = modifier,
                            contentScale = ContentScale.FillWidth
                        )
                    } else {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = modifier,
                            contentScale = ContentScale.FillWidth
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnotatedMarkdownText(
    text: AnnotatedString,
    style: TextStyle
) {
    val uriHandler = LocalUriHandler.current
    val hasLinks = remember(text) {
        text.getStringAnnotations(tag = "URL", start = 0, end = text.length).isNotEmpty()
    }
    if (!hasLinks) {
        Text(text = text, style = style)
        return
    }
    ClickableText(
        text = text,
        style = style,
        onClick = { offset ->
            text.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()
                ?.item
                ?.let { url -> runCatching { uriHandler.openUri(url) } }
        }
    )
}

private fun decodeBitmapFromDataUrl(dataUrl: String): Bitmap? {
    val url = dataUrl.trim()
    if (!url.startsWith("data:image", ignoreCase = true)) return null
    val commaIndex = url.indexOf(',')
    if (commaIndex < 0) return null
    val meta = url.substring(0, commaIndex)
    if (!meta.contains(";base64", ignoreCase = true)) return null
    val payload = url.substring(commaIndex + 1)
    if (payload.isBlank()) return null
    val decoded = runCatching { Base64.decode(payload, Base64.DEFAULT) }.getOrNull() ?: return null
    if (decoded.isEmpty()) return null
    return runCatching { BitmapFactory.decodeByteArray(decoded, 0, decoded.size) }.getOrNull()
}

@Composable
private fun MarkdownHeading(
    heading: Heading,
    textStyle: TextStyle,
    linkColor: Color
) {
    val size = when (heading.level) {
        1 -> 22.sp
        2 -> 20.sp
        3 -> 18.sp
        4 -> 17.sp
        else -> 16.sp
    }
    AnnotatedMarkdownText(
        text = buildInlineAnnotatedString(heading, linkColor),
        style = textStyle.copy(fontSize = size, fontWeight = FontWeight.SemiBold)
    )
}

@Composable
private fun MarkdownCodeBlock(
    code: String,
    textStyle: TextStyle,
    language: String? = null
) {
    val context = LocalContext.current
    val clipboardManager =
        remember(context) {
            context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        }
    val normalizedCode = remember(code) { code.trimEnd().ifBlank { " " } }
    val languageLabel = remember(language) { formatCodeBlockLanguage(language) }
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xFFEDEDED), shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = languageLabel,
                style = textStyle.copy(
                    fontSize = 13.sp,
                    color = Color(0xFF121212),
                    fontWeight = FontWeight.Medium
                )
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        runCatching {
                            clipboardManager?.setPrimaryClip(ClipData.newPlainText("code", normalizedCode))
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = AppIcons.Copy,
                    contentDescription = "Copy code",
                    tint = Color(0xFF121212),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Copy code",
                    style = textStyle.copy(
                        fontSize = 13.sp,
                        color = Color(0xFF121212),
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp, max = 360.dp)
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = normalizedCode,
                style = textStyle.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = Color(0xFF2E2E2E)
                )
            )
        }
    }
}

@Composable
private fun MarkdownBlockQuote(
    quote: BlockQuote,
    textStyle: TextStyle,
    linkColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 2.dp)
    ) {
        Spacer(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(GrayLight, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            renderChildren(quote, textStyle.copy(color = TextSecondary), linkColor, spacing = 6.dp)
        }
    }
}

@Composable
private fun MarkdownBulletList(
    list: BulletList,
    textStyle: TextStyle,
    linkColor: Color
) {
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        var item = list.firstChild
        while (item != null) {
            if (item is ListItem) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "\u2022",
                        style = textStyle.copy(color = TextSecondary),
                        modifier = Modifier.padding(top = 1.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        renderChildren(item, textStyle, linkColor, spacing = 6.dp)
                    }
                }
            }
            item = item.next
        }
    }
}

@Composable
private fun MarkdownOrderedList(
    list: OrderedList,
    textStyle: TextStyle,
    linkColor: Color
) {
    var index = list.startNumber
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        var item = list.firstChild
        while (item != null) {
            if (item is ListItem) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "$index.",
                        style = textStyle.copy(color = TextSecondary),
                        modifier = Modifier.padding(top = 1.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        renderChildren(item, textStyle, linkColor, spacing = 6.dp)
                    }
                }
                index += 1
            }
            item = item.next
        }
    }
}

private fun isParagraphOnlyImages(paragraph: Paragraph): Boolean {
    var child = paragraph.firstChild
    if (child == null) return false
    var hasImage = false
    while (child != null) {
        when (child) {
            is Image -> hasImage = true
            is SoftLineBreak, is HardLineBreak -> Unit
            else -> return false
        }
        child = child.next
    }
    return hasImage
}

private fun collectInlineImages(parent: Node): List<Image> {
    val out = mutableListOf<Image>()
    fun walk(node: Node?) {
        var child = node?.firstChild
        while (child != null) {
            when (child) {
                is Image -> out.add(child)
                else -> walk(child)
            }
            child = child.next
        }
    }
    walk(parent)
    return out
}

private fun splitMarkdownSegments(markdown: String): List<MarkdownSegment> {
    if (markdown.isBlank()) return listOf(MarkdownSegment.Text(""))
    val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val segments = mutableListOf<MarkdownSegment>()
    val textBuffer = StringBuilder()
    var inFence = false
    var index = 0

    fun flushText() {
        if (textBuffer.isNotEmpty()) {
            segments += MarkdownSegment.Text(textBuffer.toString().trim('\n'))
            textBuffer.clear()
        }
    }

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()
        val startsFence = trimmed.startsWith("```")
        if (!inFence && index + 1 < lines.size && isTableHeaderLine(line) && isTableDividerLine(lines[index + 1])) {
            val headers = parseTableLine(line)
            val aligns = parseTableAligns(lines[index + 1])
            val rows = mutableListOf<List<String>>()
            var cursor = index + 2
            while (cursor < lines.size) {
                val rowLine = lines[cursor]
                if (!isTableDataLine(rowLine)) break
                val rowValues = parseTableLine(rowLine)
                if (rowValues.isNotEmpty()) {
                    rows += rowValues
                }
                cursor += 1
            }
            flushText()
            segments += MarkdownSegment.Table(headers = headers, aligns = aligns, rows = rows)
            index = cursor
            continue
        }

        textBuffer.append(line)
        if (index != lines.lastIndex) textBuffer.append('\n')
        if (startsFence) {
            inFence = !inFence
        }
        index += 1
    }

    flushText()
    if (segments.isEmpty()) {
        return listOf(MarkdownSegment.Text(markdown))
    }
    return segments
}

private fun isTableHeaderLine(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.contains('|')) return false
    if (trimmed.startsWith("```")) return false
    return parseTableLine(trimmed).size >= 2
}

private fun isTableDividerLine(line: String): Boolean {
    val trimmed = line.trim()
    if (!trimmed.contains('-')) return false
    if (!trimmed.contains('|')) return false
    return Regex("^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$").matches(trimmed)
}

private fun isTableDataLine(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isBlank()) return false
    if (!trimmed.contains('|')) return false
    if (trimmed.startsWith("```")) return false
    if (isTableDividerLine(trimmed)) return false
    return parseTableLine(trimmed).isNotEmpty()
}

private fun parseTableLine(line: String): List<String> {
    var raw = line.trim()
    if (raw.startsWith("|")) raw = raw.drop(1)
    if (raw.endsWith("|")) raw = raw.dropLast(1)
    if (raw.isBlank()) return emptyList()
    return raw
        .split(Regex("(?<!\\\\)\\|"))
        .map { it.replace("\\|", "|").trim() }
}

private fun parseTableAligns(line: String): List<TextAlign> {
    return parseTableLine(line).map { token ->
        val value = token.trim()
        when {
            value.startsWith(":") && value.endsWith(":") -> TextAlign.Center
            value.endsWith(":") -> TextAlign.End
            else -> TextAlign.Start
        }
    }
}

private fun formatCodeBlockLanguage(language: String?): String {
    val raw =
        language
            ?.trim()
            ?.split(Regex("\\s+"))
            ?.firstOrNull()
            ?.trim()
            .orEmpty()
    if (raw.isBlank()) return "Plain text"
    val normalized = raw.replace('_', '-').trim('-')
    if (normalized.isBlank()) return "Plain text"
    return normalized
        .split('-')
        .filter { it.isNotBlank() }
        .joinToString(" ") { token ->
            token.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase() else ch.toString()
            }
        }
        .ifBlank { "Plain text" }
}

private fun buildInlineAnnotatedString(parent: Node, linkColor: Color): AnnotatedString {
    val builder = AnnotatedString.Builder()

    fun appendInline(node: Node) {
        when (node) {
            is MarkdownTextNode -> builder.append(node.literal)
            is SoftLineBreak -> builder.append('\n')
            is HardLineBreak -> builder.append('\n')
            is Image -> {
                var child = node.firstChild
                while (child != null) {
                    appendInline(child)
                    child = child.next
                }
            }
            is Emphasis -> {
                val start = builder.length
                var child = node.firstChild
                while (child != null) {
                    appendInline(child)
                    child = child.next
                }
                builder.addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, builder.length)
            }
            is StrongEmphasis -> {
                val start = builder.length
                var child = node.firstChild
                while (child != null) {
                    appendInline(child)
                    child = child.next
                }
                builder.addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), start, builder.length)
            }
            is Code -> {
                val start = builder.length
                builder.append(node.literal)
                builder.addStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF2C2C33),
                        background = Color(0xFFE8E8ED)
                    ),
                    start,
                    builder.length
                )
            }
            is Link -> {
                val start = builder.length
                var child = node.firstChild
                while (child != null) {
                    appendInline(child)
                    child = child.next
                }
                val end = builder.length
                builder.addStyle(
                    SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                    start,
                    end
                )
                if (!node.destination.isNullOrBlank()) {
                    builder.addStringAnnotation("URL", node.destination, start, end)
                }
            }
            else -> {
                var child = node.firstChild
                while (child != null) {
                    appendInline(child)
                    child = child.next
                }
            }
        }
    }

    appendInline(parent)
    return builder.toAnnotatedString()
}
