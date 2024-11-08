@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.newauth

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.newauth.objects.KeyPairUtils
import com.example.newauth.objects.KeyPairUtils.base64ToPublicKey
import com.example.newauth.objects.KeyPairUtils.encryptDataWithPublicKey
import com.example.newauth.objects.KeyPairUtils.getOtherDevicePublicKey
import com.example.newauth.objects.QRCodeGenerator

class EncryptTextActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            EncryptTextScreen(this)
        }
    }
}

@Composable
fun EncryptTextScreen(context: Context) {
    var textToEncrypt by remember { mutableStateOf(TextFieldValue("Hello, Dhiren")) }
    var encryptedText by remember { mutableStateOf<String?>(null) }
    var qrCodeBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var publicKeySizeOfOtherDevice by remember { mutableStateOf<Int?>(null) }
    var privateKeySize by remember { mutableStateOf<Int?>(null) }
    var encryptedTextSize by remember { mutableStateOf<Int?>(null) }
    var publicKeyBase64Size by remember { mutableStateOf<Int?>(null) }

    val publicKeyBase64 = getOtherDevicePublicKey(context)
    val publicKey = base64ToPublicKey(publicKeyBase64.toString())

    LaunchedEffect(Unit) {
        publicKeySizeOfOtherDevice = KeyPairUtils.getPublicKeySizeOfOtherDevice(context)
        privateKeySize = KeyPairUtils.getPrivateKeySize(context)
        publicKeyBase64Size = publicKeyBase64?.toByteArray()?.size
    }


    fun encryptAndGenerateQR() {
        if (publicKey != null) {
            val encrypted = encryptDataWithPublicKey(context = context, publicKey = publicKey, data = textToEncrypt.text)
            encryptedText = encrypted
            encryptedTextSize = encrypted.toByteArray().size
            qrCodeBitmap = QRCodeGenerator.generateQRCodeBitmap(encrypted)
        } else {
            Toast.makeText(context, "Public Key not found", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Encrypt and Generate QR") },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Public Key Base64 Size: ${publicKeyBase64Size?.toString()} bytes",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Public Key Size of other device: ${publicKeySizeOfOtherDevice?.toString()} bytes",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Private Key Size: ${privateKeySize?.toString()} bytes",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Enter Text to Encrypt",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = textToEncrypt,
                onValueChange = { textToEncrypt = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                label = { Text("Enter Text") }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { encryptAndGenerateQR() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Encrypt")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Encrypted Text Size: ${encryptedTextSize?.toString()} bytes",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Encrypted Text: $encryptedText",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(16.dp))
            qrCodeBitmap?.let {
                Image(bitmap = it.asImageBitmap(), contentDescription = "Encrypted QR Code")
            }
        }
    }
}
