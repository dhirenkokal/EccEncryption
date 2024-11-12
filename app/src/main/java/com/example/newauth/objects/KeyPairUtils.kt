@file:Suppress("DEPRECATION")

package com.example.newauth.objects

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.FileList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.ECPrivateKeySpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object KeyPairUtils {

    fun fetchECCKeysFromDrive(driveService: Drive, context: Context, callback: (KeyPair?) -> Unit) {
        val sharedPrefs = context.getSharedPreferences("key_storage", Context.MODE_PRIVATE)

        // Check if keys already exist in SharedPreferences
        val privateKeyBase64 = sharedPrefs.getString("private_key", null)
        val publicKeyBase64 = sharedPrefs.getString("public_key", null)

        if (privateKeyBase64 != null && publicKeyBase64 != null) {
            Log.d("KeyPairUtils", "Keys already exist in SharedPreferences.")
            callback(decodeKeyPair(privateKeyBase64, publicKeyBase64))
            return
        }

        // Launch a coroutine to perform the Drive operation asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Search for the file in Drive containing ECC keys
                val query = "name = 'ECC_Key_Pair' and mimeType = 'text/plain'"
                val result: FileList = driveService.files().list().setQ(query).setSpaces("drive").execute()
                val driveFile: com.google.api.services.drive.model.File? = result.files.firstOrNull()

                if (driveFile != null) {
                    // Download file content
                    val inputStream = driveService.files().get(driveFile.id).executeMediaAsInputStream()
                    val fileContent = inputStream.bufferedReader().use { it.readText() }

                    // Extract private and public keys (assuming they are stored as base64 strings, separated by a delimiter)
                    val (storedPrivateKey, storedPublicKey) = fileContent.split(":")

                    // Store keys in SharedPreferences
                    sharedPrefs.edit()
                        .putString("private_key", storedPrivateKey)
                        .putString("public_key", storedPublicKey)
                        .apply()

                    // Decode and return the KeyPair
                    callback(decodeKeyPair(storedPrivateKey, storedPublicKey))
                } else {
                    // Generate new keys if file is not found
                    Log.d("KeyPairUtils", "Key file not found in Drive. Generating new keys.")
                    val keyPair = generateNewECCKeyPair(context)
                    uploadECCKeysToDrive(driveService, keyPair)
                    callback(keyPair)
                }
            } catch (e: UserRecoverableAuthIOException) {
                Log.e("KeyPairUtils", "Authorization required: ${e.message}")
                // Start the intent to request authorization
                val intent = e.intent
                context.startActivity(intent)
                callback(null)
            } catch (e: Exception) {
                Log.e("KeyPairUtils", "Error fetching keys from Drive", e)
                callback(null)
            }
        }
    }
    private fun decodeKeyPair(privateKeyBase64: String, publicKeyBase64: String): KeyPair {
        val privateKeyBytes = Base64.decode(privateKeyBase64, Base64.DEFAULT)
        val publicKeyBytes = Base64.decode(publicKeyBase64, Base64.DEFAULT)
        val keyFactory = KeyFactory.getInstance("EC")
        val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
        val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
        return KeyPair(publicKey, privateKey)
    }

    private fun generateNewECCKeyPair(context: Context): KeyPair {
        val keyPairGenerator = java.security.KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
        val keyPair = keyPairGenerator.generateKeyPair()

        // Store the new keys in SharedPreferences
        val sharedPrefs = context.getSharedPreferences("key_storage", Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putString("private_key", Base64.encodeToString(keyPair.private.encoded, Base64.DEFAULT))
            .putString("public_key", Base64.encodeToString(keyPair.public.encoded, Base64.DEFAULT))
            .apply()

        return keyPair
    }

    private fun uploadECCKeysToDrive(driveService: Drive, keyPair: KeyPair) {
        try {
            val privateKeyBase64 = Base64.encodeToString(keyPair.private.encoded, Base64.DEFAULT)
            val publicKeyBase64 = Base64.encodeToString(keyPair.public.encoded, Base64.DEFAULT)
            val fileContent = "$privateKeyBase64:$publicKeyBase64"

            val fileMetadata = com.google.api.services.drive.model.File()
            fileMetadata.name = "ECC_Key_Pair"
            fileMetadata.mimeType = "text/plain"

            val fileContentStream = fileContent.byteInputStream()
            val mediaContent = com.google.api.client.http.InputStreamContent("text/plain", fileContentStream)
            driveService.files().create(fileMetadata, mediaContent).execute()

            Log.d("KeyPairUtils", "Keys successfully uploaded to Drive.")
        } catch (e: Exception) {
            Log.e("KeyPairUtils", "Error uploading keys to Drive", e)
        }
    }


    fun getPublicKey(context: Context): String? {
        return try {
            val sharedPrefs = context.getSharedPreferences("key_storage", Context.MODE_PRIVATE)
            sharedPrefs.getString("public_key", null)
        } catch (e: Exception) {
            Log.e("KeyStore", "Error retrieving public key", e)
            null
        }
    }

    fun getPrivateKey(context: Context): PrivateKey? {
        return try {
            val sharedPrefs = context.getSharedPreferences("key_storage", Context.MODE_PRIVATE)
            val privateKeyBase64 = sharedPrefs.getString("private_key", null)
            if (privateKeyBase64 == null) throw IllegalStateException("Private key is not available.")
            val privateKeyBytes = Base64.decode(privateKeyBase64, Base64.DEFAULT)
            val keyFactory = KeyFactory.getInstance("EC")
            keyFactory.generatePrivate(ECPrivateKeySpec(privateKeyBytes as BigInteger?, null))
        } catch (e: Exception) {
            Log.e("KeyStore", "Error retrieving private key", e)
            null
        }
    }

    fun storeOtherDevicePublicKey(context: Context, publicKeyBase64: String) {
        val sharedPrefs = context.getSharedPreferences("key_storage", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("other_device_public_key", publicKeyBase64).apply()
    }

    fun getOtherDevicePublicKey(context: Context): String? {
        val sharedPrefs = context.getSharedPreferences("key_storage", Context.MODE_PRIVATE)
        return sharedPrefs.getString("other_device_public_key", null)
    }

    fun clearAllKeys(context: Context) {
        val sharedPrefs = context.getSharedPreferences("key_storage", Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().apply()
        Log.d("KeyStore", "All keys cleared from SharedPreferences")
    }

    fun getPublicKeySizeOfOtherDevice(context: Context): Int? {
        return try {
            val sharedPrefs = context.getSharedPreferences("key_storage", Context.MODE_PRIVATE)
            val publicKeyBase64 = sharedPrefs.getString("other_device_public_key", null) ?: return null
            val publicKeyBytes = Base64.decode(publicKeyBase64, Base64.DEFAULT)
            publicKeyBytes.size
        } catch (e: Exception) {
            Log.e("KeyStore", "Error retrieving public key size", e)
            null
        }
    }

    fun getPrivateKeySize(context: Context): Int? {
        return try {
            val sharedPrefs = context.getSharedPreferences("key_storage", Context.MODE_PRIVATE)
            val privateKeyBase64 = sharedPrefs.getString("private_key", null) ?: return null
            val privateKeyBytes = Base64.decode(privateKeyBase64, Base64.DEFAULT)
            privateKeyBytes.size
        } catch (e: Exception) {
            Log.e("KeyStore", "Error retrieving private key size", e)
            null
        }
    }

    fun base64ToPublicKey(base64String: String): PublicKey? {
        return try {
            val keyBytes = Base64.decode(base64String, Base64.DEFAULT)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("EC")
            keyFactory.generatePublic(keySpec)
        } catch (e: Exception) {
            Log.e("KeyConversion", "Error converting Base64 to PublicKey", e)
            null
        }
    }

    fun encryptDataWithPublicKey(context: Context, publicKey: PublicKey, data: String): String {
        try {
            val privateKeyBase64 = context.getSharedPreferences("key_storage", Context.MODE_PRIVATE)
                .getString("private_key", null) ?: throw IllegalStateException("Private key not found in SharedPreferences")

            val privateKeyBytes = Base64.decode(privateKeyBase64, Base64.DEFAULT)
            val keyFactory = KeyFactory.getInstance("EC")
            val privateKey: PrivateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))

            val keyAgreement = KeyAgreement.getInstance("ECDH")
            keyAgreement.init(privateKey)
            keyAgreement.doPhase(publicKey, true)

            val sharedSecret = keyAgreement.generateSecret()
            val aesKey = SecretKeySpec(sharedSecret.copyOf(16), "AES")

            val iv = ByteArray(16)
            java.security.SecureRandom().nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, ivSpec)

            val encryptedData = cipher.doFinal(data.toByteArray())

            val combinedData = iv + encryptedData

            return Base64.encodeToString(combinedData, Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            return ""
        }
    }


    fun decryptDataWithPrivateKey(encryptedData: String, context: Context): String {
        try {
            val privateKeyBase64 = context.getSharedPreferences("key_storage", Context.MODE_PRIVATE)
                .getString("private_key", null) ?: throw IllegalStateException("Private key not found in SharedPreferences")

            val publicKeyBase64 = KeyPairUtils.getOtherDevicePublicKey(context)
            val publicKey = base64ToPublicKey(publicKeyBase64.toString())

            if (publicKey != null) {
                val privateKeyBytes = Base64.decode(privateKeyBase64, Base64.DEFAULT)
                val keyFactory = KeyFactory.getInstance("EC")
                val privateKey: PrivateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))

                val keyAgreement = KeyAgreement.getInstance("ECDH")
                keyAgreement.init(privateKey)
                keyAgreement.doPhase(publicKey, true)

                val sharedSecret = keyAgreement.generateSecret()
                val aesKey = SecretKeySpec(sharedSecret.copyOf(16), "AES")

                val decodedData = Base64.decode(encryptedData, Base64.DEFAULT)
                val ivBytes = decodedData.copyOfRange(0, 16)
                val encryptedBytes = decodedData.copyOfRange(16, decodedData.size)
                val ivSpec = IvParameterSpec(ivBytes)

                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, aesKey, ivSpec)

                val decryptedBytes = cipher.doFinal(encryptedBytes)
                return String(decryptedBytes)
            } else {
                Log.e("DecryptTextActivity", "Public key not found")
                return "Decryption failed"
            }
        } catch (e: Exception) {
            Log.e("DecryptTextActivity", "Error decrypting data", e)
            return "Decryption failed"
        }
    }



    fun generateRSAKeyPair(context: Context, keySize: Int = 2048): KeyPair? {
        return try {
            val sharedPrefs = context.getSharedPreferences("key_storage", Context.MODE_PRIVATE)
            val privateKeyBase64 = sharedPrefs.getString("private_key_rsa", null)
            val publicKeyBase64 = sharedPrefs.getString("public_key_rsa", null)

            if (privateKeyBase64 != null && publicKeyBase64 != null) {
                Log.d("KeyStore", "RSA Key pair already exists in SharedPreferences")
                val privateKeyBytes = Base64.decode(privateKeyBase64, Base64.DEFAULT)
                val publicKeyBytes = Base64.decode(publicKeyBase64, Base64.DEFAULT)
                val keyFactory = KeyFactory.getInstance("RSA")
                val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
                val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
                KeyPair(publicKey, privateKey)
            } else {
                Log.d("KeyStore", "RSA Key pair not found, generating new key pair")
                val keyPairGenerator = java.security.KeyPairGenerator.getInstance("RSA")
                keyPairGenerator.initialize(keySize)
                val keyPair = keyPairGenerator.generateKeyPair()

                // Store RSA keys in SharedPreferences
                sharedPrefs.edit()
                    .putString("private_key_rsa", Base64.encodeToString(keyPair.private.encoded, Base64.DEFAULT))
                    .putString("public_key_rsa", Base64.encodeToString(keyPair.public.encoded, Base64.DEFAULT))
                    .apply()
                keyPair
            }
        } catch (e: Exception) {
            Log.e("KeyStore", "Error generating RSA key pair", e)
            null
        }
    }

    fun getRSAKeySizeInBytes(context: Context): Int? {
        return try {
            val sharedPrefs = context.getSharedPreferences("key_storage", Context.MODE_PRIVATE)
            val publicKeyBase64 = sharedPrefs.getString("public_key_rsa", null)
            val publicKeyBytes = Base64.decode(publicKeyBase64, Base64.DEFAULT)

            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))

            val rsaKeySpec = publicKey as java.security.interfaces.RSAPublicKey
            val modulusBitLength = rsaKeySpec.modulus.bitLength()

            (modulusBitLength + 7) / 8
        } catch (e: Exception) {
            Log.e("KeyStore", "Error retrieving RSA key size in bytes", e)
            null
        }
    }

    fun getRSAPrivateKeySizeInBytes(context: Context): Int? {
        return try {
            val sharedPrefs = context.getSharedPreferences("key_storage", Context.MODE_PRIVATE)
            val privateKeyBase64 = sharedPrefs.getString("private_key_rsa", null)
            val privateKeyBytes = Base64.decode(privateKeyBase64, Base64.DEFAULT)

            val keyFactory = KeyFactory.getInstance("RSA")
            val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))

            privateKey.encoded.size
        } catch (e: Exception) {
            Log.e("KeyStore", "Error retrieving RSA private key size in bytes", e)
            null
        }
    }


}
