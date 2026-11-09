package com.example.dynamiclock.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.dynamiclock.R

/** Standard phone keypad, used only while registering / changing the PIN. */
@Composable
fun NumberPad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { digit -> DigitKey(digit, enabled, onDigit) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.weight(1f))
            DigitKey(0, enabled, onDigit)
            val description = stringResource(R.string.backspace)
            KeyButton(
                onClick = onBackspace,
                shape = CircleShape,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1.35f)
                    .semantics { contentDescription = description },
            ) {
                Text("⌫", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun RowScope.DigitKey(digit: Int, enabled: Boolean, onDigit: (Int) -> Unit) {
    KeyButton(
        onClick = { onDigit(digit) },
        shape = CircleShape,
        enabled = enabled,
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1.35f),
    ) {
        Text(digit.toString(), style = MaterialTheme.typography.headlineSmall)
    }
}
