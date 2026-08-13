package com.lobster.clawbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lobster.clawbox.ui.theme.Bg
import com.lobster.clawbox.ui.theme.Bg3
import com.lobster.clawbox.ui.theme.Pink
import com.lobster.clawbox.ui.theme.Purple
import com.lobster.clawbox.ui.theme.TextMuted
import com.lobster.clawbox.ui.theme.TextPrimary

/** Gradient lobster logo mark. */
@Composable
fun ClawLogo(size: Int = 28) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Brush.linearGradient(listOf(Purple, Pink))),
        contentAlignment = Alignment.Center,
    ) {
        Text("🦞", fontSize = (size * 0.52f).sp)
    }
}

/** Top app bar with logo + title + subtitle + trailing slot. */
@Composable
fun ClawTopBar(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Bg)
            .padding(horizontal = 15.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        ClawLogo()
        Column {
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (subtitle != null) {
                Text(subtitle, color = TextMuted, fontSize = 8.5.sp)
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

/** Section label used across screens. */
@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        color = TextMuted,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/** Card container with the standard ClawBox surface look. */
@Composable
fun ClawCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
    ) {
        content()
    }
}

/** Small pill used for status tags. */
@Composable
fun Pill(text: String, color: Color, bg: Color = color.copy(alpha = 0.12f)) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(text, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StatusDot(color: Color, size: Int = 9) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(50))
            .background(color),
    )
}
