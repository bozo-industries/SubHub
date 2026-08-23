package com.betasafe.app.penance;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.betasafe.app.BuildConfig;
import com.betasafe.app.R;
import com.betasafe.app.databinding.ActivityPenanceBinding;
import com.betasafe.app.util.SubHubNavigation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/** Styled local ledger and explicit PayPal settlement surface. */
public final class PenanceActivity extends AppCompatActivity {
    private ActivityPenanceBinding binding;
    private PenanceManager manager;
    private PayPalCheckoutClient client;
    private final Handler timer = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            render();
            timer.postDelayed(this, 1000L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPenanceBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        manager = new PenanceManager(this);
        SubHubNavigation.bind(this, binding.getRoot(), SubHubNavigation.Screen.MONEY);
        populateRules();

        binding.buttonBack.setOnClickListener(view -> finish());
        binding.buttonSaveRules.setOnClickListener(view -> saveRules());
        binding.buttonSettle.setOnClickListener(view -> beginCheckout());
        binding.buttonResumeCheckout.setOnClickListener(view -> openApprovalUrl());
        binding.buttonCancelCheckout.setOnClickListener(view -> cancelCheckout());
        binding.buttonForgiveLatest.setOnClickListener(view -> forgiveLatest());
        binding.buttonClearUnpaid.setOnClickListener(view -> confirmClearUnpaid());
        binding.buttonTestStrike.setVisibility(BuildConfig.DEBUG ? View.VISIBLE : View.GONE);
        binding.buttonTestStrike.setOnClickListener(view -> {
            manager.recordStrikes(1, System.currentTimeMillis());
            render();
        });
        handleReturn(getIntent());
        render();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleReturn(intent);
    }

    @Override protected void onResume() {
        super.onResume();
        timer.removeCallbacks(tick);
        timer.post(tick);
    }

    @Override protected void onPause() {
        timer.removeCallbacks(tick);
        super.onPause();
    }

    private void populateRules() {
        binding.ledgerEnabled.setChecked(manager.isEnabled());
        binding.strikeAmount.setText(decimalEuros(manager.getStrikeCents()));
        binding.dailyCap.setText(decimalEuros(manager.getDailyCapCents()));
        binding.weeklyCap.setText(decimalEuros(manager.getWeeklyCapCents()));
        binding.mercyMinutes.setText(String.valueOf(manager.getMercyMinutes()));
    }

    private void saveRules() {
        boolean enabled = binding.ledgerEnabled.isChecked();
        if (enabled && !binding.paymentConsent.isChecked()) {
            toast(R.string.penance_consent_required);
            return;
        }
        Integer strike = parseEuros(binding.strikeAmount.getText().toString());
        Integer daily = parseEuros(binding.dailyCap.getText().toString());
        Integer weekly = parseEuros(binding.weeklyCap.getText().toString());
        Integer mercy = parseInteger(binding.mercyMinutes.getText().toString());
        if (strike == null || daily == null || weekly == null || mercy == null
                || strike <= 0 || daily < strike || weekly < daily
                || strike > PenancePolicy.MAX_STRIKE_CENTS
                || daily > PenancePolicy.MAX_DAILY_CENTS
                || weekly > PenancePolicy.MAX_WEEKLY_CENTS
                || mercy < 0 || mercy > PenancePolicy.MAX_MERCY_MINUTES) {
            toast(R.string.penance_rules_invalid);
            return;
        }
        manager.configure(enabled, strike, daily, weekly, mercy);
        binding.paymentConsent.setChecked(false);
        closeClient();
        toast(R.string.penance_rules_saved);
        render();
    }

