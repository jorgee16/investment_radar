package com.investmentradar.desktop

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AlpacaIexStreamContractTest {
    @Test fun onlyArmedAuthorizedIexSymbolsAreSubscribed() {
        val data = buildJsonObject {
            put("instruments", buildJsonArray {
                add(buildJsonObject { put("id","JPM");put("source","alpaca-iex");put("currency","USD") })
                add(buildJsonObject { put("id","MSFT");put("source","alpaca-iex");put("currency","USD") })
                add(buildJsonObject { put("id","VUAA.DE");put("source","unverified");put("currency","EUR") })
            })
            put("rules", buildJsonArray {
                add(buildJsonObject {put("instrument","JPM");put("armed",true);put("basis","ASK")})
                add(buildJsonObject {put("instrument","JPM");put("armed",true);put("basis","LAST")})
                add(buildJsonObject {put("instrument","MSFT");put("armed",false);put("basis","ASK")})
                add(buildJsonObject {put("instrument","VUAA.DE");put("armed",true);put("basis","ASK")})
            })
        }
        val result = subscriptionsFor(data)
        assertEquals(setOf("JPM"),result.quotes)
        assertEquals(setOf("JPM"),result.trades)
        assertEquals(1,result.size)
    }

    @Test fun JSONCommandUsesOfficialAlpacaChannelKeys() {
        val value = Json.parseToJsonElement(command("subscribe",setOf("JPM","GS"),setOf("TSM"))).jsonObject
        assertEquals("subscribe",value["action"]?.jsonPrimitive?.content)
        assertEquals(listOf("GS","JPM"),value["quotes"]?.jsonArray?.map{it.jsonPrimitive.content})
        assertEquals(listOf("TSM"),value["trades"]?.jsonArray?.map{it.jsonPrimitive.content})
        assertNull(value["bars"])
    }
}
