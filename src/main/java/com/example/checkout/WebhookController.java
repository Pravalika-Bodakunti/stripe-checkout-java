package com.example.checkout;

import com.google.gson.JsonSyntaxException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebhookController {

    private final WebhookService webhookService;
    private final String webhookSecret;

    public WebhookController(WebhookService webhookService,
                             @Value("${STRIPE_WEBHOOK_SECRET}") String webhookSecret) {
        this.webhookService = webhookService;
        this.webhookSecret = webhookSecret;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handle(
            @RequestBody String payload, // raw body as a String: the signature is computed over it
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {

        if (signature == null) {
            return ResponseEntity.badRequest().body("Missing signature");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException | JsonSyntaxException e) {
            return ResponseEntity.badRequest().body("Invalid payload or signature");
        }

        return ResponseEntity.ok(webhookService.process(event));
    }
}
