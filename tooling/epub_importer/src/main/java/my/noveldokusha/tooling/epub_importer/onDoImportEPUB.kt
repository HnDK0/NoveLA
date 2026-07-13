package my.noveldokusha.tooling.epub_importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun onDoImportEPUB(): () -> Unit = onDoImportLocalBook(LocalBookImportType.EPUB)

@Composable
fun onDoImportTXT(): () -> Unit = onDoImportLocalBook(LocalBookImportType.TXT)

@Composable
fun onDoImportPDF(): () -> Unit = onDoImportLocalBook(LocalBookImportType.PDF)

@Composable
private fun onDoImportLocalBook(type: LocalBookImportType): () -> Unit {
    val context = LocalContext.current
    val fileExplorer = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null)
                EpubImportService.start(ctx = context, uri = uri, type = type)
        }
    )
    return { fileExplorer.launch(type.mimeType) }
}
