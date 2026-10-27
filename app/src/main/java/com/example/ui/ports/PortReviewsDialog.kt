package com.example.ui.ports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.Port
import com.example.model.Review

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortReviewsDialog(
    port: Port,
    viewModel: PortViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val reviews by viewModel.observeReviews(port.id).collectAsStateWithLifecycle(initialValue = emptyList())

    var userName by remember { mutableStateOf("") }
    var rating by remember { mutableFloatStateOf(5f) }
    var comment by remember { mutableStateOf("") }
    var showAddForm by remember { mutableStateOf(false) }

    val averageRating = if (reviews.isNotEmpty()) {
        reviews.map { it.rating }.average().toFloat()
    } else {
        0.0f
    }

    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${context.getString(R.string.reviews)}: ${port.name}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                if (port.realGameTitle.isNotBlank() && !port.realGameTitle.equals(port.name, ignoreCase = true)) {
                    Text(
                        text = "(${port.realGameTitle})",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(5) { index ->
                        Icon(
                            imageVector = if (index < averageRating) Icons.Filled.Star else Icons.Outlined.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = String.format("%.1f (%d)", averageRating, reviews.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isLandscape) 270.dp else 370.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (showAddForm) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(context.getString(R.string.leave_rating), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            OutlinedTextField(
                                value = userName,
                                onValueChange = { userName = it },
                                label = { Text(context.getString(R.string.your_name)) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // 5 Interactive Star Icons (NO slider bar)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${context.getString(R.string.rating)}:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    (1..5).forEach { starIndex ->
                                        val isSelected = starIndex <= rating.toInt()
                                        Icon(
                                            imageVector = if (isSelected) Icons.Filled.Star else Icons.Outlined.Star,
                                            contentDescription = "Star $starIndex",
                                            tint = if (isSelected) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clickable { rating = starIndex.toFloat() }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = comment,
                                onValueChange = { comment = it },
                                label = { Text(context.getString(R.string.comment)) },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { showAddForm = false }) {
                                    Text(context.getString(R.string.cancel))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (userName.isNotBlank() && comment.isNotBlank()) {
                                            viewModel.addReview(
                                                port.id,
                                                Review(
                                                    userName = userName.trim(),
                                                    rating = rating,
                                                    comment = comment.trim()
                                                )
                                            )
                                            userName = ""
                                            comment = ""
                                            showAddForm = false
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(context.getString(R.string.submit))
                                }
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = { showAddForm = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(context.getString(R.string.add_review))
                    }
                }

                if (reviews.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No reviews yet. Be the first!", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(reviews, key = { it.id }) { review ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(review.userName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                        Row {
                                            repeat(review.rating.toInt()) {
                                                Icon(
                                                    Icons.Filled.Star,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFFD700),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(review.comment, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

