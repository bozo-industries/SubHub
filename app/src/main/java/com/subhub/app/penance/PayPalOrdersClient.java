package com.subhub.app.penance;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Environment-bound PayPal Orders v2 client with strict verification and bounded retries. */
public final class PayPalOrdersClient {
    private static final String RETURN_URL = "subhub://paypal/return";
    private static final String CANCEL_URL = "subhub://paypal/cancel";
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final PayPalRiskDataCollector riskData;

    public PayPalOrdersClient(Context context) {
        riskData = new PayPalRiskDataCollector(context);
    }

    /** Verifies merchant credentials against PayPal without creating an order or payer session. */
    public void validateCredentials(PayPalCredentialStore.Credentials credentials,
            Callback<Boolean> callback) {
        network.execute(() -> {
            try {
                accessToken(credentials);
                deliver(callback, Result.success(Boolean.TRUE));
            } catch (Exception error) {
                deliver(callback, Result.failure(safeMessage(error), classify(error)));
            }
        });
    }

    public void createOrder(PayPalCredentialStore.Credentials credentials,
            String settlementId, int amountCents, Callback<Order> callback) {
        createOrder(credentials, settlementId, amountCents, true, callback);
    }

    public void createOrder(PayPalCredentialStore.Credentials credentials,
            String settlementId, int amountCents, boolean requestVault,
            Callback<Order> callback) {
        network.execute(() -> {
            try {
                String clientMetadataId = riskData.collect(
                        credentials.clientId(), credentials.environment());
                if (clientMetadataId.isEmpty()) {
                    throw new IllegalStateException("PayPal risk data was unavailable");
                }
                String token = accessToken(credentials);
                JSONObject amount = new JSONObject()
                        .put("currency_code", PenanceManager.CURRENCY)
                        .put("value", decimalAmount(amountCents));
                JSONObject unit = new JSONObject()
                        .put("reference_id", settlementId)
                        .put("custom_id", settlementId)
                        .put("amount", amount);
                boolean vaultRequested = requestVault;
                JSONObject response;
                try {
                    response = request(credentials.environment(), "POST",
                            "/v2/checkout/orders", token,
                            createOrderBody(unit, clientMetadataId, vaultRequested).toString(),
                            vaultRequested
                                    ? PayPalRequestPolicy.createRequestId(settlementId)
                                    : PayPalRequestPolicy.standardCreateRequestId(settlementId),
                            clientMetadataId);
                } catch (PayPalApiException apiError) {
                    if (!vaultRequested || !PayPalVaultPolicy.shouldRetryWithoutVault(
                            apiError.status, apiError.context)) {
                        throw apiError;
                    }
                    response = request(credentials.environment(), "POST",
                            "/v2/checkout/orders", token,
                            createOrderBody(unit, clientMetadataId, false).toString(),
                            PayPalRequestPolicy.standardCreateRequestId(settlementId),
                            clientMetadataId);
                    vaultRequested = false;
                }
                String orderId = response.optString("id", "");
                String approvalUrl = link(response.optJSONArray("links"), "approve");
                if (approvalUrl.isEmpty()) {
                    approvalUrl = link(response.optJSONArray("links"), "payer-action");
                }
                if (orderId.isEmpty() || approvalUrl.isEmpty()) {
                    throw new IllegalStateException("PayPal order response was incomplete");
                }
                deliver(callback, Result.success(
                        new Order(orderId, approvalUrl, clientMetadataId, vaultRequested)));
            } catch (Exception error) {
                deliver(callback, Result.failure(safeMessage(error), classify(error)));
            }
        });
    }

    private static JSONObject createOrderBody(JSONObject unit, String clientMetadataId,
            boolean requestVault) throws Exception {
        JSONObject experience = new JSONObject()
                .put("user_action", "PAY_NOW")
                .put("shipping_preference", "NO_SHIPPING")
                .put("return_url", callbackUrl(RETURN_URL, clientMetadataId))
                .put("cancel_url", callbackUrl(CANCEL_URL, clientMetadataId));
        JSONObject paypal = new JSONObject().put("experience_context", experience);
        if (requestVault) {
            paypal.put("attributes", new JSONObject().put("vault",
                    new JSONObject()
                            .put("store_in_vault", "ON_SUCCESS")
                            .put("usage_type", "MERCHANT")
                            .put("usage_pattern", "UNSCHEDULED_POSTPAID")
                            .put("customer_type", "CONSUMER")));
        }
        return new JSONObject()
                .put("intent", "CAPTURE")
                .put("purchase_units", new JSONArray().put(unit))
                .put("payment_source", new JSONObject().put("paypal", paypal));
    }

