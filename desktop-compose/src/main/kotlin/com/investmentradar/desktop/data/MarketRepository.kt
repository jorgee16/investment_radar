package com.investmentradar.desktop.data

import com.investmentradar.desktop.model.EarningsDto
import com.investmentradar.desktop.model.QuoteDto
import com.investmentradar.desktop.model.SnapshotDto
import com.investmentradar.desktop.model.StatusDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

interface MarketRepository {
    suspend fun status():StatusDto
    suspend fun quote(symbol:String):QuoteDto
    suspend fun snapshot(symbol:String):SnapshotDto
    suspend fun earnings():List<EarningsDto>
}

class NativeMarketRepository(
    private val bridge:NativeBridge,
    private val json:Json = Json { ignoreUnknownKeys=true }
):MarketRepository {
    override suspend fun status():StatusDto = decode("/api/status")
    override suspend fun quote(symbol:String):QuoteDto = decode("/api/quote?symbol=${enc(symbol)}")
    override suspend fun snapshot(symbol:String):SnapshotDto = decode("/api/snapshot?symbol=${enc(symbol)}")
    override suspend fun earnings():List<EarningsDto> = decode("/api/earnings")

    private suspend inline fun <reified T> decode(path:String):T = withContext(Dispatchers.IO) {
        json.decodeFromString<T>(bridge.get(path))
    }

    private fun enc(value:String)=URLEncoder.encode(value,StandardCharsets.UTF_8)
}
