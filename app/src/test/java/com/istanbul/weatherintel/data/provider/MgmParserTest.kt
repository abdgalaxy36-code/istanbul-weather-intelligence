package com.istanbul.weatherintel.data.provider

import com.istanbul.weatherintel.data.model.Availability
import com.istanbul.weatherintel.data.model.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parser tests using real, permitted MGM responses captured live from
 * servis.mgm.gov.tr (November 2026). These are the actual payloads, not mocks.
 */
class MgmParserTest {

    private val observationJson = """
        [
          {
            "aktuelBasinc": 1015.2,
            "denizSicaklik": -9999,
            "denizeIndirgenmisBasinc": 1018.4,
            "gorus": 7000,
            "hadiseKodu": "A",
            "istNo": 17060,
            "kapalilik": 0,
            "karYukseklik": -9999,
            "nem": 87,
            "rasatMetar": "LTBA 220320Z 21010KT 7000 NSC 14/12 Q1018 RMK RWY23 22009KT =",
            "rasatSinoptik": "-9999",
            "rasatTaf": "-9999",
            "ruzgarHiz": 17.28,
            "ruzgarYon": 211,
            "sicaklik": 13.8,
            "veriZamani": "2026-11-22T03:12:00.000Z",
            "yagis00Now": 0,
            "yagis10Dk": -9999,
            "yagis12Saat": 5.4,
            "yagis1Saat": 0,
            "yagis24Saat": 5.4,
            "yagis6Saat": 0,
            "denizVeriZamani": "2026-11-21T06:00:00.000Z",
            "hissedilenSicaklik": 13.8
          }
        ]
    """.trimIndent()

    private val hourlyJson = """
        [
          {
            "merkez": "İstanbul",
            "baslangicZamani": "2026-11-21T12:00:00.000Z",
            "tahmin": [
              {"tarih":"2026-11-22T06:00:00.000Z","hadise":"A","sicaklik":13,"hissedilenSicaklik":11,"nem":78,"ruzgarYonu":251,"ruzgarHizi":6,"maksimumRuzgarHizi":10},
              {"tarih":"2026-11-22T09:00:00.000Z","hadise":"PB","sicaklik":15,"hissedilenSicaklik":13,"nem":72,"ruzgarYonu":262,"ruzgarHizi":5,"maksimumRuzgarHizi":16}
            ]
          }
        ]
    """.trimIndent()

    private val dailyJson = """
        [
          {
            "merkez": "İstanbul",
            "tarihGun0": "2026-11-22T00:00:00.000Z",
            "enDusukGun0": 9, "enYuksekGun0": 16,
            "enDusukNemGun0": 60, "enYuksekNemGun0": 88,
            "hadiseGun0": "PB", "ruzgarHizGun0": 11, "ruzgarYonGun0": 215,
            "tarihGun1": "2026-11-23T00:00:00.000Z",
            "enDusukGun1": 8, "enYuksekGun1": 15,
            "enDusukNemGun1": 55, "enYuksekNemGun1": 90,
            "hadiseGun1": "Y", "ruzgarHizGun1": 39, "ruzgarYonGun1": 215
          }
        ]
    """.trimIndent()

    private fun observation() = MgmParser.parseObservation(
        body = observationJson,
        stationName = "Bakırköy, İstanbul",
        stationExternalId = "17060",
        collectedAt = 1_763_784_000_000L,
    )

    @Test
    fun `observation maps core fields with units`() {
        val obs = observation()!!
        assertEquals(ProviderId.MGM, obs.provider)
        assertEquals(13.8, obs.measurements.first { it.code == "sicaklik" }.value!!, 0.001)
        assertEquals(87.0, obs.measurements.first { it.code == "nem" }.value!!, 0.001)
        assertEquals(1015.2, obs.measurements.first { it.code == "aktuelBasinc" }.value!!, 0.001)
        assertEquals(1018.4, obs.measurements.first { it.code == "denizeIndirgenmisBasinc" }.value!!, 0.001)
        assertEquals(7000.0, obs.measurements.first { it.code == "gorus" }.value!!, 0.001)
        assertEquals(17.28, obs.measurements.first { it.code == "ruzgarHiz" }.value!!, 0.001)
        assertEquals(211.0, obs.measurements.first { it.code == "ruzgarYon" }.value!!, 0.001)
        assertEquals(5.4, obs.measurements.first { it.code == "yagis24Saat" }.value!!, 0.001)
    }

