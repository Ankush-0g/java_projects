# 💱 Currency Converter

A full-stack currency converter web application with a Java backend and a vanilla HTML/CSS/JavaScript frontend. The backend fetches live exchange rates from free APIs and falls back to hardcoded rates if offline, keeping the converter functional in both online and offline modes.

## Features

- Convert between 16 major currencies (USD, EUR, GBP, INR, AUD, CAD, JPY, CNY, NZD, SGD, CHF, ZAR, SEK, NOK, MXN, HKD)
- Live exchange rates from multiple free, no-key APIs
- Automatic fallback to offline rates when the internet is unavailable
- In-memory caching to stay within API rate limits
- Simple, responsive UI with dropdowns for currency selection
- Error handling for invalid inputs and network issues
- Cross-origin resource sharing (CORS) enabled

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Java HTTP Server |
| Frontend | HTML5, CSS3, JavaScript (vanilla) |
| APIs used | [ExchangeRate-API](https://www.exchangerate-api.com/), [Open ER API](https://open.er-api.com/) |
| Caching | In-memory cache with TTL (10 min) |

## Project Structure

currency-converter/
├── backend/
│   ├── pom.xml
│   └── src/main/java/CurrencyConverterServer.java
├── frontend/
│   └── index.html
└── README.md

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven
- A modern web browser (Chrome, Firefox, Edge, etc.)

### Run the backend

From the backend folder:

```bash
mvn package
java -jar target/currency-converter-backend-1.0-SNAPSHOT.jar
```

The backend will run on port 5000 and expose:

- /api/currencies
- /api/convert

### Run the frontend

Open [frontend/index.html](frontend/index.html) in a browser, or serve the folder with any static file server.

