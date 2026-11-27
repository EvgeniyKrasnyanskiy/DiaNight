package com.diaclock.nightstand;

import org.junit.Test;

import static org.junit.Assert.*;

public class GlucoseDataFetcherTest {

    @Test
    public void buildNightscoutUrl_normalizesSchemeTrailingSlashAndToken() {
        assertEquals(
                "https://my.ns.org/pebble",
                GlucoseDataFetcher.buildNightscoutUrl("my.ns.org/", "/pebble", null)
        );
        assertEquals(
                "https://my.ns.org/api/v1/entries/sgv.json?count=1&token=abc-123",
                GlucoseDataFetcher.buildNightscoutUrl("https://my.ns.org/api/v1/?token=abc-123", "/api/v1/entries/sgv.json", "count=1")
        );
        assertEquals(
                "http://192.168.1.50:1337/pebble?token=xyz",
                GlucoseDataFetcher.buildNightscoutUrl("http://192.168.1.50:1337/?token=xyz", "/pebble", null)
        );
    }

    @Test
    public void parsePebbleResponse_parsesMgdlAndIobAndFreshness() {
        long now = 1_700_000_100_000L;
        long recent = now - 60_000L; // 1 min ago
        String json = "{\"bgs\":[{\"sgv\":\"108\",\"direction\":\"Flat\",\"datetime\":" + recent + ",\"iob\":\"0,64\"}]}";

        GlucoseDataFetcher.GlucoseReading reading = GlucoseDataFetcher.parsePebbleResponse(
                json, GlucoseDataFetcher.SOURCE_NIGHTSCOUT, 120_000L, now);

        assertNotNull(reading);
        assertEquals(6.0, reading.glucoseMmol, 0.01);
        assertEquals("Flat", reading.direction);
        assertEquals(0.64, reading.iob, 0.001);
        assertEquals(GlucoseDataFetcher.SOURCE_NIGHTSCOUT, reading.sourceTag);
        assertFalse(reading.isStale);
    }

    @Test
    public void parsePebbleResponse_detectsMmolUnitsAndStaleTimestamp() {
        long now = 1_700_000_200_000L;
        long stale = now - 180_000L; // 3 min ago > 2 min threshold
        String json = "{\"bgs\":[{\"sgv\":\"5.4\",\"direction\":\"SingleUp\",\"datetime\":" + stale + "}],"
                + "\"status\":[{\"iob\":{\"iob\":1.25}}]}";

        GlucoseDataFetcher.GlucoseReading reading = GlucoseDataFetcher.parsePebbleResponse(
                json, GlucoseDataFetcher.SOURCE_WIFI, 120_000L, now);

        assertNotNull(reading);
        assertEquals(5.4, reading.glucoseMmol, 0.01);
        assertEquals("SingleUp", reading.direction);
        assertEquals(1.25, reading.iob, 0.001);
        assertTrue(reading.isStale);
    }

    @Test
    public void parseSgvResponse_parsesArrayWithEpochDate() {
        long now = 1_700_000_200_000L;
        long recent = now - 30_000L;
        String json = "[{\"sgv\":126,\"direction\":\"FortyFiveUp\",\"date\":" + recent + "}]";

        GlucoseDataFetcher.GlucoseReading reading = GlucoseDataFetcher.parseSgvResponse(
                json, GlucoseDataFetcher.SOURCE_NIGHTSCOUT, 120_000L, now);

        assertNotNull(reading);
        assertEquals(7.0, reading.glucoseMmol, 0.01);
        assertEquals("FortyFiveUp", reading.direction);
        assertEquals(-1.0, reading.iob, 0.001);
        assertFalse(reading.isStale);
    }
}
