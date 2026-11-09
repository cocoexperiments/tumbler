package com.cocoexperiments.tumbler.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.cocoexperiments.tumbler.R

/** "tumbler" in light lowercase, with an optional "by Coco Experiments" byline. */
@Composable
fun Wordmark(
    modifier: Modifier = Modifier,
    large: Boolean = false,
    showByline: Boolean = true,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(modifier, horizontalAlignment = horizontalAlignment) {
        Text(
            text = stringResource(R.string.brand_name),
            style = if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Light,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.primary,
        )
        if (showByline) {
            Text(
                text = stringResource(R.string.brand_byline),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Tappable "A Coco Experiments product" line that opens the company site. */
@Composable
fun CompanyFooter(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(
            text = stringResource(R.string.brand_footer),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
