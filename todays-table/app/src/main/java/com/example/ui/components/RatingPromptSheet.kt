package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.MealImageState
import com.example.model.UnratedMeal
import com.example.ui.MealViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatingPromptSheet(
    meal: UnratedMeal,
    imageState: MealImageState?,
    isSubmitting: Boolean,
    submitError: String?,
    onDismiss: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    onSubmitRating: (date: String, anissa: String, adamme: String, notes: String) -> Unit
) {
    BackHandler(enabled = !isSubmitting) {
        onDismiss()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val todayFormatted = remember { MealViewModel.formatRelativeDate(0) }

    var dateInput by rememberSaveable(meal.uniqueId) { mutableStateOf(todayFormatted) }
    var anissaChoice by rememberSaveable(meal.uniqueId) { mutableStateOf("") }
    var adammeChoice by rememberSaveable(meal.uniqueId) { mutableStateOf("") }
    var notesInput by rememberSaveable(meal.uniqueId) { mutableStateOf("") }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedMillis = datePickerState.selectedDateMillis
                        if (selectedMillis != null) {
                            dateInput = MealViewModel.formatUtcMillisToDate(selectedMillis)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Select Date")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    val canSubmit = !isSubmitting &&
        dateInput.isNotBlank() &&
        anissaChoice.isNotBlank() &&
        adammeChoice.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = {
            if (!isSubmitting) onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(20.dp))
            ) {
                MealPhotoBanner(
                    meal = meal,
                    imageState = imageState,
                    height = 185.dp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ServiceBadge(tab = meal.tab)

                    if (!meal.recipeUrl.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = { onOpenRecipe(meal.recipeUrl) },
                            modifier = Modifier.testTag("prompt_open_recipe_button"),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = stringResource(R.string.action_open_recipe),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.action_open_recipe),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                Text(
                    text = meal.displayMealName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.label_date_eaten),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isToday = dateInput == todayFormatted
                        Surface(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(CircleShape)
                                .clickable { dateInput = todayFormatted }
                                .testTag("date_chip_today"),
                            shape = CircleShape,
                            color = if (isToday) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            },
                            border = if (isToday) {
                                null
                            } else {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (isToday) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = if (isToday) {
                                        "Today ($todayFormatted)"
                                    } else {
                                        stringResource(R.string.chip_today)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isToday) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .height(36.dp)
                                .clip(CircleShape)
                                .clickable { showDatePicker = true }
                                .testTag("date_chip_calendar"),
                            shape = CircleShape,
                            color = if (!isToday) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            },
                            border = if (!isToday) {
                                null
                            } else {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = stringResource(R.string.chip_pick_date),
                                    tint = if (!isToday) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = if (!isToday) {
                                        dateInput
                                    } else {
                                        stringResource(R.string.chip_pick_date)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (!isToday) FontWeight.Bold else FontWeight.Medium,
                                    color = if (!isToday) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                ScoreSelectorSection(
                    label = stringResource(R.string.label_anissa_score),
                    selectedChoice = anissaChoice,
                    onChoiceSelected = { anissaChoice = it },
                    tagPrefix = "anissa_score"
                )

                ScoreSelectorSection(
                    label = stringResource(R.string.label_adamme_score),
                    selectedChoice = adammeChoice,
                    onChoiceSelected = { adammeChoice = it },
                    tagPrefix = "adamme_score"
                )

                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = { Text(stringResource(R.string.label_notes_optional)) },
                    placeholder = { Text(stringResource(R.string.placeholder_notes)) },
                    enabled = !isSubmitting,
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("notes_input")
                )

                if (!submitError.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = submitError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        onSubmitRating(dateInput, anissaChoice, adammeChoice, notesInput.trim())
                    },
                    enabled = canSubmit,
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("submit_rating_button")
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.action_submitting_rating),
                            style = MaterialTheme.typography.titleMedium
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_submit_rating),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreSelectorSection(
    label: String,
    selectedChoice: String,
    onChoiceSelected: (String) -> Unit,
    tagPrefix: String
) {
    val allOptions = remember { listOf("-", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (selectedChoice.isNotBlank()) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    val badgeText = if (selectedChoice == "-") {
                        "Didn’t eat (-)"
                    } else {
                        val scoreInt = selectedChoice.toIntOrNull() ?: 0
                        "$selectedChoice / 10 • ${scoreSentiment(scoreInt)}"
                    }
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            allOptions.forEach { option ->
                ScorePillButton(
                    optionLabel = option,
                    isSelected = selectedChoice == option,
                    onClick = { onChoiceSelected(option) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("${tagPrefix}_${if (option == "-") "nil" else option}")
                )
            }
        }
    }
}

@Composable
private fun ScorePillButton(
    optionLabel: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val border = if (isSelected) {
        null
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    }

    Surface(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
        border = border
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = optionLabel,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}

private fun scoreSentiment(score: Int): String = when (score) {
    10 -> "All-Time Favorite"
    9 -> "Amazing"
    8 -> "Great"
    7 -> "Good"
    6 -> "Decent"
    5 -> "Average"
    4 -> "Below Average"
    3 -> "Disappointing"
    2 -> "Rough"
    else -> "Never Again"
}