    private void beginCheckout() {
        if (!validBackend(manager.getBackendUrl())) {
            toast(R.string.penance_backend_required);
            return;
        }
        PenanceManager.Settlement settlement = manager.beginSettlement(System.currentTimeMillis());
        if (settlement == null) {
            toast(R.string.penance_no_due);
            return;
        }
        setBusy(true);
        paymentClient().createOrder(settlement.getId(), settlement.getAmountCents(),
                (order, error) -> {
                    setBusy(false);
                    if (error != null || order == null) {
                        manager.cancelSettlement(settlement.getId());
                        toast(getString(R.string.penance_checkout_failed,
                                error == null ? "Unknown error" : error));
                        render();
                        return;
                    }
                    if (!settlement.getId().equals(order.getSettlementId())
                            || settlement.getAmountCents() != order.getAmountCents()
                            || !PenanceManager.CURRENCY.equals(order.getCurrency())) {
                        manager.cancelSettlement(settlement.getId());
                        toast(R.string.penance_payment_mismatch);
                        render();
                        return;
                    }
                    manager.bindOrder(settlement.getId(), order.getOrderId(),
                            order.getApprovalUrl());
                    render();
                    openApprovalUrl();
                });
    }

    private void openApprovalUrl() {
        String approvalUrl = manager.getActiveApprovalUrl();
        if (!validExternalUrl(approvalUrl)) {
            toast(R.string.penance_payment_pending);
            return;
        }
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(approvalUrl)));
    }

    private void handleReturn(Intent intent) {
        Uri data = intent == null ? null : intent.getData();
        if (data == null || !"betasafe".equals(data.getScheme())
                || !"paypal".equals(data.getHost())) return;
        String orderId = data.getQueryParameter("orderId");
        if (orderId == null || !orderId.equals(manager.getActiveOrderId())) return;
        if ("/cancel".equals(data.getPath())) {
            cancelCheckout();
        } else if ("/result".equals(data.getPath())) {
            captureApprovedOrder(orderId);
        }
        intent.setData(null);
    }

    private void captureApprovedOrder(String orderId) {
        String expectedSettlement = manager.getActiveSettlementId();
        if (expectedSettlement.isEmpty()) return;
        setBusy(true);
        paymentClient().captureOrder(orderId, (order, error) -> {
            setBusy(false);
            if (error != null || order == null) {
                toast(getString(R.string.penance_checkout_failed,
                        error == null ? "Unknown error" : error));
                render();
                return;
            }
            if (!order.isCompleted()) {
                toast(R.string.penance_payment_pending);
                render();
                return;
            }
            if (!expectedSettlement.equals(order.getSettlementId())
                    || !PenanceManager.CURRENCY.equals(order.getCurrency())
                    || !manager.completeSettlement(order.getSettlementId(), order.getAmountCents())) {
                toast(R.string.penance_payment_mismatch);
                render();
                return;
            }
            toast(R.string.penance_payment_complete);
            render();
        });
    }

    private void cancelCheckout() {
        String settlementId = manager.getActiveSettlementId();
        String orderId = manager.getActiveOrderId();
        if (!orderId.isEmpty() && validBackend(manager.getBackendUrl())) {
            paymentClient().cancelOrder(orderId, (ignored, error) -> { });
        }
        manager.cancelSettlement(settlementId);
        toast(R.string.penance_cancelled);
        render();
    }

    private void forgiveLatest() {
        if (manager.forgiveLatestInMercy(System.currentTimeMillis())) {
            toast(R.string.penance_forgiven);
        } else {
            toast(R.string.penance_none_in_mercy);
        }
        render();
    }

    private void confirmClearUnpaid() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.penance_clear_title)
                .setMessage(R.string.penance_clear_body)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.penance_clear_now, (dialog, which) -> {
                    String orderId = manager.getActiveOrderId();
                    if (!orderId.isEmpty() && validBackend(manager.getBackendUrl())) {
                        paymentClient().cancelOrder(orderId, (ignored, error) -> { });
                    }
                    manager.forgiveAllUnpaid();
                    toast(R.string.penance_cleared);
                    render();
                }).show();
    }

    private void render() {
        if (binding == null) return;
        long now = System.currentTimeMillis();
        PenanceSnapshot snapshot = manager.snapshot(now);
        binding.dueAmount.setText(PenanceManager.formatMoney(snapshot.getDueCents()));
        binding.mercyAmount.setText(PenanceManager.formatMoney(snapshot.getMercyCents()));
        binding.checkoutAmount.setText(PenanceManager.formatMoney(snapshot.getCheckoutCents()));
        binding.paidAmount.setText(PenanceManager.formatMoney(snapshot.getPaidCents()));
        boolean checkout = snapshot.getCheckoutCents() > 0;
        boolean paymentAvailable = validBackend(manager.getBackendUrl());
        binding.paymentAvailability.setText(paymentAvailable
                ? R.string.penance_payment_ready : R.string.penance_payment_unavailable);
        binding.buttonSettle.setEnabled(paymentAvailable && snapshot.getDueCents() > 0 && !checkout);
        binding.buttonResumeCheckout.setVisibility(
                checkout && !manager.getActiveApprovalUrl().isEmpty() ? View.VISIBLE : View.GONE);
        binding.buttonCancelCheckout.setVisibility(checkout ? View.VISIBLE : View.GONE);
        if (checkout) binding.paymentStatus.setText(R.string.penance_payment_pending);
        else binding.paymentStatus.setText("");
        renderHistory(snapshot, now);
    }

    private void renderHistory(PenanceSnapshot snapshot, long nowMillis) {
        if (snapshot.getEvents().isEmpty()) {
            binding.history.setText(R.string.penance_history_empty);
            return;
        }
        StringBuilder text = new StringBuilder();
        DateFormat date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        int count = 0;
        for (PenanceEvent event : snapshot.getEvents()) {
            if (count++ >= 12) break;
            String status;
            if (event.isInMercy(nowMillis)) status = getString(R.string.penance_status_mercy);
            else if (event.getStatus() == PenanceEvent.Status.OPEN) status = getString(R.string.penance_status_open);
            else if (event.getStatus() == PenanceEvent.Status.CHECKOUT) status = getString(R.string.penance_status_checkout);
            else if (event.getStatus() == PenanceEvent.Status.PAID) status = getString(R.string.penance_status_paid);
            else status = getString(R.string.penance_status_forgiven);
            if (text.length() > 0) text.append('\n');
            text.append(getString(R.string.penance_history_item,
                    date.format(new Date(event.getCreatedAtMillis())), event.getStrikeCount(),
                    PenanceManager.formatMoney(event.getAmountCents()), status));
        }
        binding.history.setText(text.toString());
    }

    private PayPalCheckoutClient paymentClient() {
        if (client == null) client = new PayPalCheckoutClient(manager.getBackendUrl());
        return client;
    }

    private void setBusy(boolean busy) {
        if (binding == null) return;
        binding.buttonSettle.setEnabled(!busy);
        binding.buttonResumeCheckout.setEnabled(!busy);
        binding.buttonCancelCheckout.setEnabled(!busy);
        binding.paymentStatus.setText(busy ? R.string.penance_checking : R.string.penance_payment_pending);
    }

    private static boolean validBackend(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null) return false;
            if ("https".equalsIgnoreCase(uri.getScheme())) return true;
            return BuildConfig.DEBUG && "http".equalsIgnoreCase(uri.getScheme())
                    && ("10.0.2.2".equals(uri.getHost())
                    || "127.0.0.1".equals(uri.getHost())
                    || "localhost".equalsIgnoreCase(uri.getHost()));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean validExternalUrl(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            return host != null && "https".equalsIgnoreCase(uri.getScheme())
                    && ("paypal.com".equalsIgnoreCase(host)
                    || host.toLowerCase(Locale.ROOT).endsWith(".paypal.com"));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static Integer parseEuros(String value) {
        try {
            BigDecimal euros = new BigDecimal(value.trim().replace(',', '.'));
            return euros.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).intValueExact();
        } catch (ArithmeticException | NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer parseInteger(String value) {
        try { return Integer.parseInt(value.trim()); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static String decimalEuros(int cents) {
        return String.format(Locale.ROOT, "%.2f", cents / 100.0);
    }

    private void toast(int resource) {
        Toast.makeText(this, resource, Toast.LENGTH_SHORT).show();
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private void closeClient() {
        if (client != null) client.close();
        client = null;
    }

    @Override protected void onDestroy() {
        timer.removeCallbacks(tick);
        closeClient();
        binding = null;
        super.onDestroy();
    }
}
