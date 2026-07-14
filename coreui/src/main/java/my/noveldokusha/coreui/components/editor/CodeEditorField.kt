package my.noveldokusha.coreui.components.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

/**
 * A scrollable code editor: a line-number gutter on the left and a
 * syntax-highlighted [BasicTextField] on the right, both sharing a single
 * vertical scroll state so they stay in sync.
 */
@Composable
fun CodeEditorField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    language: String,
    fontSize: Int,
    showLineNumbers: Boolean,
    wordWrap: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalSyntaxColors.current
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    val textStyle = TextStyle(
        color = colors.foreground,
        fontFamily = FontFamily.Monospace,
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.5f).sp,
    )

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(verticalScrollState)
    ) {
        if (showLineNumbers) {
            LineNumberGutter(
                text = value.text,
                layout = textLayout,
                fontSize = fontSize,
                colors = colors,
            )
        }

        val fieldModifier = if (wordWrap) {
            Modifier.fillMaxWidth()
        } else {
            Modifier.width(IntrinsicSize.Max).fillMaxHeight()
        }

        Box(
            modifier = Modifier
                .padding(top = 8.dp, start = 4.dp, end = 16.dp, bottom = 48.dp)
                .then(
                    if (wordWrap) Modifier.fillMaxWidth()
                    else Modifier.horizontalScroll(horizontalScrollState)
                )
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = textStyle,
                cursorBrush = SolidColor(colors.foreground),
                visualTransformation = { text ->
                    TransformedText(
                        highlight(text.text, language, colors),
                        OffsetMapping.Identity
                    )
                },
                onTextLayout = { textLayout = it },
                modifier = fieldModifier,
            )
        }
    }
}

@Composable
private fun LineNumberGutter(
    text: String,
    layout: TextLayoutResult?,
    fontSize: Int,
    colors: SyntaxColors,
) {
    val density = LocalDensity.current
    val lineHeightSp = (fontSize * 1.5f).sp

    Column(modifier = Modifier.width(48.dp).padding(top = 8.dp, end = 4.dp)) {
        if (layout == null || layout.lineCount == 0) {
            val lineCount = text.count { it == '\n' } + 1
            for (line in 1..lineCount) {
                Text(
                    text = line.toString(),
                    color = colors.gutterText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize.sp,
                    lineHeight = lineHeightSp,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            return
        }

        var logicalLine = 1
        for (visualLine in 0 until layout.lineCount) {
            val start = layout.getLineStart(visualLine)
            val isLogicalStart = start == 0 || text.getOrNull(start - 1) == '\n'
            val rowHeight = with(density) {
                (layout.getLineBottom(visualLine) - layout.getLineTop(visualLine)).toDp()
            }

            Text(
                text = if (isLogicalStart) logicalLine.toString() else "",
                color = colors.gutterText,
                fontFamily = FontFamily.Monospace,
                fontSize = fontSize.sp,
                lineHeight = lineHeightSp,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().height(rowHeight),
            )

            if (isLogicalStart) logicalLine++
        }
    }
}
