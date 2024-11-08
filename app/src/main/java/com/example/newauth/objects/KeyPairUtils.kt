package com.example.newauth.objects

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import android.util.Log
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

    @SuppressLint("InlinedApi")
    fun generateECCKeyPair(context: Context): KeyPair? {
        return try {
            val sharedPrefs = context.getSharedPreferences("key_storage", Context.MODE_PRIVATE)
            val privateKeyBase64 = sharedPrefs.getString("private_key", null)
            val publicKeyBase64 = sharedPrefs.getString("public_key", null)

            if (privateKeyBase64 != null && publicKeyBase64 != null) {
                Log.d("KeyStore", "Key pair already exists in SharedPreferences")
                val privateKeyBytes = Base64.decode(privateKeyBase64, Base64.DEFAULT)
                val publicKeyBytes = Base64.decode(publicKeyBase64, Base64.DEFAULT)
                val keyFactory = KeyFactory.getInstance("EC")
                val privateKey = keyFactory.generatePrivate(ECPrivateKeySpec(privateKeyBytes as BigInteger?, null))
                val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
                KeyPair(publicKey, privateKey)
            } else {
                Log.d("KeyStore", "Key pair not found, generating new key pair")
                val keyPairGenerator = java.security.KeyPairGenerator.getInstance("EC")
                val ecSpec = java.security.spec.ECGenParameterSpec("secp256r1")
                keyPairGenerator.initialize(ecSpec)
                val keyPair = keyPairGenerator.generateKeyPair()
                sharedPrefs.edit()
                    .putString("private_key", Base64.encodeToString(keyPair.private.encoded, Base64.DEFAULT))
                    .putString("public_key", Base64.encodeToString(keyPair.public.encoded, Base64.DEFAULT))
                    .apply()
                keyPair
            }
        } catch (e: Exception) {
            Log.e("KeyStore", "Error generating ECC key pair", e)
            null
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
