package com.acme.resilience;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Contrato de configuracion de la libreria.
 * <ul>
 *   <li>{@code routes}: host + path (+ method) -> nombre logico de politica.</li>
 *   <li>{@code policies}: nombre logico -> nombres de instancias de Resilience4j definidas en {@code resilience4j.*}.</li>
 * </ul>
 */
@Validated
@ConfigurationProperties(prefix = "app.outbound.resilience")
public class OutboundResilienceProperties {

    private boolean enabled = true;

    @NotBlank
    private String defaultPolicy = "defaultPolicy";

    /** Evaluadas en orden: gana la primera que coincide. */
    @Valid
    private List<RouteRule> routes = new ArrayList<>();

    @Valid
    @NotEmpty
    private List<PolicyDefinition> policies = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultPolicy() {
        return defaultPolicy;
    }

    public void setDefaultPolicy(String defaultPolicy) {
        this.defaultPolicy = defaultPolicy;
    }

    public List<RouteRule> getRoutes() {
        return routes;
    }

    public void setRoutes(List<RouteRule> routes) {
        this.routes = routes;
    }

    public List<PolicyDefinition> getPolicies() {
        return policies;
    }

    public void setPolicies(List<PolicyDefinition> policies) {
        this.policies = policies;
    }

    public static class RouteRule {

        @NotBlank
        private String host;

        /** Patron Ant, p. ej. /v1/orders/** */
        @NotBlank
        private String path = "/**";

        /** Opcional. Si no se indica aplica a cualquier metodo. */
        private String method;

        @NotBlank
        private String policy;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public String getPolicy() {
            return policy;
        }

        public void setPolicy(String policy) {
            this.policy = policy;
        }
    }

    public static class PolicyDefinition {

        @NotBlank
        private String name;

        private String circuitBreaker;
        private String retry;
        private String rateLimiter;
        private String bulkhead;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCircuitBreaker() {
            return circuitBreaker;
        }

        public void setCircuitBreaker(String circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
        }

        public String getRetry() {
            return retry;
        }

        public void setRetry(String retry) {
            this.retry = retry;
        }

        public String getRateLimiter() {
            return rateLimiter;
        }

        public void setRateLimiter(String rateLimiter) {
            this.rateLimiter = rateLimiter;
        }

        public String getBulkhead() {
            return bulkhead;
        }

        public void setBulkhead(String bulkhead) {
            this.bulkhead = bulkhead;
        }
    }
}