    public void captureOrder(PayPalCredentialStore.Credentials credentials,
            String orderId, String settlementId, int expectedAmountCents,
            String clientMetadataId, Callback<Capture> callback) {
        network.execute(() -> {
            try {
                String token = accessToken(credentials);
                String metadata = clientMetadataId == null ? "" : clientMetadataId.trim();
                if (metadata.isEmpty()) {
                    metadata = riskData.collect(
                            credentials.clientId(), credentials.environment());
                }
                if (metadata.isEmpty()) {
                    throw new IllegalStateException("PayPal risk data was unavailable");
                }
                JSONObject response = request(credentials.environment(), "POST",
                        "/v2/checkout/orders/" + orderId + "/capture", token, "{}",
                        PayPalRequestPolicy.captureRequestId(settlementId), metadata);
                deliver(callback, Result.success(parseCapture(
                        response, settlementId, expectedAmountCents)));
            } catch (Exception error) {
                deliver(callback, Result.failure(safeMessage(error), classify(error)));
            }
        });
    }

    /** Captures a saved PayPal wallet for an explicitly authorized Hardcore pact. */
    public void createStoredWalletPayment(PayPalCredentialStore.Credentials credentials,
            String settlementId, int amountCents, String vaultId,
            Callback<Capture> callback) {
        network.execute(() -> {
            try {
                String cleanVaultId = vaultId == null ? "" : vaultId.trim();
                if (cleanVaultId.isEmpty()) {
                    throw new ReauthorizationRequiredException(
                            "The saved PayPal wallet must be linked again");
                }
                String clientMetadataId = riskData.collect(
                        credentials.clientId(), credentials.environment());
                if (clientMetadataId.isEmpty()) {
                    throw new IllegalStateException("PayPal risk data was unavailable");
                }
                String token = accessToken(credentials);
                JSONObject unit = new JSONObject()
                        .put("reference_id", settlementId)
                        .put("custom_id", settlementId)
                        .put("amount", new JSONObject()
                                .put("currency_code", PenanceManager.CURRENCY)
                                .put("value", decimalAmount(amountCents)));
                JSONObject paypal = new JSONObject()
                        .put("vault_id", cleanVaultId)
                        .put("stored_credential", new JSONObject()
                                .put("payment_initiator", "MERCHANT")
                                .put("usage", "SUBSEQUENT")
                                .put("usage_pattern", "UNSCHEDULED_POSTPAID"));
                JSONObject body = new JSONObject()
                        .put("intent", "CAPTURE")
                        .put("purchase_units", new JSONArray().put(unit))
                        .put("payment_source", new JSONObject().put("paypal", paypal));
                JSONObject response = request(credentials.environment(), "POST",
                        "/v2/checkout/orders", token, body.toString(),
                        PayPalRequestPolicy.autoRequestId(settlementId), clientMetadataId);
                String status = response.optString("status", "");
                if ("PAYER_ACTION_REQUIRED".equalsIgnoreCase(status)
                        || !link(response.optJSONArray("links"), "payer-action").isEmpty()
                        || !link(response.optJSONArray("links"), "approve").isEmpty()) {
                    throw new ReauthorizationRequiredException(
                            "PayPal requires the wallet owner to approve again");
                }
                deliver(callback, Result.success(parseCapture(
                        response, settlementId, amountCents)));
            } catch (Exception error) {
                deliver(callback, Result.failure(safeMessage(error), classify(error)));
            }
        });
    }

