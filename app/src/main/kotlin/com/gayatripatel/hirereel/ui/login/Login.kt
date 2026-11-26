package com.gayatripatel.hirereel.ui.login

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gayatripatel.hirereel.core.OtpParser
import com.gayatripatel.hirereel.data.SessionRepository
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.koin.androidx.compose.koinViewModel

data class LoginUiState(
    val phone: String = "",
    val codeSent: Boolean = false,
    val code: String = "",
    val error: String? = null,
    val signedIn: Boolean = false,
)

class LoginViewModel(private val session: SessionRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onPhone(value: String) = _state.update { it.copy(phone = value.filter(Char::isDigit).take(10), error = null) }

    fun sendCode() = _state.update {
        if (it.phone.length == 10) it.copy(codeSent = true, error = null) else it.copy(error = "Enter a 10-digit mobile number")
    }

    fun onCode(value: String) = _state.update { it.copy(code = value.filter(Char::isDigit).take(6), error = null) }

    fun onSms(message: String) {
        OtpParser.parse(message)?.let { code ->
            onCode(code)
            verify()
        }
    }

    fun verify() {
        val s = _state.value
        if (s.code.length != 6) {
            _state.update { it.copy(error = "Enter the 6-digit code") }
            return
        }
        // Demo backend accepts any code.
        session.signIn(s.phone)
        _state.update { it.copy(signedIn = true) }
    }
}

@Composable
fun LoginRoute(onSignedIn: () -> Unit, viewModel: LoginViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (state.codeSent) SmsRetrieverEffect(viewModel::onSms)
    LaunchedEffect(state.signedIn) { if (state.signedIn) onSignedIn() }
    LoginScreen(state, viewModel::onPhone, viewModel::sendCode, viewModel::onCode, viewModel::verify)
}

/** SMS Retriever: reads the verification SMS addressed to this app without the READ_SMS permission. */
@Composable
private fun SmsRetrieverEffect(onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onMessage)
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val extras = intent.extras ?: return
                val status = BundleCompat.getParcelable(extras, SmsRetriever.EXTRA_STATUS, Status::class.java) ?: return
                if (status.statusCode == CommonStatusCodes.SUCCESS) extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE)?.let { latest(it) }
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION),
            SmsRetriever.SEND_PERMISSION, null, ContextCompat.RECEIVER_EXPORTED,
        )
        SmsRetriever.getClient(context).startSmsRetriever()
        onDispose { context.unregisterReceiver(receiver) }
    }
}

@Composable
fun LoginScreen(
    state: LoginUiState,
    onPhone: (String) -> Unit,
    onSendCode: () -> Unit,
    onCode: (String) -> Unit,
    onVerify: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, Color(0xFF7C3AED), MaterialTheme.colorScheme.secondary)))
            .safeDrawingPadding(),
    ) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(28.dp), verticalArrangement = Arrangement.Center) {
            Box(
                Modifier.size(64.dp).background(Color.White, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.PlayArrow, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp)) }
            Spacer(Modifier.height(20.dp))
            Text("HireReel", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text(
                "Watch the team, then answer with a 60-second video instead of a cover letter.",
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Surface(shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp)) {
                Text(if (state.codeSent) "Enter the code" else "Sign in with your phone", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))
                if (!state.codeSent) {
                    OutlinedTextField(
                        value = state.phone,
                        onValueChange = onPhone,
                        label = { Text("Mobile number") },
                        prefix = { Text("+1 ") },
                        singleLine = true,
                        isError = state.error != null,
                        supportingText = { state.error?.let { Text(it) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = onSendCode, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Send code") }
                } else {
                    OutlinedTextField(
                        value = state.code,
                        onValueChange = onCode,
                        label = { Text("6-digit code") },
                        singleLine = true,
                        isError = state.error != null,
                        supportingText = { Text(state.error ?: "Sent to +1 ${state.phone}") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Text("  We'll fill it in automatically when the SMS arrives", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = onVerify, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Verify") }
                    TextButton(onClick = onSendCode, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Resend code") }
                }
            }
        }
    }
}
