package com.acme.resilience;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

/**
 * Traduce una request saliente (host + path + method) a un nombre logico de politica.
 * Primera regla que coincide gana; si ninguna coincide se usa la politica por defecto.
 */
public class ResilienceRouteResolver {

    private final List<OutboundResilienceProperties.RouteRule> rules;
    private final String defaultPolicy;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public ResilienceRouteResolver(OutboundResilienceProperties properties) {
        this.rules = List.copyOf(properties.getRoutes());
        this.defaultPolicy = properties.getDefaultPolicy();
    }

    public String resolvePolicyName(URI uri, HttpMethod method) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        String path = StringUtils.hasText(uri.getPath()) ? uri.getPath() : "/";

        for (OutboundResilienceProperties.RouteRule rule : rules) {
            if (!host.equalsIgnoreCase(rule.getHost())) {
                continue;
            }
            if (!pathMatcher.match(rule.getPath(), path)) {
                continue;
            }
            if (StringUtils.hasText(rule.getMethod()) && !rule.getMethod().equalsIgnoreCase(method.name())) {
                continue;
            }
            return rule.getPolicy();
        }
        return defaultPolicy;
    }
}
