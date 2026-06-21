package com.better.nothing.music.vizualizer.ui.SecondaryScreens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.better.nothing.music.vizualizer.R
import com.better.nothing.music.vizualizer.ui.BodyText
import com.better.nothing.music.vizualizer.ui.ExpressiveCard
import com.better.nothing.music.vizualizer.ui.ScreenTitle

@Composable
internal fun LicenseScreen(
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
            .verticalScroll(scrollState),
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back)
            )
        }

        ScreenTitle(text = stringResource(R.string.license_title).uppercase())

        ExpressiveCard {
            LicenseText()
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun LicenseText() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.license_agreement_title),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        
        Column {
            Text(stringResource(R.string.license_version), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(stringResource(R.string.license_date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.license_licensor), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.license_licensor_name), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.license_founder), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.license_founder_name), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
        }
        
        Column {
            Text(stringResource(R.string.license_organization), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.license_organization_name), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

        Text(stringResource(R.string.license_section_1), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        BodyText(stringResource(R.string.license_preamble_1), size = 14.sp)
        BodyText(stringResource(R.string.license_preamble_2), size = 14.sp)

        Text(stringResource(R.string.license_section_2), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        BodyText(stringResource(R.string.license_def_1), size = 14.sp)
        BodyText(stringResource(R.string.license_def_2), size = 14.sp)

        Text(stringResource(R.string.license_section_3), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.license_section_3_1), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        BodyText(stringResource(R.string.license_grant_3_1), size = 14.sp)
        Text(stringResource(R.string.license_section_3_2), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        BodyText(stringResource(R.string.license_grant_3_2), size = 14.sp)
        BodyText(stringResource(R.string.license_restriction_a), size = 13.sp)
        BodyText(stringResource(R.string.license_restriction_b), size = 13.sp)
        BodyText(stringResource(R.string.license_restriction_c), size = 13.sp)
        BodyText(stringResource(R.string.license_restriction_d), size = 13.sp)

        Text(stringResource(R.string.license_section_4), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.license_section_4_1), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        BodyText(stringResource(R.string.license_ownership), size = 14.sp)

        Text(stringResource(R.string.license_section_5), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.license_section_5_1), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        BodyText(stringResource(R.string.license_warranty_disclaimer), size = 14.sp)
        Text(stringResource(R.string.license_section_5_2), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        BodyText(stringResource(R.string.license_technical_support), size = 14.sp)

        Text(stringResource(R.string.license_section_6), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.license_section_6_1), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        BodyText(stringResource(R.string.license_liability_cap), size = 14.sp)

        Text(stringResource(R.string.license_section_7), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.license_section_7_1), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        BodyText(stringResource(R.string.license_termination), size = 14.sp)

        Text(stringResource(R.string.license_section_8), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.license_section_8_1), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        BodyText(stringResource(R.string.license_governing_law), size = 14.sp)

        Text(stringResource(R.string.end_of_license), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp), style = MaterialTheme.typography.labelSmall)
    }
}
