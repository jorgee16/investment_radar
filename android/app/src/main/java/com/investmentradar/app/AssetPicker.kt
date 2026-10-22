package com.investmentradar.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable internal fun SectorMenu(sectors: List<String>, selected: String, tag: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton({ expanded = true }, Modifier.testTag(tag)) { Text("Setor: $selected ▾") }
        DropdownMenu(expanded, { expanded = false }) {
            (listOf("Todos") + sectors.distinct().sorted()).forEach { sector ->
                DropdownMenuItem(text = { Text(sector) }, onClick = { onSelect(sector); expanded = false }, modifier = Modifier.testTag("$tag-$sector"))
            }
        }
    }
}

@Composable internal fun AssetSuggestions(query: String, tag: String, onSelect: (DirectoryAsset) -> Unit) {
    var sector by rememberSaveable { mutableStateOf("Todos") }
    SectorMenu(investmentSectors + assetDirectory.map { it.sector }, sector, "$tag-sector") { sector = it }
    val catalogue = rememberCataloguePage(query, sector = sector)
    val matches = (assetDirectory.filter { it.matches(query, sector) } + catalogue.page.items).distinctBy { it.symbol }
    Text("Catálogo global · ${catalogue.page.matches} correspondências", style = MaterialTheme.typography.labelMedium)
    matches.take(5).forEach { asset ->
        Column(Modifier.fillMaxWidth().clickable { onSelect(asset) }.testTag("$tag-suggestion-${asset.symbol}").padding(vertical = 10.dp)) {
            Text(asset.name, fontWeight = FontWeight.SemiBold)
            Text("${asset.symbol} · ${asset.currency} · ${asset.sector}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (matches.size > 5) Text("Escreve um nome para refinar os resultados.", style = MaterialTheme.typography.bodySmall)
    Text("Não encontras o ativo? Podes pesquisar nos providers ou introduzir o ticker. Confirma a bolsa e a moeda; este catálogo não confirma disponibilidade na XTB.", style = MaterialTheme.typography.bodySmall)
}
