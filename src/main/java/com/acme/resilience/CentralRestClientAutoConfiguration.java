package com.acme.resilience;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Auto-configuracion de la libreria: basta con anadir la dependencia para tener el RestClient central.
 * Los registries de Resilience4j los crea resilience4j-spring-boot3 a partir de resilience4j.*.
 */
@AutoConfiguration(afterName = {
    "org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration",
    "io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration",
    "io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration",
    "io.github.resilience4j.springboot3.ratelimiter.autoconfigure.RateLimiterAutoConfiguration",
    "io.github.resilience4j.springboot3.bulkhead.autoconfigure.BulkheadAutoConfiguration"
})
@EnableConfigurationProperties(OutboundResilienceProperties.class)
public class CentralRestClientAutoConfiguration {

    /**
     * El RestClient existe siempre; si la resiliencia esta desactivada simplemente no lleva interceptor.
     */
    @Bean
    @ConditionalOnMissingBean(name = "centralRestClient")
    public RestClient centralRestClient(RestClient.Builder builder,
                                        ObjectProvider<ResilienceClientHttpRequestInterceptor> resilienceInterceptor) {
        resilienceInterceptor.ifAvailable(builder::requestInterceptor);
        return builder.build();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "app.outbound.resilience", name = "enabled", havingValue = "true",
        matchIfMissing = true)
    static class ResilienceConfiguration {

        @Bean
        @ConditionalOnMissingBean
        ResilienceRouteResolver resilienceRouteResolver(OutboundResilienceProperties properties) {
            return new ResilienceRouteResolver(properties);
        }

        @Bean
        @ConditionalOnMissingBean
        ResolvedPolicyRegistry resolvedPolicyRegistry(OutboundResilienceProperties properties,
                                                      CircuitBreakerRegistry circuitBreakerRegistry,
                                                      RetryRegistry retryRegistry,
                                                      RateLimiterRegistry rateLimiterRegistry,
                                                      BulkheadRegistry bulkheadRegistry) {
            return new ResolvedPolicyRegistry(properties, circuitBreakerRegistry, retryRegistry,
                rateLimiterRegistry, bulkheadRegistry);
        }

        @Bean
        @ConditionalOnMissingBean
        ResilienceClientHttpRequestInterceptor resilienceClientHttpRequestInterceptor(
            ResilienceRouteResolver routeResolver, ResolvedPolicyRegistry policyRegistry) {
            return new ResilienceClientHttpRequestInterceptor(routeResolver, policyRegistry);
        }
    }
}
