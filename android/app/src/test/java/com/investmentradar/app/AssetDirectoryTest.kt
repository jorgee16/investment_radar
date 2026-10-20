package com.investmentradar.app

import org.junit.Assert.*
import org.junit.Test

class AssetDirectoryTest {
    @Test fun namesSymbolsAccentsAndSectorAreSearchable() {
        val airbus = directoryAsset("AIR.PA")!!
        assertTrue(airbus.matches(" airbus "))
        assertTrue(airbus.matches("air.pa"))
        assertTrue(airbus.matches("industria AIR"))
        assertFalse(airbus.matches("Airbus", "Tecnologia"))
        assertEquals("USD", directoryAsset("MSFT")!!.currency)
        assertNull(directoryAsset("UNKNOWN"))
    }
    @Test fun catalogueHasUniqueIdentifiersAndEtfsAreNotCorporateSectors() {
        assertEquals(assetDirectory.size, assetDirectory.map { it.symbol }.distinct().size)
        assertEquals("ETF diversificados", directoryAsset("VWCE.DE")!!.sector)
        assertFalse(directoryAsset("VWCE.DE")!!.matches("", "Tecnologia"))
        assertTrue(directoryAsset("VWCE.DE")!!.matches("vanguard acumulacao"))
    }
}
