package com.example.checkout;

import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Kept separate from the controller on purpose: Spring's @Transactional only works
 * when the method is called from ANOTHER bean (it wraps this class in a proxy).
 */
@Service
public class WebhookService {

    private record StoredAmount(long amount, String currency) {}

    private final JdbcTemplate db;

    public WebhookService(JdbcTemplate db) {
        this.db = db;
    }

    @Transactional // event record + order update commit together, or not at all
    public String process(Event event) {
        try {
            db.update("INSERT INTO processed_events (id) VALUES (?)", event.getId());
        } catch (DuplicateKeyException e) {
            return "duplicate ignored";
        }

        return switch (event.getType()) {
            case "payment_intent.succeeded" -> markPaid(readPaymentIntent(event));
            case "payment_intent.payment_failed" -> markFailed(readPaymentIntent(event));
            default -> "ignored " + event.getType();
        };
    }

    private String markPaid(PaymentIntent intent) {
        List<StoredAmount> rows = db.query(
                "SELECT amount, currency FROM orders WHERE payment_intent_id = ?",
                (rs, n) -> new StoredAmount(rs.getLong("amount"), rs.getString("currency")),
                intent.getId());
        if (rows.isEmpty()) {
            return "unknown intent";
        }

        StoredAmount order = rows.get(0);
        if (intent.getAmountReceived() == null
                || intent.getAmountReceived() != order.amount()
                || !order.currency().equals(intent.getCurrency())) {
            // Throwing rolls back the transaction and returns 500, so Stripe will retry.
            throw new IllegalStateException("Amount mismatch for " + intent.getId());
        }

        db.update("UPDATE orders SET status = 'paid' WHERE payment_intent_id = ?", intent.getId());
        return "ok";
    }

    private String markFailed(PaymentIntent intent) {
        // Don't let a late failure event overwrite an order that already succeeded.
        db.update(
                "UPDATE orders SET status = 'failed' WHERE payment_intent_id = ? AND status <> 'paid'",
                intent.getId());
        return "ok";
    }

    /**
     * Java-specific gotcha: stripe-java is pinned to one Stripe API version. If your account's
     * API version differs, getObject() comes back EMPTY. deserializeUnsafe() reads it anyway.
     */
    private PaymentIntent readPaymentIntent(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        StripeObject obj = deserializer.getObject().orElse(null);
        if (obj == null) {
            try {
                obj = deserializer.deserializeUnsafe();
            } catch (EventDataObjectDeserializationException e) {
                throw new IllegalStateException("Could not read event data", e);
            }
        }
        return (PaymentIntent) obj;
    }
}
