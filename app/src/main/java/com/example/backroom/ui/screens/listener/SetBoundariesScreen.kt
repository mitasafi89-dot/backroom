package com.example.backroom.ui.screens.listener

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.backroom.data.PreferencesManager
import com.example.backroom.ui.screens.sharer.Topic
import com.example.backroom.ui.theme.BackroomTheme

enum class MaxIntensity(val title: String, val description: String) {
    LIGHT_ONLY("Light only", "Just casual chats"),
    UP_TO_HEAVY("Up to Heavy", "Can handle some weight"),
    ANY("Any (incl. Very Heavy)", "Ready for anything")
}

data class ListenerBoundaries(
    val acceptedTopics: Set<Topic> = Topic.entries.toSet() - Topic.SOMETHING_HARD,
    val maxIntensity: MaxIntensity = MaxIntensity.UP_TO_HEAVY,
    val maxDurationMinutes: Int = 15
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetBoundariesScreen(
    onBackClick: () -> Unit = {},
    onSave: () -> Unit = {}
) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }

    // Load saved values from preferences
    var acceptedTopics by remember {
        mutableStateOf(
            preferencesManager.acceptedTopics.mapNotNull { name ->
                try { Topic.valueOf(name) } catch (e: Exception) { null }
            }.toSet()
        )
    }
    var maxIntensity by remember {
        mutableStateOf(
            try { MaxIntensity.valueOf(preferencesManager.maxIntensity) }
            catch (e: Exception) { MaxIntensity.UP_TO_HEAVY }
        )
    }
    var maxDuration by remember {
        mutableFloatStateOf(preferencesManager.maxDurationMinutes.toFloat())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar
        TopAppBar(
            title = {
                Text(
                    text = "My Boundaries",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Topics Section
            Text(
                text = "Topics I'll Accept",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(8.dp)
            ) {
                Topic.entries.forEach { topic ->
                    TopicCheckbox(
                        topic = topic,
                        isChecked = topic in acceptedTopics,
                        onCheckedChange = { checked ->
                            acceptedTopics = if (checked) {
                                acceptedTopics + topic
                            } else {
                                acceptedTopics - topic
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Intensity Section
            Text(
                text = "Emotional Intensity",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Max I can handle:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(8.dp)
            ) {
                MaxIntensity.entries.forEach { intensity ->
                    IntensityOption(
                        intensity = intensity,
                        isSelected = maxIntensity == intensity,
                        onClick = { maxIntensity = intensity }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Duration Section
            Text(
                text = "Max Call Duration",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp)
            ) {
                Slider(
                    value = maxDuration,
                    onValueChange = { maxDuration = it },
                    valueRange = 5f..30f,
                    steps = 4
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Currently: ${maxDuration.toInt()} minutes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Save Button
            Button(
                onClick = {
                    // Save to preferences
                    preferencesManager.acceptedTopics = acceptedTopics.map { it.name }.toSet()
                    preferencesManager.maxIntensity = maxIntensity.name
                    preferencesManager.maxDurationMinutes = maxDuration.toInt()
                    onSave()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "Save Boundaries",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun TopicCheckbox(
    topic: Topic,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
        Text(
            text = topic.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun IntensityOption(
    intensity: MaxIntensity,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
        Text(
            text = intensity.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun SetBoundariesScreenPreview() {
    BackroomTheme {
        SetBoundariesScreen()
    }
}

@Preview(showBackground = true, showSystemUi = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SetBoundariesScreenDarkPreview() {
    BackroomTheme(darkTheme = true) {
        SetBoundariesScreen()
    }
}

