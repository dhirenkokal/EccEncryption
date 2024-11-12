@file:OptIn(ExperimentalMaterial3Api::class)
@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")

package com.example.newauth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.auth.api.identity.SignInCredential
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

class MainActivity : ComponentActivity() {

    private lateinit var driveService: Drive
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    companion object {
        private const val RC_SIGN_IN = 9001 // Choose any unique value for the request code
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        firebaseAuth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(getString(R.string.default_web_client_id))
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

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
                    },
                    onGoogleSignInClick = { startGoogleSignIn() },
                    onGoogleSignOutClick = { signOut() }
                )
            }
        }
        checkExistingSignIn()
    }

    private fun signOut() {
        googleSignInClient.signOut().addOnCompleteListener {
            firebaseAuth.signOut()
            Toast.makeText(this, "Signed Out", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkExistingSignIn() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            firebaseAuthWithGoogle(account.idToken!!)
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Firebase authentication successful!")
                    Toast.makeText(this, "Sign-in successful", Toast.LENGTH_SHORT).show()

                    // Initialize Google Drive service
                    val googleSignInAccount = GoogleSignIn.getLastSignedInAccount(this)
                    googleSignInAccount?.let {
                        initializeDriveService(it)
                    }
                } else {
                    Log.e(TAG, "Firebase authentication failed: ${task.exception?.message}")
                    Toast.makeText(this, "Sign-in failed", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun startGoogleSignIn() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            if (data != null) {
                val result = GoogleSignIn.getSignedInAccountFromIntent(data)

                if (result != null) {
                    try {
                        val account = result.getResult(ApiException::class.java)
                        if (account != null) {
                            // Proceed with Firebase authentication or any further logic
                            firebaseAuthWithGoogle(account.idToken!!)
                        } else {
                            Log.e(TAG, "Google sign-in failed: Account is null")
                            Toast.makeText(this, "Sign-in failed", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: ApiException) {
                        Log.e(TAG, "Google sign-in failed with exception: ${e.statusCode}")
                        Toast.makeText(this, "Sign-in failed", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e(TAG, "Google sign-in result is null")
                    Toast.makeText(this, "Sign-in failed", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e(TAG, "Google sign-in data is null")
                Toast.makeText(this, "Sign-in failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initializeDriveService(googleSignInAccount: GoogleSignInAccount) {
        val credential = GoogleAccountCredential.usingOAuth2(
            this, listOf(DriveScopes.DRIVE)
        ).apply {
            selectedAccount = googleSignInAccount.account
        }

        driveService = Drive.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("NewAuth")
            .build()

        KeyPairUtils.fetchECCKeysFromDrive(
            driveService,
            context = this
        ) { keypair ->
            if (keypair != null) {
                Log.d("MainActivity", "Keypair fetched from Drive: $keypair")
            } else {
                Log.d("MainActivity", "Keypair not found")
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
    onClearKeysClick: () -> Unit,
    onGoogleSignInClick: () -> Unit,
    onGoogleSignOutClick: () -> Unit
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
            Spacer(modifier = Modifier.height(8.dp))

            Button(onClick = onGoogleSignInClick) {
                Text("Sign In with Google")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onGoogleSignOutClick) {
                Text("Sign out")
            }
        }
    }
}
