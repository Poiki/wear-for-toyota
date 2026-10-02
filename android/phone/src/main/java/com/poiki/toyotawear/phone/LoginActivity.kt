package com.poiki.toyotawear.phone

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import com.poiki.toyotawear.core.Tokens
import com.poiki.toyotawear.core.ToyotaAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The whole phone app: log in to Toyota once and hand the tokens to the paired watch. Nothing is stored here. */
class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { LoginScreen() } }
    }
}

@Composable
private fun LoginScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var lexus by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val note = stringResource(R.string.phone_note)
    val busyText = stringResource(R.string.phone_busy)
    var status by remember { mutableStateOf(note) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.phone_title), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(stringResource(R.string.phone_email)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(stringResource(R.string.phone_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = lexus, onCheckedChange = { lexus = it })
            Text("  " + stringResource(R.string.phone_lexus))
        }
        Button(
            onClick = {
                val pw = password
                password = ""
                busy = true
                status = busyText
                scope.launch {
                    status = runCatching {
                        withContext(Dispatchers.IO) {
                            val tokens = ToyotaAuth.login(email.trim(), pw, if (lexus) "L" else "T")
                            sendToWatch(context, tokens)
                        }
                    }.fold(
                        onSuccess = { context.getString(R.string.phone_sent, it) },
                        onFailure = { it.message ?: it.javaClass.simpleName },
                    )
                    busy = false
                }
            },
            enabled = !busy && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.phone_button)) }
        Text(status, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Sends the tokens to every connected watch node over the Data Layer (Bluetooth-encrypted). Returns the node names. */
private fun sendToWatch(context: Context, tokens: Tokens): String {
    val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
    if (nodes.isEmpty()) throw IllegalStateException(context.getString(R.string.phone_no_watch))
    val bytes = tokens.toJson().toByteArray()
    val client = Wearable.getMessageClient(context)
    nodes.forEach { Tasks.await(client.sendMessage(it.id, "/toyota/tokens", bytes)) }
    return nodes.joinToString { it.displayName }
}
