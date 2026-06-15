package com.gridshift.routing.signal;

import java.util.Map;

/**
 * Hardcoded carbon intensity fallback — used when no API key is configured
 * or when the live API is unavailable.
 *
 * Values are sourced from published annual averages (ElectricityMaps 2023 data)
 * and are good enough for demo purposes. Production should always use a live source.
 *
 * @author Saamarth Attray
 */
public final class StaticFallbackSignal implements CarbonSignal {

    public static final StaticFallbackSignal INSTANCE = new StaticFallbackSignal();

    /** gCO2eq/kWh annual averages per cloud region */
    private static final Map<String, Double> INTENSITIES = Map.ofEntries(
            // AWS
            Map.entry("aws/us-east-1",       410.0),  // PJM grid, coal mix
            Map.entry("aws/us-east-2",       510.0),  // MISO, high coal
            Map.entry("aws/us-west-1",        90.0),  // California, high renewables
            Map.entry("aws/us-west-2",        80.0),  // Pacific NW, mostly hydro
            Map.entry("aws/eu-west-1",       220.0),  // Ireland, improving
            Map.entry("aws/eu-north-1",       15.0),  // Sweden, near-zero
            // GCP
            Map.entry("gcp/us-central1",     480.0),  // Iowa, Midwest coal mix
            Map.entry("gcp/us-east1",        520.0),  // South Carolina, coal heavy
            Map.entry("gcp/europe-north1",    15.0),  // Finland, near-zero
            Map.entry("gcp/europe-west1",    160.0),  // Belgium
            Map.entry("gcp/asia-east1",      550.0),  // Taiwan, coal heavy
            // Azure
            Map.entry("azure/eastus",        410.0),  // Virginia / PJM
            Map.entry("azure/westus2",        80.0),  // Washington, hydro
            Map.entry("azure/northeurope",   220.0),  // Ireland
            Map.entry("azure/swedencentral",  15.0)   // Sweden
    );

    private StaticFallbackSignal() {}

    @Override
    public double getCarbonIntensity(String provider, String region) {
        return INTENSITIES.getOrDefault(provider + "/" + region, 400.0);
    }

    @Override
    public boolean isLive() { return false; }
}
