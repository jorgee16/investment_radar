package com.investmentradar.desktop

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader
import java.io.StringWriter

/** No TLS server, credentials, email address or network connection is used. */
class MonitorSmtpProtocolTest {
    private fun alert():JsonObject = buildJsonObject {
        put("id","0123456789abcdef0123456789abcdef:0")
        put("instrument","JPM")
        put("basis","ASK")
        put("currency","USD")
        put("venue","IEX")
        put("feed","alpaca-iex")
        put("priceUnits",150000000L)
        put("observedAt",1793977200L)
        put("email","recipient@example.invalid")
    }

    private fun exchange(replies:String,event:JsonObject=alert()):String {
        val output=StringWriter()
        smtpExchange(event,"sender@example.invalid","local-test-user","local-test-password",
            BufferedReader(StringReader(replies)),output)
        return output.toString()
    }

    @Test fun multilineHelloAndAcceptedMessageCreateOneTransaction() {
        val written=exchange(
            "220 ready\r\n250-Hello\r\n250 AUTH PLAIN\r\n235 accepted\r\n"+
            "250 sender okay\r\n250 recipient okay\r\n354 data\r\n250 queued\r\n")
        assertTrue(written.contains("EHLO investment-radar.local\r\n"))
        assertTrue(written.contains("MAIL FROM:<sender@example.invalid>\r\n"))
        assertTrue(written.contains("RCPT TO:<recipient@example.invalid>\r\n"))
        assertTrue(written.contains("Message-ID: <0123456789abcdef0123456789abcdef-0@investment-radar.local>"))
        assertTrue(written.endsWith("\r\n.\r\n"))
        assertEquals(1, Regex("(?m)^DATA\r?$").findAll(written).count())
    }

    @Test fun serverRejectionCannotBeMisclassifiedAsDelivery() {
        try {
            exchange("220 ready\r\n250 hello\r\n235 accepted\r\n250 sender\r\n550 rejected\r\n")
            fail("Recipient rejection must fail")
        }catch(expected:IllegalArgumentException){ }
        try {
            exchange("220 ready\r\n250-Hello\r\n550 wrong-code\r\n")
            fail("Malformed multiline EHLO must fail")
        }catch(expected:IllegalArgumentException){ }
    }

    @Test fun emailAndMessageIdHeaderInjectionAreRejectedBeforeWriting() {
        for(event in listOf(
            buildJsonObject { alert().forEach{(k,v)->put(k,if(k=="email") JsonPrimitive("recipient@example.invalid\r\nBcc: x") else v)} },
            buildJsonObject { alert().forEach{(k,v)->put(k,if(k=="id") JsonPrimitive("wrong\r\nBcc: x") else v)} }
        )){
            try {
                exchange("220 ready\r\n",event)
                fail("User-controlled SMTP header must fail validation")
            }catch(expected:IllegalArgumentException){ }
        }
    }
}
