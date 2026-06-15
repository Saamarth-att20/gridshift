# GridShift

AI inference routing engine. Routes workloads to the lowest-cost cloud region
across real-time carbon intensity, water stress, and compute cost signals.

## Requirements

- Java 21+
- Maven 3.9+

## Setup

```bash
# 1. Copy env template
cp .env.example .env

# 2. Fill in your keys in .env
#    ELECTRICITY_MAPS_API_KEY — free at electricitymaps.com
#    GRIDSHIFT_API_KEY        — any secret string you choose

# 3. Run
mvn spring-boot:run
```

Server starts at http://localhost:8080

## API

### POST /api/v1/route

**Headers**
```
Content-Type: application/json
X-Api-Key: your_secret_api_key_here
```

**Body**
```json
{
  "jobId": "job-001",
  "modelId": "llama-3-70b",
  "gpuHours": 2.0,
  "deadlineHours": 6,
  "homeRegion": "us-east-1"
}
```

**Response**
```json
{
  "jobId": "job-001",
  "chosenRegion": "us-west1",
  "chosenProvider": "gcp",
  "totalCostUsd": 6.0252,
  "baselineCostUsd": 7.2369,
  "dollarsSaved": 1.2117,
  "carbonIntensity": 80.0,
  "baselineCarbonIntensity": 410.0,
  "carbonReductionPct": 80.5,
  "delayMinutes": 2,
  "usedLiveCarbon": false,
  "rationale": "..."
}
```

### GET /api/v1/health

Public — no auth required.

## Architecture

```
src/main/java/com/gridshift/
├── GridShiftApplication.java       Entry point
├── config/
│   ├── SecurityConfig.java         API key auth + security headers
│   └── RateLimitConfig.java        IP-based rate limiting (Bucket4j)
├── controller/
│   ├── RoutingController.java      POST /api/v1/route
│   └── GlobalExceptionHandler.java No stack traces to callers
├── dto/
│   ├── RouteRequest.java           Validated inbound request
│   └── RouteResponse.java          Sanitized outbound response
└── routing/
    ├── RoutingEngine.java           Pure decision logic
    ├── model/                       Workload, RoutingCandidate, etc.
    ├── scoring/                     ScoringWeights, CandidateScorer
    └── signal/                      ElectricityMaps, fallback, SignalLayer
```
