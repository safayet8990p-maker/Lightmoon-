package com.example.ui.components

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.ExportConfig
import com.example.model.ExportFormat
import com.example.model.ExportResolution
import com.example.model.OutputSharpening
import com.example.model.WatermarkCorner
import com.example.ui.develop.ExportUiState
import com.example.ui.theme.MonoReadoutStyle
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioSuccess
import com.example.ui.theme.StudioSurface

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExportSheet(
    title: String,
    exportState: ExportUiState,
    onDismiss: () -> Unit,
    onExport: (ExportConfig) -> Unit,
    isBatchMode: Boolean = false
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var config by remember { mutableStateOf(ExportConfig()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StudioSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text("Format", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ExportFormat.entries.forEach { fmt ->
                    FilterChip(
                        selected = config.format == fmt,
                        onClick = { config = config.copy(format = fmt) },
                        label = { Text(fmt.label) }
                    )
                }
            }

            if (config.format != ExportFormat.SIDECAR_RECIPE) {
                Spacer(modifier = Modifier.height(12.dp))
                if (config.format != ExportFormat.PNG) {
                    StudioSlider(
                        label = "Compression Quality",
                        value = config.quality.toFloat(),
                        valueRange = 50f..100f,
                        defaultValue = 92f,
                        valueText = "${config.quality}%",
                        onValueChange = { config = config.copy(quality = it.toInt()) },
                        onValueChangeFinished = {}
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Resolution & Sizing", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ExportResolution.entries.forEach { res ->
                        FilterChip(
                            selected = config.resolution == res,
                            onClick = { config = config.copy(resolution = res) },
                            label = { Text(res.label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Output Sharpening", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutputSharpening.entries.forEach { sharp ->
                        FilterChip(
                            selected = config.sharpening == sharp,
                            onClick = { config = config.copy(sharpening = sharp) },
                            label = { Text(sharp.label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Preserve Camera Metadata", style = MaterialTheme.typography.bodyLarge)
                        Text("Keep EXIF camera & lens tags", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = config.includeMetadata,
                        onCheckedChange = { config = config.copy(includeMetadata = it) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Watermark Overlay", style = MaterialTheme.typography.bodyLarge)
                        Text("Stamp studio signature on export", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = config.watermarkEnabled,
                        onCheckedChange = { config = config.copy(watermarkEnabled = it) }
                    )
                }

                if (config.watermarkEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = config.watermarkText,
                        onValueChange = { config = config.copy(watermarkText = it) },
                        label = { Text("Watermark Text") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    StudioSlider(
                        label = "Watermark Opacity",
                        value = config.watermarkOpacity * 100f,
                        valueRange = 15f..100f,
                        defaultValue = 60f,
                        valueText = "${(config.watermarkOpacity * 100).toInt()}%",
                        onValueChange = { config = config.copy(watermarkOpacity = it / 100f) },
                        onValueChangeFinished = {}
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WatermarkCorner.entries.forEach { corner ->
                            FilterChip(
                                selected = config.watermarkCorner == corner,
                                onClick = { config = config.copy(watermarkCorner = corner) },
                                label = { Text(corner.label) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = config.filenamePrefix,
                onValueChange = { config = config.copy(filenamePrefix = it) },
                label = { Text("Filename Prefix") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (exportState) {
                ExportUiState.Exporting -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), color = StudioCyan, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Rendering full-resolution pipeline…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is ExportUiState.Success -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StudioSuccess)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Export Complete", style = MaterialTheme.typography.titleSmall, color = StudioSuccess)
                            Text(exportState.fileName, style = MonoReadoutStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = config.format.mimeType
                                    putExtra(Intent.EXTRA_STREAM, exportState.shareUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share ${exportState.fileName}"))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Done")
                        }
                    }
                }
                is ExportUiState.Error -> {
                    Text(
                        text = exportState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onExport(config) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Retry Export")
                    }
                }
                ExportUiState.Idle -> {
                    Button(
                        onClick = { onExport(config) },
                        colors = ButtonDefaults.buttonColors(containerColor = StudioCyan, contentColor = MaterialTheme.colorScheme.onSecondary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("confirm_export_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isBatchMode) "Batch Export to Pictures/Lightmoon" else "Export to Pictures/Lightmoon")
                    }
                }
            }
        }
    }
}
