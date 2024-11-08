@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.newauth

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.example.newauth.objects.KeyPairUtils
import com.example.newauth.objects.QRCodeGenerator

class CreateQrActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                CreateQrScreen(context = this)
            }
        }
    }

    @Composable
    fun CreateQrScreen(context: Context) {
        val qrBitmap = remember { mutableStateOf<Bitmap?>(null) }

        KeyPairUtils.generateECCKeyPair(context)
        val publicKeyBase64 = KeyPairUtils.getPublicKey(context)

        if (publicKeyBase64 != null) {
            qrBitmap.value = QRCodeGenerator.generateQRCodeBitmap(publicKeyBase64)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Create QR - Public Key") }
                )
            },
            content = { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    qrBitmap.value?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.size(400.dp)
                        )
                    }
                }
            }
        )
    }
}
