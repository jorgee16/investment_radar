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
    @Test fun fullCompanyNamesAndIsinsRemainSearchableWithListingMetadata() {
        val microsoft = directoryAsset("MSFT")!!
        assertTrue(microsoft.matches("Microsoft Corporation"))
        assertTrue(microsoft.copy(country = "United States").matches("MICROSOFT corporation"))
        assertFalse(microsoft.matches("Microsoft Corporation", "Finanças"))
        val fund = directoryAsset("VWCE.DE")!!
        assertTrue(fund.matches("IE00BK5BQT80"))
        assertTrue(fund.copy(country = "Germany", region = "Mundial").matches("IE00BK5BQT80"))
    }
    @Test fun humanIdentitySurvivesProviderTickerOnlyResponses() {
        assertEquals("Airbus SE", assetDisplayName("AIR.PA", "AIR.PA", directoryAsset("AIR.PA")))
        assertEquals("A new company", assetDisplayName("NEW", "A new company", null))
        assertEquals("NEW", assetDisplayName("NEW", "", null))
        assertTrue(directoryAsset("VUAA.DE")!!.matches("vanguard sp500"))
        assertEquals("LU1681038243", directoryAsset("6AQQ.DE")!!.isin)
    }
    @Test fun catalogueHasUniqueIdentifiersAndEtfsAreNotCorporateSectors() {
        assertEquals(assetDirectory.size, assetDirectory.map { it.symbol }.distinct().size)
        assertEquals("ETF diversificados", directoryAsset("VWCE.DE")!!.sector)
        assertFalse(directoryAsset("VWCE.DE")!!.matches("", "Tecnologia"))
        assertTrue(directoryAsset("VWCE.DE")!!.matches("vanguard acumulacao"))
    }
}
