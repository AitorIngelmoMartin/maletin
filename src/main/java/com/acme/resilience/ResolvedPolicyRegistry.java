package com.acme.resilience;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.core.functions.CheckedSupplier;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.HashMap;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * Se construye UNA vez al arrancar: para cada politica obtiene de los registries de Resilience4j
 * (creados y configurados por Spring a partir de resilience4j.*) las instancias referenciadas.
 * En cada request solo se hace un lookup en un Map inmutable.
 */
public class ResolvedPolicyRegistry {

    private final Map<String, PolicyComponents> byPolicyName;

    public ResolvedPolicyRegistry(OutboundResilienceProperties properties,
                                  CircuitBreakerRegistry circuitBreakerRegistry,
                                  RetryRegistry retryRegistry,
                                  RateLimiterRegistry rateLimiterRegistry,
                                  BulkheadRegistry bulkheadRegistry) {

        Map<String, PolicyComponents> result = new HashMap<>();
        for (OutboundResilienceProperties.PolicyDefinition def : properties.getPolicies()) {
            PolicyComponents components = new PolicyComponents(
                hasText(def.getCircuitBreaker()) ? circuitBreakerRegistry.circuitBreaker(def.getCircuitBreaker()) : null,
                hasText(def.getRetry()) ? retryRegistry.retry(def.getRetry()) : null,
                hasText(def.getRateLimiter()) ? rateLimiterRegistry.rateLimiter(def.getRateLimiter()) : null,
                hasText(def.getBulkhead()) ? bulkheadRegistry.bulkhead(def.getBulkhead()) : null);

            if (result.put(def.getName(), components) != null) {
                throw new IllegalStateException("Politica duplicada: '%s'".formatted(def.getName()));
            }
        }
        this.byPolicyName = Map.copyOf(result);

        validateReferences(properties);
    }

    public PolicyComponents getByPolicyName(String policyName) {
        PolicyComponents components = byPolicyName.get(policyName);
        if (components == null) {
            throw new IllegalStateException("No existe la politica '%s'".formatted(policyName));
        }
        return components;
    }

    private void validateReferences(OutboundResilienceProperties properties) {
        if (!byPolicyName.containsKey(properties.getDefaultPolicy())) {
            throw new IllegalStateException(
                "default-policy '%s' no esta definida en app.outbound.resilience.policies"
                    .formatted(properties.getDefaultPolicy()));
        }
        for (OutboundResilienceProperties.RouteRule rule : properties.getRoutes()) {
            if (!byPolicyName.containsKey(rule.getPolicy())) {
                throw new IllegalStateException(
                    "La ruta %s%s referencia la politica inexistente '%s'"
                        .formatted(rule.getHost(), rule.getPath(), rule.getPolicy()));
            }
        }
    }

    private static boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    /**
     * Instancias de Resilience4j de una politica. Cualquiera puede ser null (patron no aplicado).
     */
    public record PolicyComponents(CircuitBreaker circuitBreaker,
                                   Retry retry,
                                   RateLimiter rateLimiter,
                                   Bulkhead bulkhead) {

        /**
         * Aplica los patrones con las utilidades estaticas de la propia libreria.
         * Orden resultante (exterior -> interior): CircuitBreaker -> Retry -> RateLimiter -> Bulkhead -> HTTP.
         */
        public <T> CheckedSupplier<T> decorate(CheckedSupplier<T> call) {
            CheckedSupplier<T> decorated = call;
            if (bulkhead != null) {
                decorated = Bulkhead.decorateCheckedSupplier(bulkhead, decorated);
            }
            if (rateLimiter != null) {
                decorated = RateLimiter.decorateCheckedSupplier(rateLimiter, decorated);
            }
            if (retry != null) {
                decorated = Retry.decorateCheckedSupplier(retry, decorated);
            }
            if (circuitBreaker != null) {
                decorated = CircuitBreaker.decorateCheckedSupplier(circuitBreaker, decorated);
            }
            return decorated;
        }
    }
}