    private static Capture parseCapture(JSONObject response, String settlementId,
            int expectedAmountCents) throws Exception {
        if (!"COMPLETED".equalsIgnoreCase(response.optString("status"))) {
            throw new IllegalStateException("PayPal order is not completed");
        }
        JSONArray units = response.optJSONArray("purchase_units");
        JSONObject unit = units == null || units.length() == 0
                ? null : units.optJSONObject(0);
        if (unit == null || !settlementId.equals(unit.optString("custom_id"))) {
            throw new IllegalStateException("PayPal settlement reference did not match");
        }
        JSONObject payments = unit.optJSONObject("payments");
        JSONArray captures = payments == null ? null : payments.optJSONArray("captures");
        JSONObject capture = captures == null || captures.length() == 0
                ? null : captures.optJSONObject(0);
        if (capture == null || !"COMPLETED".equalsIgnoreCase(capture.optString("status"))) {
            throw new IllegalStateException("PayPal capture is not completed");
        }
        JSONObject amount = capture.optJSONObject("amount");
        if (amount == null
                || !PenanceManager.CURRENCY.equalsIgnoreCase(amount.optString("currency_code"))
                || expectedAmountCents != cents(amount.optString("value"))) {
            throw new IllegalStateException("PayPal capture amount did not match");
        }
        JSONObject source = response.optJSONObject("payment_source");
        JSONObject paypal = source == null ? null : source.optJSONObject("paypal");
        JSONObject attributes = paypal == null ? null : paypal.optJSONObject("attributes");
        if (attributes == null && paypal != null) attributes = paypal.optJSONObject("attribute");
        JSONObject vault = attributes == null ? null : attributes.optJSONObject("vault");
        JSONObject customer = vault == null ? null : vault.optJSONObject("customer");
        JSONObject payer = response.optJSONObject("payer");
        String payerEmail = paypal == null ? "" : paypal.optString("email_address", "");
        String payerAccountId = paypal == null ? "" : paypal.optString("account_id", "");
        if (payerEmail.isEmpty() && payer != null) {
            payerEmail = payer.optString("email_address", "");
        }
        if (payerAccountId.isEmpty() && payer != null) {
            payerAccountId = payer.optString("payer_id", "");
        }
        return new Capture(capture.optString("id"),
                vault == null ? "" : vault.optString("status"),
                vault == null ? "" : vault.optString("id"),
                customer == null ? "" : customer.optString("id"),
                payerEmail, payerAccountId);
    }

