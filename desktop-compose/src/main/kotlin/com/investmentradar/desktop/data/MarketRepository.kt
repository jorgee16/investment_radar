package com.investmentradar.desktop.data

import com.investmentradar.desktop.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

interface MarketRepository {
    suspend fun status():StatusDto
    suspend fun quote(symbol:String):QuoteDto
    suspend fun snapshot(symbol:String):SnapshotDto
    suspend fun earnings(symbol:String?=null):List<EarningsDto>
    suspend fun news(symbol:String):List<NewsDto>
    suspend fun providers():List<ProviderDto>
    suspend fun providerSettings():ProviderSettingsDto
    suspend fun saveProviderSettings(
        demo:Boolean,
        eodhdKey:String?,
        fmpKey:String?,
        twelveKey:String?,
        routing:Map<String,String>
    ):ProviderSettingsDto
    suspend fun testProvider(provider:String,symbol:String):List<ProviderTestDto>
}

class NativeMarketRepository(
    private val bridge:NativeBridge,
    private val json:Json = Json { ignoreUnknownKeys=true }
):MarketRepository {
    override suspend fun status():StatusDto = decode("/api/status")
    override suspend fun quote(symbol:String):QuoteDto = decode("/api/quote?symbol=${enc(symbol)}")
    override suspend fun snapshot(symbol:String):SnapshotDto = decode("/api/snapshot?symbol=${enc(symbol)}")
    override suspend fun earnings(symbol:String?):List<EarningsDto> =
        decode(if(symbol.isNullOrBlank()) "/api/earnings" else "/api/earnings?symbol=${enc(symbol)}")
    override suspend fun news(symbol:String):List<NewsDto> = decode("/api/news?symbol=${enc(symbol)}")
    override suspend fun providers():List<ProviderDto> = decode("/api/providers")
    override suspend fun providerSettings():ProviderSettingsDto = decode("/api/settings")

    override suspend fun saveProviderSettings(
        demo:Boolean,
        eodhdKey:String?,
        fmpKey:String?,
        twelveKey:String?,
        routing:Map<String,String>
    ):ProviderSettingsDto = withContext(Dispatchers.IO) {
        val payload=buildJsonObject {
            put("demo",demo)
            eodhdKey?.let{put("eodhdKey",it)}
            fmpKey?.let{put("fmpKey",it)}
            twelveKey?.let{put("twelveKey",it)}
            putJsonObject("routing"){
                listOf("quotes","fundamentals","estimates","earnings","news","history").forEach { key ->
                    put(key,routing[key]?:"auto")
                }
            }
        }
        json.decodeFromString<ProviderSettingsDto>(bridge.post("/api/settings",payload.toString()))
    }

    override suspend fun testProvider(provider:String,symbol:String):List<ProviderTestDto> =
        withContext(Dispatchers.IO) {
            val payload=buildJsonObject {
                put("provider",provider)
                put("symbol",symbol)
            }
            json.decodeFromString<List<ProviderTestDto>>(bridge.post("/api/providers/test",payload.toString()))
        }

    private suspend inline fun <reified T> decode(path:String):T = withContext(Dispatchers.IO) {
        json.decodeFromString<T>(bridge.get(path))
    }

    private fun enc(value:String)=URLEncoder.encode(value,StandardCharsets.UTF_8)
}
