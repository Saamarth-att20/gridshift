package com.gridshift.routing.signal;

/**
 * Abstraction over a real-time carbon intensity data source.
 *
 * Implementations:
 *   - ElectricityMapsSignal  (live, requires API key)
 *   - WattTimeSignal         (live, requires API key)
 *   - StaticFallbackSignal   (hardcoded — for offline demo / CI)
 *
 * Returns gCO2eq per kWh for a given cloud region.
 *
 * @author Saamarth Attray
 */
public interface CarbonSignal {
    /**
     * @param provider  "aws" | "gcp" | "azure"
     * @param region    provider-specific region identifier
     * @return gCO2eq per kWh (real-time or best available estimate)
     */
    double getCarbonIntensity(String provider, String region);

    /**
     * @return true if this signal is backed by a live API call,
     *         false if using static / cached fallback data
     */
    boolean isLive();
}
