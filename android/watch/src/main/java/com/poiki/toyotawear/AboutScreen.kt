package com.poiki.toyotawear

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.*
import kotlinx.coroutines.launch

@Composable
fun AboutScreen(onClear: () -> Unit) {
    val context = LocalContext.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
    val checking by Store.updateChecking.collectAsState()
    val status by Store.updateStatus.collectAsState()
    val busy by Store.busy.collectAsState()
    val loadingTrips by Store.tripsLoading.collectAsState()
    val updating by Store.updating.collectAsState()
    val canClear = busy == null && !loadingTrips && !updating
    var confirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val state = rememberTransformingLazyColumnState()
    Box(Modifier.fillMaxSize().cockpit()) {
        ScreenScaffold(scrollState = state) { padding ->
            TransformingLazyColumn(state = state, contentPadding = padding, modifier = Modifier.padding(horizontal = 24.dp)) {
                item { ListHeader { AboutText(stringResource(R.string.about_title)) } }
                item { AboutText(stringResource(R.string.app_name)) }
                item { AboutText(stringResource(R.string.about_version, version.versionName ?: "—", version.longVersionCode)) }
                item { AboutText(stringResource(R.string.about_description)) }
                item {
                    Button(onClick = { scope.launch { Store.checkUpdate(force = true) } }, enabled = !checking && !updating,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        AboutText(stringResource(if (checking) R.string.about_checking else R.string.about_check_updates))
                    }
                }
                status?.let { item { AboutText(it) } }
                item {
                    OutlinedButton(onClick = { confirm = true }, enabled = canClear,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        AboutText(stringResource(R.string.about_clear))
                    }
                }
            }
        }
    }
    AlertDialog(visible = confirm, onDismissRequest = { confirm = false },
        title = { AboutText(stringResource(R.string.about_clear)) },
        text = { AboutText(stringResource(R.string.about_clear_confirm)) },
        confirmButton = { AlertDialogDefaults.ConfirmButton(onClick = { if (canClear) { confirm = false; onClear() } }) },
        dismissButton = { AlertDialogDefaults.DismissButton(onClick = { confirm = false }) })
}

@Composable
private fun AboutText(value: String) = Text(value, fontSize = 12.sp, lineHeight = 15.sp,
    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
