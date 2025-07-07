# Drools Engine API

A Spring Boot REST API powered by Drools and Excel decision tables, delivering loyalty discount and currency conversion rules based on country, region, tier, and duration — with runtime rule refresh and S3 integration.

---

## ⚙️ Tech Stack

| Layer           | Tech                                              |
|----------------|---------------------------------------------------|
| API Framework   | Spring Boot 3.x                                   |
| Rules Engine    | Drools (XLS decision tables)                      |
| Cloud Storage   | AWS S3 / LocalStack                               |
| Testing         | JUnit 5, Testcontainers, Mockito, MockMvc         |
| Build Tool      | Maven + JaCoCo                                    |
| Runtime         | Java 17                                           |
| Containerization| Docker, Docker Compose                            |

---

## 🧪 API Usage

### Endpoint

URL: POST: http://localhost:8080/api/loyalty/discount

### Request Payload

```json
{
  "country": "AU",
  "state": "NSW",
  "city": "SYD",
  "loyaltyTier": "BRZ",
  "loyaltyPeriod": "1"
}
```

### Response Payload
```json
{
  "conversionRateUSD": "1.0",
  "discountPercentage": "0.5"
}
```

## Local Development (with LocalStack)

### 🚀 One Command Startup

Makefile is create with required commands which can be used to build and run the API.

#### up
This will build and spin up the localstack and API containers
```bash
make up
```

#### seed
This will copy the rulesheet XLSX file to S3 bucket in localstack
```bash
make seed
```

#### logs
This will display the logs for application
```bash
make logs
```

#### down
This will teardown the localstack and API containers
```bash
make down
```

### API Request
```base
curl -X POST http://localhost:8080/api/loyalty/discount \
     -H "Content-Type: application/json" \
     -d '{"country":"AU","state":"NSW","city":"SYD","loyaltyTier":"BRZ","loyaltyPeriod":"1"}'
```
---

## SpringBoot Local Setup

If you want to connect the SpringBoot API from local environment to your personal AWS S3, then 
create the bucket as per `application.yml` and then uplaod the sheet present in `resources/rules` folder.

Note: XLSX file present in the resources folder is not used by the application.