package com.example.checkout;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class OrderController {

    /** What the browser is allowed to see about an order. Jackson turns this into JSON. */
    public record OrderView(String id, long amount, String currency, String status) {}

    private final JdbcTemplate db;
    private final String publishableKey;

    public OrderController(JdbcTemplate db, @Value("${STRIPE_PUBLISHABLE_KEY}") String publishableKey) {
        this.db = db;
        this.publishableKey = publishableKey;
    }

    @GetMapping("/config")
    public Map<String, String> config() {
        return Map.of("publishableKey", publishableKey);
    }

    @PostMapping("/orders")
    public Map<String, String> createOrder() throws StripeException {
        String orderId = UUID.randomUUID().toString();
        long amount = 2500L; // The SERVER decides the price. Never accept an amount from the client.

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amount)
                .setCurrency("usd")
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build())
                .putMetadata("order_id", orderId)
                .build();

        // A retried request returns the same PaymentIntent instead of creating a second one.
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey("order-" + orderId)
                .build();

        PaymentIntent intent = PaymentIntent.create(params, options);

        db.update(
                "INSERT INTO orders (id, amount, currency, status, payment_intent_id) VALUES (?, ?, ?, ?, ?)",
                orderId, amount, "usd", "pending", intent.getId());

        return Map.of("orderId", orderId, "clientSecret", intent.getClientSecret());
    }

    @GetMapping("/orders/{id}")
    public OrderView getOrder(@PathVariable String id) {
        List<OrderView> rows = db.query(
                "SELECT id, amount, currency, status FROM orders WHERE id = ?",
                (rs, n) -> new OrderView(
                        rs.getString("id"), rs.getLong("amount"),
                        rs.getString("currency"), rs.getString("status")),
                id);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        return rows.get(0);
    }
}
