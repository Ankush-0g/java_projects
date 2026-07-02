import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CurrencyConverterServer {
    private static final long CACHE_TTL = 600_000L;
    private static final Map<String, String> CURRENCY_NAMES = new LinkedHashMap<>();
    private static final Map<String, Double> FALLBACK_RATES = new LinkedHashMap<>();

    private static Map<String, Double> ratesCacheData = null;
    private static long ratesCacheTimestamp = 0L;

    static {
        CURRENCY_NAMES.put("USD", "US Dollar");
        CURRENCY_NAMES.put("EUR", "Euro");
        CURRENCY_NAMES.put("GBP", "British Pound");
        CURRENCY_NAMES.put("INR", "Indian Rupee");
        CURRENCY_NAMES.put("AUD", "Australian Dollar");
        CURRENCY_NAMES.put("CAD", "Canadian Dollar");
        CURRENCY_NAMES.put("JPY", "Japanese Yen");
        CURRENCY_NAMES.put("CNY", "Chinese Yuan Renminbi");
        CURRENCY_NAMES.put("NZD", "New Zealand Dollar");
        CURRENCY_NAMES.put("SGD", "Singapore Dollar");
        CURRENCY_NAMES.put("CHF", "Swiss Franc");
        CURRENCY_NAMES.put("ZAR", "South African Rand");
        CURRENCY_NAMES.put("SEK", "Swedish Krona");
        CURRENCY_NAMES.put("NOK", "Norwegian Krone");
        CURRENCY_NAMES.put("MXN", "Mexican Peso");
        CURRENCY_NAMES.put("HKD", "Hong Kong Dollar");

        FALLBACK_RATES.put("USD", 1.0);
        FALLBACK_RATES.put("EUR", 0.92);
        FALLBACK_RATES.put("GBP", 0.79);
        FALLBACK_RATES.put("INR", 83.12);
        FALLBACK_RATES.put("AUD", 1.52);
        FALLBACK_RATES.put("CAD", 1.36);
        FALLBACK_RATES.put("JPY", 149.85);
        FALLBACK_RATES.put("CNY", 7.24);
        FALLBACK_RATES.put("NZD", 1.62);
        FALLBACK_RATES.put("SGD", 1.34);
        FALLBACK_RATES.put("CHF", 0.88);
        FALLBACK_RATES.put("ZAR", 18.73);
        FALLBACK_RATES.put("SEK", 10.45);
        FALLBACK_RATES.put("NOK", 10.62);
        FALLBACK_RATES.put("MXN", 17.10);
        FALLBACK_RATES.put("HKD", 7.82);
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(5000), 0);
        server.createContext("/api/currencies", new CurrencyListHandler());
        server.createContext("/api/convert", new ConversionHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("Currency converter Java backend is running on http://localhost:5000");
    }

    private static Map<String, Double> fetchRatesFromApi(String apiUrl) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);

        int status = connection.getResponseCode();
        if (status != 200) {
            throw new IOException("HTTP " + status);
        }

        InputStream inputStream = connection.getInputStream();
        String body = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        inputStream.close();

        Matcher matcher = Pattern.compile("\\\"rates\\\"\\s*:\\s*\\{(.*?)\\}").matcher(body);
        if (!matcher.find()) {
            throw new Exception("Response missing 'rates'");
        }

        String ratesSection = matcher.group(1);
        Pattern ratePattern = Pattern.compile("\\\"([A-Z]{3})\\\"\\s*:\\s*([0-9.]+)");
        Matcher rateMatcher = ratePattern.matcher(ratesSection);
        Map<String, Double> rates = new LinkedHashMap<>();
        while (rateMatcher.find()) {
            rates.put(rateMatcher.group(1), Double.parseDouble(rateMatcher.group(2)));
        }

        if (rates.isEmpty()) {
            throw new Exception("Response missing 'rates'");
        }
        return rates;
    }

    private static Map<String, Double> getRates() throws Exception {
        long now = System.currentTimeMillis();
        if (ratesCacheData != null && (now - ratesCacheTimestamp) < CACHE_TTL) {
            return ratesCacheData;
        }

        List<String> apis = Arrays.asList(
                "https://api.exchangerate-api.com/v4/latest/USD",
                "https://open.er-api.com/v6/latest/USD"
        );

        for (String apiUrl : apis) {
            try {
                Map<String, Double> rates = fetchRatesFromApi(apiUrl);
                ratesCacheData = rates;
                ratesCacheTimestamp = now;
                System.out.println("Rates fetched from " + apiUrl);
                return rates;
            } catch (Exception exception) {
                System.out.println("API " + apiUrl + " failed: " + exception.getMessage());
                exception.printStackTrace();
            }
        }

        System.out.println("All external APIs failed. Using fallback rates (offline mode).");
        ratesCacheData = FALLBACK_RATES;
        ratesCacheTimestamp = now;
        return FALLBACK_RATES;
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
    }

    private static void writeJsonResponse(HttpExchange exchange, int statusCode, String payload) throws IOException {
        byte[] responseBytes = payload.getBytes(StandardCharsets.UTF_8);
        addCorsHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }

    private static class CurrencyListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            StringBuilder json = new StringBuilder();
            json.append("[");
            boolean first = true;
            for (Map.Entry<String, String> entry : CURRENCY_NAMES.entrySet()) {
                if (!first) {
                    json.append(",");
                }
                json.append("{\"code\":\"").append(entry.getKey()).append("\",\"name\":\"")
                        .append(entry.getValue()).append("\"}");
                first = false;
            }
            json.append("]");
            writeJsonResponse(exchange, 200, json.toString());
        }
    }

    private static class ConversionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                addCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = new LinkedHashMap<>();
            if (query != null && !query.isEmpty()) {
                for (String part : query.split("&")) {
                    String[] pieces = part.split("=", 2);
                    if (pieces.length == 2) {
                        params.put(pieces[0], java.net.URLDecoder.decode(pieces[1], StandardCharsets.UTF_8));
                    }
                }
            }

            String amountParam = params.get("amount");
            String fromParam = params.get("from");
            String toParam = params.get("to");

            if (amountParam == null || fromParam == null || toParam == null) {
                writeJsonResponse(exchange, 400, "{\"error\":\"Missing parameters. Use amount, from, to.\"}");
                return;
            }

            double amount;
            try {
                amount = Double.parseDouble(amountParam);
            } catch (NumberFormatException exception) {
                writeJsonResponse(exchange, 400, "{\"error\":\"Invalid amount.\"}");
                return;
            }

            if (amount <= 0) {
                writeJsonResponse(exchange, 400, "{\"error\":\"Amount must be positive.\"}");
                return;
            }

            String fromCurrency = fromParam.toUpperCase();
            String toCurrency = toParam.toUpperCase();

            if (!CURRENCY_NAMES.containsKey(fromCurrency) || !CURRENCY_NAMES.containsKey(toCurrency)) {
                writeJsonResponse(exchange, 400, "{\"error\":\"Invalid currency code.\"}");
                return;
            }

            try {
                Map<String, Double> rates = getRates();
                if (!rates.containsKey(fromCurrency) || !rates.containsKey(toCurrency)) {
                    writeJsonResponse(exchange, 400, "{\"error\":\"Currency not supported by current rates.\"}");
                    return;
                }

                double converted = Math.round(amount * (rates.get(toCurrency) / rates.get(fromCurrency)) * 100.0) / 100.0;
                double rate = Math.round((rates.get(toCurrency) / rates.get(fromCurrency)) * 1_000_000.0) / 1_000_000.0;
                String payload = String.format("{\"amount\":%.2f,\"from\":\"%s\",\"to\":\"%s\",\"result\":%.2f,\"rate\":%.6f}",
                        amount, fromCurrency, toCurrency, converted, rate);
                writeJsonResponse(exchange, 200, payload);
            } catch (Exception exception) {
                writeJsonResponse(exchange, 502, "{\"error\":\"Failed to fetch exchange rates.\"}");
            }
        }
    }
}
