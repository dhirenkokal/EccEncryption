@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.newauth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.newauth.objects.KeyPairUtils

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                AuthenticationScreen(
                    context = this,
                    onCreateQrClick = { navigateToCreateQrActivity() },
                    onGetQrClick = { navigateToGetQrActivity() },
                    onEncryptTextClick = { navigateToEncryptTextActivity() },
                    onDecryptTextClick = { navigateToDecryptTextActivity() },
                    onClearKeysClick = {
                        KeyPairUtils.clearAllKeys(context = this)
                        Toast.makeText(this, "Keys Cleared", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    private fun navigateToCreateQrActivity() {
        val intent = Intent(this, CreateQrActivity::class.java)
        startActivity(intent)
    }

    private fun navigateToGetQrActivity() {
        val intent = Intent(this, GetQrActivity::class.java)
        startActivity(intent)
    }

    private fun navigateToEncryptTextActivity() {
        val intent = Intent(this, EncryptTextActivity::class.java)
        startActivity(intent)
    }

    private fun navigateToDecryptTextActivity() {
        val intent = Intent(this, DecryptTextActivity::class.java)
        startActivity(intent)
    }
}

@Composable
fun AuthenticationScreen(
    context: Context,
    onCreateQrClick: () -> Unit,
    onGetQrClick: () -> Unit,
    onEncryptTextClick: () -> Unit,
    onDecryptTextClick: () -> Unit,
    onClearKeysClick: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Authentication") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Button(onClick = onCreateQrClick) {
                Text("Create QR")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = onGetQrClick) {
                Text("Get QR")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = {
                onEncryptTextClick()
            }) {
                Text("Encrypt Text")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = onDecryptTextClick) {
                Text("Decrypt Text")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = onClearKeysClick ) {
                Text("Clear Keys")
            }
        }
    }
}
