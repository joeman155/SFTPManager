package com.sftpmanager.service.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Talks to the Stripe REST API directly with the JDK HTTP client —
 * no SDK dependency needed for the handful of endpoints we use.
 */
public class StripeGateway implements PaymentGateway {

    private static final String BASE = "https://api.stripe.com/v1";

    private final String secretKey;
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public StripeGateway(String secretKey) {
        this.secretKey = secretKey;
    }

    @Override
    public String mode() {
        return secretKey.startsWith("sk_live") ? "stripe-live" : "stripe-test";
    }

    @Override
    public String ensureCustomer(String existingCustomerId, String email, String name) throws GatewayException {
        if (existingCustomerId != null && !existingCustomerId.isBlank()) return existingCustomerId;
        Map<String, String> params = new LinkedHashMap<>();
        params.put("email", email);
        if (name != null && !name.isBlank()) params.put("name", name);
        JsonNode res = post("/customers", params, null);
        return res.path("id").asText();
    }

    @Override
    public SetupIntent createSetupIntent(String customerId) throws GatewayException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("customer", customerId);
        params.put("payment_method_types[]", "card");
        params.put("usage", "off_session");
        JsonNode res = post("/setup_intents", params, null);
        return new SetupIntent(res.path("id").asText(), res.path("client_secret").asText());
    }

    @Override
    public CardDetails getCard(String paymentMethodId) throws GatewayException {
        JsonNode res = get("/payment_methods/" + paymentMethodId);
        JsonNode card = res.path("card");
        String expiry = String.format("%02d/%02d",
            card.path("exp_month").asInt(), card.path("exp_year").asInt() % 100);
        return new CardDetails(card.path("brand").asText(), card.path("last4").asText(), expiry);
    }

    @Override
    public ChargeResult charge(String customerId, String paymentMethodId, long amountCents,
                               String currency, String description, String idempotencyKey) throws GatewayException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("amount", String.valueOf(amountCents));
        params.put("currency", currency);
        params.put("customer", customerId);
        params.put("payment_method", paymentMethodId);
        params.put("off_session", "true");
        params.put("confirm", "true");
        if (description != null) params.put("description", description);
        try {
            JsonNode res = post("/payment_intents", params, idempotencyKey);
            String status = res.path("status").asText();
            if ("succeeded".equals(status)) {
                return new ChargeResult(true, res.path("id").asText(), null);
            }
            return new ChargeResult(false, res.path("id").asText(), "Payment status: " + status);
        } catch (StripeApiException e) {
            // Card declined / insufficient funds etc. come back as API errors
            return new ChargeResult(false, e.paymentIntentId, e.getMessage());
        }
    }

    @Override
    public void detach(String paymentMethodId) throws GatewayException {
        post("/payment_methods/" + paymentMethodId + "/detach", Map.of(), null);
    }

    // ── HTTP plumbing ──

    private static class StripeApiException extends GatewayException {
        final String paymentIntentId;
        StripeApiException(String message, String paymentIntentId) {
            super(message);
            this.paymentIntentId = paymentIntentId;
        }
    }

    private JsonNode get(String path) throws GatewayException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + path))
            .header("Authorization", "Bearer " + secretKey)
            .timeout(Duration.ofSeconds(30))
            .GET().build();
        return send(req);
    }

    private JsonNode post(String path, Map<String, String> params, String idempotencyKey) throws GatewayException {
        String body = params.entrySet().stream()
            .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                    + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE + path))
            .header("Authorization", "Bearer " + secretKey)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(body));
        if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
        return send(builder.build());
    }

    private JsonNode send(HttpRequest req) throws GatewayException {
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode json = mapper.readTree(res.body());
            if (res.statusCode() >= 400) {
                JsonNode err = json.path("error");
                String msg = err.path("message").asText("Stripe error HTTP " + res.statusCode());
                String piId = err.path("payment_intent").path("id").asText(null);
                throw new StripeApiException(msg, piId);
            }
            return json;
        } catch (StripeApiException e) {
            throw e;
        } catch (Exception e) {
            throw new GatewayException("Stripe request failed: " + e.getMessage(), e);
        }
    }
}