    private String accessToken(PayPalCredentialStore.Credentials credentials)
            throws Exception {
        if (!credentials.isComplete()) throw new IllegalStateException("PayPal credentials missing");
        Exception latest = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return accessTokenOnce(credentials);
            } catch (Exception error) {
                latest = error;
                if (attempt > 0 || !retryable(error)) throw error;
                pauseForRetry();
            }
        }
        throw latest == null ? new IOException("PayPal authentication failed") : latest;
    }

    private String accessTokenOnce(PayPalCredentialStore.Credentials credentials)
            throws Exception {
        String basic = credentials.clientId() + ":" + credentials.secret();
        HttpURLConnection connection = connection(
                credentials.environment(), "/v1/oauth2/token", "POST");
        connection.setRequestProperty("Authorization", "Basic " + Base64.encodeToString(
                basic.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        write(connection, "grant_type=client_credentials");
        JSONObject response = response(connection);
        String token = response.optString("access_token", "");
        if (token.isEmpty()) throw new IllegalStateException("PayPal authentication failed");
        return token;
    }

    private JSONObject request(PayPalEnvironment environment, String method,
            String path, String token, String body,
            String requestId, String clientMetadataId) throws Exception {
        Exception latest = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return requestOnce(environment, method, path, token, body,
                        requestId, clientMetadataId);
            } catch (Exception error) {
                latest = error;
                if (attempt > 0 || !retryable(error)) throw error;
                pauseForRetry();
            }
        }
        throw latest == null ? new IOException("PayPal request failed") : latest;
    }

    private JSONObject requestOnce(PayPalEnvironment environment, String method,
            String path, String token, String body,
            String requestId, String clientMetadataId) throws Exception {
        HttpURLConnection connection = connection(environment, path, method);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Prefer", "return=representation");
        if (requestId != null && !requestId.isEmpty()) {
            connection.setRequestProperty("PayPal-Request-Id", requestId);
        }
        if (clientMetadataId != null && !clientMetadataId.isEmpty()) {
            connection.setRequestProperty("PayPal-Client-Metadata-Id", clientMetadataId);
        }
        write(connection, body);
        return response(connection);
    }

    private static HttpURLConnection connection(PayPalEnvironment environment,
            String path, String method) throws Exception {
        PayPalEnvironment selected = environment == null
                ? PayPalEnvironment.SANDBOX : environment;
        URL url = new URL(selected.apiRoot() + path);
        if (!selected.apiHost().equalsIgnoreCase(url.getHost())) {
            throw new IllegalStateException("PayPal environment boundary mismatch");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(20_000);
        connection.setUseCaches(false);
        connection.setDoOutput(true);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Accept-Language", "en_US");
        return connection;
    }

    private static void write(HttpURLConnection connection, String body) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(bytes);
        }
    }

    private static JSONObject response(HttpURLConnection connection) throws Exception {
        int status = connection.getResponseCode();
        String debugId = connection.getHeaderField("PayPal-Debug-Id");
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream();
        String body = read(stream);
        connection.disconnect();
        if (status < 200 || status >= 300) {
            String detail = "PayPal returned HTTP " + status;
            String issue = "";
            StringBuilder context = new StringBuilder();
            try {
                JSONObject error = new JSONObject(body);
                appendContext(context, error.optString("name", ""));
                appendContext(context, error.optString("message", ""));
                JSONArray details = error.optJSONArray("details");
                String description = "";
                if (details != null) {
                    for (int index = 0; index < details.length(); index++) {
                        JSONObject item = details.optJSONObject(index);
                        if (item == null) continue;
                        String itemIssue = item.optString("issue", "");
                        String itemDescription = item.optString("description", "");
                        if (issue.isEmpty()) issue = itemIssue;
                        if (description.isEmpty()) description = itemDescription;
                        appendContext(context, itemIssue);
                        appendContext(context, item.optString("field", ""));
                        appendContext(context, itemDescription);
                    }
                }
                if (issue.isEmpty()) issue = error.optString("name", "");
                if (!issue.isEmpty()) detail += ": " + issue;
                if (!description.isEmpty()) detail += " — " + description;
                else {
                    String message = error.optString("message", "");
                    if (!message.isEmpty()) detail += ": " + message;
                }
            } catch (Exception ignored) {}
            if (debugId != null && !debugId.isEmpty()) detail += " · debug " + debugId;
            throw new PayPalApiException(status, issue, context.toString(), detail);
        }
        return body.isEmpty() ? new JSONObject() : new JSONObject(body);
    }

    private static void appendContext(StringBuilder target, String value) {
        if (value == null || value.trim().isEmpty()) return;
        if (target.length() > 0) target.append(' ');
        target.append(value.trim());
    }

    private static boolean retryable(Exception error) {
        if (error instanceof PayPalApiException) {
            int status = ((PayPalApiException) error).status;
            return PayPalRequestPolicy.isTransientStatus(status);
        }
        return error instanceof IOException;
    }

    private static void pauseForRetry() throws InterruptedException {
        try {
            Thread.sleep(350L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        }
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            int total = 0;
            while ((count = input.read(buffer)) != -1 && total < 262_144) {
                int accepted = Math.min(count, 262_144 - total);
                output.write(buffer, 0, accepted);
                total += accepted;
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String link(JSONArray links, String relation) {
        if (links == null) return "";
        for (int index = 0; index < links.length(); index++) {
            JSONObject link = links.optJSONObject(index);
            if (link != null && relation.equals(link.optString("rel"))) {
                return link.optString("href", "");
            }
        }
        return "";
    }

    private static String decimalAmount(int amountCents) {
        return String.format(Locale.ROOT, "%.2f", amountCents / 100.0);
    }

    private static String callbackUrl(String base, String clientMetadataId) {
        return base + "?cmid=" + android.net.Uri.encode(clientMetadataId);
    }

    private static int cents(String decimal) {
        return new java.math.BigDecimal(decimal).movePointRight(2).intValueExact();
    }

    private static String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? "PayPal request failed" : message;
    }

    private static ErrorKind classify(Exception error) {
        if (error instanceof ReauthorizationRequiredException) {
            return ErrorKind.REAUTHORIZATION_REQUIRED;
        }
        if (error instanceof PayPalApiException) {
            PayPalApiException api = (PayPalApiException) error;
            if (PayPalVaultPolicy.isUnavailableIssue(api.issue)
                    || PayPalVaultPolicy.shouldRetryWithoutVault(api.status, api.context)) {
                return ErrorKind.VAULT_UNAVAILABLE;
            }
            if (reauthorizationIssue(api.issue)) {
                return ErrorKind.REAUTHORIZATION_REQUIRED;
            }
            if (api.status == 401 || api.status == 403) return ErrorKind.AUTHENTICATION;
            if (PayPalRequestPolicy.isTransientStatus(api.status)) return ErrorKind.TRANSIENT;
            return ErrorKind.API;
        }
        return error instanceof IOException ? ErrorKind.NETWORK : ErrorKind.CONFIGURATION;
    }

    private static boolean reauthorizationIssue(String issue) {
        String normalized = issue == null ? "" : issue.trim().toUpperCase(Locale.ROOT);
        return normalized.contains("PAYER_ACTION_REQUIRED")
                || normalized.contains("PAYMENT_SOURCE_DECLINED")
                || normalized.contains("PAYMENT_SOURCE_INFO_CANNOT_BE_VERIFIED")
                || normalized.contains("INSTRUMENT_DECLINED");
    }

    private static final class ReauthorizationRequiredException extends IOException {
        private ReauthorizationRequiredException(String message) { super(message); }
    }

    private static final class PayPalApiException extends IOException {
        private final int status;
        private final String issue;
        private final String context;
        private PayPalApiException(
                int status, String issue, String context, String message) {
            super(message);
            this.status = status;
            this.issue = issue == null ? "" : issue;
            this.context = context == null ? "" : context;
        }
    }

    private <T> void deliver(Callback<T> callback, Result<T> result) {
        main.post(() -> callback.complete(result));
    }

    public void close() {
        network.shutdownNow();
    }

    public interface Callback<T> { void complete(Result<T> result); }

    public enum ErrorKind {
        NETWORK, TRANSIENT, AUTHENTICATION, VAULT_UNAVAILABLE,
        REAUTHORIZATION_REQUIRED, CONFIGURATION, API
    }

    public static final class Result<T> {
        private final T value;
        private final String error;
        private final ErrorKind errorKind;
        private Result(T value, String error, ErrorKind errorKind) {
            this.value = value;
            this.error = error;
            this.errorKind = errorKind;
        }
        public static <T> Result<T> success(T value) {
            return new Result<>(value, "", null);
        }
        public static <T> Result<T> failure(String error, ErrorKind errorKind) {
            return new Result<>(null, error, errorKind);
        }
        public boolean isSuccess() { return value != null; }
        public T value() { return value; }
        public String error() { return error; }
        public ErrorKind errorKind() { return errorKind; }
    }

    public static final class Order {
        private final String id;
        private final String approvalUrl;
        private final String clientMetadataId;
        private final boolean vaultRequested;
        private Order(String id, String approvalUrl, String clientMetadataId,
                boolean vaultRequested) {
            this.id = id;
            this.approvalUrl = approvalUrl;
            this.clientMetadataId = clientMetadataId;
            this.vaultRequested = vaultRequested;
        }
        public String id() { return id; }
        public String approvalUrl() { return approvalUrl; }
        public String clientMetadataId() { return clientMetadataId; }
        public boolean vaultRequested() { return vaultRequested; }
    }

    public static final class Capture {
        private final String id;
        private final String vaultStatus;
        private final String vaultId;
        private final String customerId;
        private final String payerEmail;
        private final String payerAccountId;
        private Capture(String id, String vaultStatus, String vaultId, String customerId,
                String payerEmail, String payerAccountId) {
            this.id = id;
            this.vaultStatus = vaultStatus;
            this.vaultId = vaultId;
            this.customerId = customerId;
            this.payerEmail = payerEmail;
            this.payerAccountId = payerAccountId;
        }
        public String id() { return id; }
        public String vaultStatus() { return vaultStatus; }
        public String vaultId() { return vaultId; }
        public String customerId() { return customerId; }
        public String payerEmail() { return payerEmail; }
        public String payerAccountId() { return payerAccountId; }
    }
}
