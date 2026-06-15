package com.gridshift.routing.signal;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Live carbon intensity via the ElectricityMaps API.
 * https://static.electricitymaps.com/api/docs/index.html
 *
 * Free tier: 10,000 calls/month.
 * Set ELECTRICITY_MAPS_API_KEY as an environment variable.
 *
 * Maps cloud provider regions to ElectricityMaps zone codes.
 * Caches responses for 5 minutes to stay within free tier limits.
 *
 * @author Saamarth Attray
 */
public final class ElectricityMapsSignal implements CarbonSignal {

    private static final Logger LOG = Logger.getLogger(ElectricityMapsSignal.class.getName());
    private static final String BASE_URL = "https://api.electricitymap.org/v3/carbon-intensity/latest?zone=";
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L; // 5 minutes

    /** Maps "provider/region" → ElectricityMaps zone code */
    private static final Map<String, String> REGION_TO_ZONE = Map.ofEntries(
            Map.entry("aws/us-east-1",       "US-MIDA-PJM"),
            Map.entry("aws/us-east-2",       "US-MIDW-MISO"),
            Map.entry("aws/us-west-1",       "US-CAL-CISO"),
            Map.entry("aws/us-west-2",       "US-NW-PACW"),
            Map.entry("aws/eu-west-1",       "IE"),
            Map.entry("aws/eu-north-1",      "SE-SE3"),
            Map.entry("gcp/us-central1",     "US-MIDW-MISO"),
            Map.entry("gcp/us-east1",        "US-SE-SOCO"),
            Map.entry("gcp/europe-north1",   "FI"),
            Map.entry("gcp/europe-west1",    "BE"),
            Map.entry("gcp/asia-east1",      "TW"),
            Map.entry("azure/eastus",        "US-MIDA-PJM"),
            Map.entry("azure/westus2",       "US-NW-PACW"),
            Map.entry("azure/northeurope",   "IE"),
            Map.entry("azure/swedencentral", "SE-SE3")
    );

    /** Simple in-process cache: zone → (intensity, fetchedAt) */
    private record CachedValue(double intensity, long fetchedAt) {}
    private final Map<String, CachedValue> cache = new ConcurrentHashMap<>();

    private final HttpClient http;
    private final String apiKey;

    public ElectricityMapsSignal(String apiKey) {
        this.apiKey = apiKey;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public double getCarbonIntensity(String provider, String region) {
        String key = provider + "/" + region;
        String zone = REGION_TO_ZONE.get(key);

        if (zone == null) {
            LOG.warning("No zone mapping for " + key + " — using fallback 400 gCO2/kWh");
            return 400.0;
        }

        // Check cache
        CachedValue cached = cache.get(zone);
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt() < CACHE_TTL_MS) {
            return cached.intensity();
        }

        // Fetch live
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + zone))
                    .header("auth-token", apiKey)
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                double intensity = parseCarbonIntensity(response.body());
                cache.put(zone, new CachedValue(intensity, System.currentTimeMillis()));
                LOG.info(String.format("Live carbon [%s]: %.1f gCO2/kWh", zone, intensity));
                return intensity;
            } else {
                LOG.warning("ElectricityMaps returned " + response.statusCode() + " for " + zone);
            }
        } catch (IOException | InterruptedException e) {
            LOG.warning("ElectricityMaps fetch failed for " + zone + ": " + e.getMessage());
        }

        // Fallback to static estimate if API fails
        return StaticFallbackSignal.INSTANCE.getCarbonIntensity(provider, region);
    }

    @Override
    public boolean isLive() { return true; }

    /**
     * Parses: {"zone":"US-MIDA-PJM","carbonIntensity":248,"datetime":"...","updatedAt":"..."}
     * Simple manual parse to avoid pulling in a JSON library dependency.
     */
    private double parseCarbonIntensity(String json) {
        int idx = json.indexOf("\"carbonIntensity\":");
        if (idx == -1) throw new IllegalArgumentException("carbonIntensity not found in: " + json);
        int start = idx + "\"carbonIntensity\":".length();
        int end = json.indexOf(",", start);
        if (end == -1) end = json.indexOf("}", start);
        return Double.parseDouble(json.substring(start, end).trim());
    }
}
