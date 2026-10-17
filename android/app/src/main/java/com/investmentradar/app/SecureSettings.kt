package com.investmentradar.app
import android.content.Context
import android.security.keystore.*
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec

class SecureSettings(private val ctx: Context) {
 private val prefs=ctx.getSharedPreferences("secure_providers",Context.MODE_PRIVATE)
 private val alias="investment-radar-providers-v1"
 private fun key(): SecretKey {
  val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  (store.getKey(alias,null) as? SecretKey)?.let { return it }
  val gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
  gen.init(KeyGenParameterSpec.Builder(alias,
   KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
   .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
   .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
  return gen.generateKey()
 }
 @Synchronized fun load(): JSONObject {
  val encoded=prefs.getString("config",null)
  if(encoded!=null) {
   val bytes=Base64.decode(encoded,Base64.NO_WRAP)
   val cipher=Cipher.getInstance("AES/GCM/NoPadding")
   cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
   return JSONObject(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8))
  }
  val old=ctx.getSharedPreferences("providers",Context.MODE_PRIVATE)
  val migrated=JSONObject().put("demo",true).put("routing",JSONObject())
  mapOf("eod" to "eodhdKey","fmp" to "fmpKey","twelve" to "twelveKey")
   .forEach { (from,to)->migrated.put(to,old.getString(from,"") ?: "") }
  if(old.all.isNotEmpty()) {
   save(migrated)
   check(old.edit().clear().commit())
  }
  return migrated
 }
 @Synchronized fun save(config: JSONObject) {
  val cipher=Cipher.getInstance("AES/GCM/NoPadding")
  cipher.init(Cipher.ENCRYPT_MODE,key())
  val bytes=cipher.iv+cipher.doFinal(config.toString().toByteArray(Charsets.UTF_8))
  check(prefs.edit().putString("config",Base64.encodeToString(bytes,Base64.NO_WRAP)).commit())
 }
}
