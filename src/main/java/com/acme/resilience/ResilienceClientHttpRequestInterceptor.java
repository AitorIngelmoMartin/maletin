package com.acme.resilience;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.HttpServerErrorException;

/**
 * Punto unico de aplicacion: intercepta TODAS las llamadas del RestClient central,
 * resuelve la politica por host + path + method y ejecuta la llamada protegida.
 * <p>
 * Debe registrarse como ULTIMO interceptor del RestClient: Retry vuelve a invocar
 * {@code execution.execute(...)}, lo que crea una request HTTP nueva.
 */
public class ResilienceClientHttpRequestInterceptor implements ClientHttpRequestInterceptor {

    private final ResilienceRouteResolver routeResolver;
    private final ResolvedPolicyRegistry policyRegistry;

    public ResilienceClientHttpRequestInterceptor(ResilienceRouteResolver routeResolver,
                                                  ResolvedPolicyRegistry policyRegistry) {
        this.routeResolver = routeResolver;
        this.policyRegistry = policyRegistry;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
        throws IOException {

        String policyName = routeResolver.resolvePolicyName(request.getURI(), request.getMethod());
        ResolvedPolicyRegistry.PolicyComponents policy = policyRegistry.getByPolicyName(policyName);

        try {
            return policy.<ClientHttpResponse>decorate(() -> executeAndFailOn5xx(request, body, execution)).get();
        } catch (IOException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IOException(t);
        }
    }

    /**
     * Un interceptor recibe la respuesta 5xx como respuesta "correcta" (no hay excepcion);
     * la convertimos en excepcion para que CircuitBreaker y Retry la cuenten como fallo.
     */
    private static ClientHttpResponse executeAndFailOn5xx(HttpRequest request, byte[] body,
                                                          ClientHttpRequestExecution execution) throws IOException {
        ClientHttpResponse response = execution.execute(request, body);
        HttpStatusCode status = response.getStatusCode();
        if (!status.is5xxServerError()) {
            return response;
        }
        try (response) {
            byte[] responseBody = StreamUtils.copyToByteArray(response.getBody());
            throw HttpServerErrorException.create(status, response.getStatusText(), response.getHeaders(),
                responseBody, StandardCharsets.UTF_8);
        }
    }
}
