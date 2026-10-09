package com.example.checkout;

import com.stripe.Stripe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StripeConfig {
    public StripeConfig(@Value("${STRIPE_SECRET_KEY}") String secretKey) {
        // The secret key stays on the server. Never ship it to the browser.
        Stripe.apiKey = secretKey;
    }
}
