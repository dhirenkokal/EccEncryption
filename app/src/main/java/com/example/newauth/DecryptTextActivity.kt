@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)

package com.example.newauth

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.CaptureManager
import com.journeyapps.barcodescanner.CompoundBarcodeView
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import androidx.compose.ui.viewinterop.AndroidView
import com.example.newauth.objects.KeyPairUtils.decryptDataWithPrivateKey

class DecryptTextActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QRCodeScannerForDecryptionScreen()
        }
    }
}

@Composable
fun QRCodeScannerForDecryptionScreen() {
    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        cameraPermissionState.launchPermissionRequest()
    }

    if (cameraPermissionState.status.isGranted) {
        ScanningScreenForDecryption()
    } else {
        PermissionDeniedContent(context = LocalContext.current)
    }
}

fun PermissionDeniedContent(context: Context) {
    Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
}

@Composable
fun ScanningScreenForDecryption() {
    var scanFlag by remember { mutableStateOf(false) }
    var lastReadBarcode by remember { mutableStateOf<String?>(null) }

    AndroidView(
        factory = { context ->
            val preview = CompoundBarcodeView(context)
            val capture = CaptureManager(context as DecryptTextActivity, preview)
            capture.initializeFromIntent(context.intent, null)
            capture.decode()
            preview.resume()

            preview.decodeContinuous { result ->
                if (scanFlag) return@decodeContinuous
                scanFlag = true
                lastReadBarcode = result.text
            }
            preview
        },
        modifier = Modifier.fillMaxSize()
    )

    lastReadBarcode?.let { encryptedData ->
        DecryptedTextScreen(encryptedData)
    }
}

@Composable
fun DecryptedTextScreen(encryptedData: String) {
    val context = LocalContext.current
    val decryptedText = decryptDataWithPrivateKey(encryptedData, context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Decrypted Text") }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
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
                text = "Encrypted Data: $encryptedData",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(8.dp)
            )
            Text(
                text = "Decrypted Text: $decryptedText",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

