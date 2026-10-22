package com.investmentradar.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Local identity mark, not an issuer logo; works offline without tracking requests. */
@Composable internal fun AssetIdentityIcon(asset: DirectoryAsset) {
    val fund = asset.type == "ETF"
    Surface(Modifier.size(44.dp).semantics { contentDescription = if (fund) "ETF ${asset.name}" else asset.name },
        shape = RoundedCornerShape(14.dp), color = if (fund) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer) {
        Box(contentAlignment = Alignment.Center) {
            Text(if (fund) "ETF" else asset.name.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase(),
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                color = if (fund) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
internal fun assetDisplayName(symbol: String, providerName: String, asset: DirectoryAsset?): String =
    asset?.name?.takeIf { it.isNotBlank() && it != symbol } ?: providerName.takeIf { it.isNotBlank() && it != symbol } ?: symbol
