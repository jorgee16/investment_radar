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
    @Test fun alpacaErrorCodesDifferentiatePermanentAndTransientFailures() {
        assertEquals("permission-denied", alpacaErrorAction(402))
        assertEquals("permission-denied", alpacaErrorAction(409))
        assertEquals("limit-exceeded", alpacaErrorAction(405))
        assertEquals("connection-limit", alpacaErrorAction(406))
        assertEquals("stream-overflow", alpacaErrorAction(407))
        assertEquals("server-error", alpacaErrorAction(500))
        assertEquals("protocol-error", alpacaErrorAction(400))
    }

    @Test fun subscriptionAcknowledgementMustMatchRequestedChannels() {
        val expected=IexSubscriptions(setOf("JPM","GS"),setOf("TSM"))
        val actual=Json.parseToJsonElement("""
            {"T":"subscription","quotes":["GS","JPM"],"trades":["TSM"],"bars":[]}
        """.trimIndent()).jsonObject
        assertTrue(subscriptionMatches(actual,expected))
        val partial=Json.parseToJsonElement("""
            {"T":"subscription","quotes":["JPM"],"trades":["TSM"]}
        """.trimIndent()).jsonObject
        assertFalse(subscriptionMatches(partial,expected))
        assertFalse(subscriptionMatches(Json.parseToJsonElement("{}").jsonObject,expected))
    }

}
