package com.nexa.integration;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.function.Supplier;

/** Shared HTTP setup and error mapping for provider clients. Response bodies are never surfaced to users. */
public final class ProviderHttp {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private ProviderHttp() {
    }

    /** PRD section 50: external calls should complete within 10 seconds. */
    public static RestClient client(RestClient.Builder builder, String baseUrl) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
        factory.setReadTimeout(TIMEOUT);
        return builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public static <T> T call(String providerName, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 401) {
                throw new IntegrationException(IntegrationException.Code.AUTH_FAILED,
                        providerName + " rejected Nexa's credentials. An admin needs to reconnect " + providerName + ".", e);
            }
            if (status == 403) {
                throw new IntegrationException(IntegrationException.Code.REJECTED,
                        providerName + " denied permission for this action. Check that the connected account can do it.", e);
            }
            if (status == 404) {
                throw new IntegrationException(IntegrationException.Code.REJECTED,
                        providerName + " couldn't find the configured project or channel. Update the settings in Integrations.", e);
            }
            if (status == 429) {
                throw new IntegrationException(IntegrationException.Code.RATE_LIMITED, providerName + " is rate limiting requests. Nexa will retry.", e);
            }
            if (status >= 500) {
                throw new IntegrationException(IntegrationException.Code.UNAVAILABLE, providerName + " is temporarily unavailable. Nexa will retry.", e);
            }
            throw new IntegrationException(IntegrationException.Code.REJECTED, providerName + " rejected the request (HTTP " + status + ").", e);
        } catch (ResourceAccessException e) {
            throw new IntegrationException(IntegrationException.Code.UNAVAILABLE, "Couldn't reach " + providerName + ". Nexa will retry.", e);
        }
    }
}
