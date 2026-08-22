package com.betasafe.app.penance;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small client for the project-owned PayPal order backend. No PayPal secret enters Android. */
public final class PayPalCheckoutClient implements AutoCloseable {
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private final String baseUrl;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public PayPalCheckoutClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void createOrder(String settlementId, int amountCents, Callback<OrderState> callback) {
        worker.execute(() -> {
            try {
                JSONObject body = new JSONObject()
                        .put("settlementId", settlementId)
                        .put("amountCents", amountCents)
                        .put("currency", PenanceManager.CURRENCY);
                JSONObject response = request("POST", "/api/v1/orders", body);
                deliver(callback, parseOrder(response), null);
            } catch (Exception error) {
                deliver(callback, null, safeMessage(error));
            }
        });
    }

    public void getOrder(String orderId, Callback<OrderState> callback) {
        worker.execute(() -> {
            try {
                JSONObject response = request("GET", "/api/v1/orders/" + urlSegment(orderId), null);
                deliver(callback, parseOrder(response), null);
            } catch (Exception error) {
                deliver(callback, null, safeMessage(error));
            }
        });
    }

    public void captureOrder(String orderId, Callback<OrderState> callback) {
        worker.execute(() -> {
            try {
                JSONObject response = request("POST",
                        "/api/v1/orders/" + urlSegment(orderId) + "/capture", new JSONObject());
                deliver(callback, parseOrder(response), null);
            } catch (Exception error) {
                deliver(callback, null, safeMessage(error));
            }
        });
    }

    public void cancelOrder(String orderId, Callback<OrderState> callback) {
        worker.execute(() -> {
            try {
                JSONObject response = request("POST",
                        "/api/v1/orders/" + urlSegment(orderId) + "/cancel", new JSONObject());
                deliver(callback, parseOrder(response), null);
            } catch (Exception error) {
                deliver(callback, null, safeMessage(error));
            }
        });
    }

    private JSONObject request(String method, String path, JSONObject body)
            throws IOException, JSONException {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(12_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setInstanceFollowRedirects(false);
        if (body != null) {
            connection.setDoOutput(true);
            byte[] encoded = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(encoded.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(encoded);
            }
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream();
        byte[] response = readBounded(stream);
        connection.disconnect();
        if (status < 200 || status >= 300) throw new IOException("Backend returned HTTP " + status);
        return new JSONObject(new String(response, StandardCharsets.UTF_8));
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        if (input == null) return new byte[0];
        try (InputStream source = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = source.read(buffer)) >= 0) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) throw new IOException("Backend response is too large");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static OrderState parseOrder(JSONObject value) throws JSONException {
        return new OrderState(value.getString("orderId"), value.getString("settlementId"),
                value.getInt("amountCents"), value.getString("currency"),
                value.getString("status"), value.optString("approvalUrl", ""));
    }

    private <T> void deliver(Callback<T> callback, T value, String error) {
        main.post(() -> callback.onResult(value, error));
    }

    private static String safeMessage(Exception error) {
        if (error instanceof IOException && error.getMessage() != null) {
            String message = error.getMessage();
            if (message.startsWith("Backend returned") || message.startsWith("Backend response")) {
                return message;
            }
        }
        return "Could not reach the payment service.";
    }

    private static String urlSegment(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new IllegalArgumentException("Invalid order identifier");
        }
        return value;
    }

    @Override public void close() {
        worker.shutdownNow();
    }

    public interface Callback<T> {
        void onResult(T value, String error);
    }

    public static final class OrderState {
        private final String orderId;
        private final String settlementId;
        private final int amountCents;
        private final String currency;
        private final String status;
        private final String approvalUrl;

        private OrderState(String orderId, String settlementId, int amountCents,
                String currency, String status, String approvalUrl) {
            this.orderId = orderId;
            this.settlementId = settlementId;
            this.amountCents = amountCents;
            this.currency = currency;
            this.status = status;
            this.approvalUrl = approvalUrl;
        }

        public String getOrderId() { return orderId; }
        public String getSettlementId() { return settlementId; }
        public int getAmountCents() { return amountCents; }
        public String getCurrency() { return currency; }
        public String getStatus() { return status; }
        public String getApprovalUrl() { return approvalUrl; }
        public boolean isCompleted() { return "COMPLETED".equals(status); }
    }
}
