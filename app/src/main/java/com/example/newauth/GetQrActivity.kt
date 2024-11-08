@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class,
    ExperimentalPermissionsApi::class
)

package com.example.newauth

import android.Manifest
import android.os.Bundle
import android.util.Base64
import android.util.Log
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
import androidx.compose.ui.viewinterop.AndroidView
import com.example.newauth.objects.KeyPairUtils
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import kotlinx.coroutines.delay
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

class GetQrActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QRCodeScannerScreen()
        }
    }
}

@Composable
fun QRCodeScannerScreen() {
    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        cameraPermissionState.launchPermissionRequest()
    }

    if (cameraPermissionState.status.isGranted) {
        ScanningScreen()
    } else {
        PermissionDeniedContent()
    }
}

@Composable
fun PermissionDeniedContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Need Camera Permission", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ScanningScreen() {
    var scanFlag by remember { mutableStateOf(false) }
    var lastReadBarcode by remember { mutableStateOf<String?>(null) }

    AndroidView(
        factory = { context ->
            val preview = CompoundBarcodeView(context)
            val capture = CaptureManager(context as GetQrActivity, preview)
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

    lastReadBarcode?.let { encodedResult ->
        ResultScreen(encodedResult) {
            scanFlag = false
        }
    }
}

@Composable
fun ResultScreen(encodedResult: String, onClose: () -> Unit) {
    val decodedResult = Base64.decode(encodedResult, Base64.DEFAULT)

    val keyFactory = KeyFactory.getInstance("EC")
    val publicKeySpec = X509EncodedKeySpec(decodedResult)
    val publicKey = keyFactory.generatePublic(publicKeySpec)

    val context = LocalContext.current
    KeyPairUtils.storeOtherDevicePublicKey(context, encodedResult)

    val storedPublicKey = KeyPairUtils.getOtherDevicePublicKey(context)
    Log.d("QRCodeActivity", "Stored Public Key: $storedPublicKey")

    val privateKey = KeyPairUtils.getPrivateKey(context)
    if (privateKey != null) {
        val encryptedData = encryptData(privateKey, "SomeDataToEncrypt")
        Log.d("QRCodeActivity", "Encrypted Data: $encryptedData")
    }

    LaunchedEffect(Unit) {
        delay(2000)
        onClose()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Result") }
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
                text = "Encoded Base64: $encodedResult",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(8.dp)
            )
            Text(
                text = "Decoded Public Key: ${publicKey.encoded}",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

private fun encryptData(privateKey: PrivateKey, data: String): String {
    try {
        val cipher = Cipher.getInstance("ECIES")
        cipher.init(Cipher.ENCRYPT_MODE, privateKey)
        val encryptedBytes = cipher.doFinal(data.toByteArray())
        return Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
    } catch (e: Exception) {
        Log.e("QRCodeActivity", "Error encrypting data", e)
        return ""
    }
}
