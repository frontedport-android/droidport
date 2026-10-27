package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GamerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = true,
    icon: @Composable (() -> Unit)? = null
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    val bgBrush = if (isPrimary) {
        Brush.verticalGradient(
            listOf(primaryColor, primaryColor.copy(alpha = 0.7f))
        )
    } else {
        Brush.verticalGradient(
            listOf(secondaryColor, secondaryColor.copy(alpha = 0.7f))
        )
    }

    val textColor = if (isPrimary) MaterialTheme.colorScheme.onPrimary else Color.White

    Box(
        modifier = modifier
            .heightIn(min = 38.dp)
            .shadow(6.dp, RoundedCornerShape(14.dp), ambientColor = primaryColor, spotColor = primaryColor)
            .clip(RoundedCornerShape(14.dp))
            .background(bgBrush)
            .border(1.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text.uppercase(),
                color = textColor,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