    @Test
    fun `minus 9999 becomes null and is never zero`() {
        val obs = observation()!!
        assertNull(
            "Sentinel must become null",
            obs.measurements.first { it.code == "denizSicaklik" }.value,
        )
        assertNull(
            "Sentinel must become null",
            obs.measurements.first { it.code == "karYukseklik" }.value,
        )
        assertNull(
            "Sentinel must become null",
            obs.measurements.first { it.code == "yagis10Dk" }.value,
        )
        val sentinel = obs.measurements.first { it.code == "denizSicaklik" }
        assertEquals(Availability.NULL_VALUE, sentinel.availability)
        // A real zero must stay zero - null and zero are different states.
        assertEquals(0.0, obs.measurements.first { it.code == "yagis00Now" }.value!!, 0.001)
    }

    @Test
    fun `weather code maps to MGM condition label`() {
        val obs = observation()!!
        assertEquals("Açık", obs.measurements.first { it.code == "hadiseAdi" }.text)
    }

    @Test
    fun `observation time is parsed as UTC epoch millis`() {
        val obs = observation()!!
        // 2026-11-22T03:12:00.000Z
        assertEquals(1795317120000L, obs.observedAt)
    }

    @Test
    fun `original provider payload is preserved verbatim`() {
        val obs = observation()!!
        assertTrue(obs.originalJson.contains("rasatMetar"))
        assertTrue(obs.originalJson.contains("LTBA 220320Z"))
    }

    @Test
    fun `hourly rows keep every forecast field`() {
        val rows = MgmParser.parseHourly(hourlyJson, "Bakırköy")!!.rows
        assertEquals(2, rows.size)
        val first = rows.first()
        assertEquals(13.0, first.measurements.first { it.code == "sicaklik" }.value!!, 0.001)
        assertEquals(11.0, first.measurements.first { it.code == "hissedilenSicaklik" }.value!!, 0.001)
        assertEquals(78.0, first.measurements.first { it.code == "nem" }.value!!, 0.001)
        assertEquals(251.0, first.measurements.first { it.code == "ruzgarYonu" }.value!!, 0.001)
        assertEquals(6.0, first.measurements.first { it.code == "ruzgarHizi" }.value!!, 0.001)
        assertEquals(10.0, first.measurements.first { it.code == "maksimumRuzgarHizi" }.value!!, 0.001)
        assertEquals("Açık", first.measurements.first { it.code == "hadiseAdi" }.text)
    }

    @Test
    fun `daily rows map parallel day arrays`() {
        val rows = MgmParser.parseDaily(dailyJson, "Bakırköy")!!.rows
        assertEquals(2, rows.size)
        val day0 = rows[0]
        fun v(row: com.istanbul.weatherintel.data.model.ForecastRow, code: String) =
            row.measurements.first { it.code == code }.value
        assertEquals(9.0, v(day0, "enDusukGun0")!!, 0.001)
        assertEquals(16.0, v(day0, "enYuksekGun0")!!, 0.001)
        assertEquals(60.0, v(day0, "enDusukNemGun0")!!, 0.001)
        assertEquals(88.0, v(day0, "enYuksekNemGun0")!!, 0.001)
        assertEquals(11.0, v(day0, "ruzgarHizGun0")!!, 0.001)
        assertEquals(215.0, v(day0, "ruzgarYonGun0")!!, 0.001)
        assertEquals("Parçalı Bulutlu", day0.measurements.first { it.code == "hadiseGun0Adi" }.text)
        assertEquals("Yağmurlu", rows[1].measurements.first { it.code == "hadiseGun1Adi" }.text)
    }

    @Test
    fun `units are the verified MGM units`() {
        val obs = observation()!!
        assertEquals("degC", obs.measurements.first { it.code == "sicaklik" }.unit)
        assertEquals("%", obs.measurements.first { it.code == "nem" }.unit)
        assertEquals("hPa", obs.measurements.first { it.code == "aktuelBasinc" }.unit)
        assertEquals("km/h", obs.measurements.first { it.code == "ruzgarHiz" }.unit)
        assertEquals("m", obs.measurements.first { it.code == "gorus" }.unit)
        assertEquals("mm", obs.measurements.first { it.code == "yagis24Saat" }.unit)
    }

    @Test
    fun `unknown MGM condition code is preserved rather than dropped`() {
        assertEquals("ZZZ", MgmCodes.label("ZZZ"))
        assertNotNull(MgmCodes.label("A"))
    }
}
