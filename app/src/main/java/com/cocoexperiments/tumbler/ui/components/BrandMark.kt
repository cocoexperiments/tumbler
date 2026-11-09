package com.cocoexperiments.tumbler.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cocoexperiments.tumbler.R

/** The padlock-keypad mark on its own. The drawable is 60x72, so keep that 5:6 ratio. */
@Composable
fun LogoMark(modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 40.dp) {
    Image(
        painter = painterResource(R.drawable.ic_logo),
        contentDescription = null, // decorative: the wordmark next to it carries the name
        modifier = modifier.size(width = height * 5f / 6f, height = height),
    )
}

/** Logo mark + "tumbler" in light lowercase, with an optional "by Coco Experiments" byline. */
@Composable
fun Wordmark(
    modifier: Modifier = Modifier,
    large: Boolean = false,
    showByline: Boolean = true,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(modifier, horizontalAlignment = horizontalAlignment) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (large) 14.dp else 10.dp)) {
            LogoMark(height = if (large) 56.dp else 32.dp)
            Column {
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
