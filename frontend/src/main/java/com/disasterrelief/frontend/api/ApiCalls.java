package com.disasterrelief.frontend.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Runs a backend call and turns every failure into a {@link BackendException}; views never see Spring exceptions. */
final class ApiCalls {

    private ApiCalls() {
    }

    static <T> T execute(Supplier<T> call) {
        try {
            return call.get();
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        } catch (ResourceAccessException ex) {
            throw new BackendException(0, null, "The backend is not reachable.", null);
        } catch (RestClientException ex) {
            throw new BackendException(502, "BAD_RESPONSE", "The backend returned an unexpected response.", null);
        }
    }

    /** Reads the backend's problem+json body: {status, detail, code, errors[{field, message}]}. */
    static BackendException translate(RestClientResponseException ex) {
        String code = null;
        String message = "The request failed (HTTP " + ex.getStatusCode().value() + ").";
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        try {
            Map<String, Object> body = ex.getResponseBodyAs(new ParameterizedTypeReference<Map<String, Object>>() { });
            if (body != null) {
                if (body.get("code") != null) {
                    code = body.get("code").toString();
                }
                if (body.get("detail") != null) {
                    message = body.get("detail").toString();
                }
                if (body.get("errors") instanceof List<?> errors) {
                    for (Object item : errors) {
                        if (item instanceof Map<?, ?> e && e.get("field") != null && e.get("message") != null) {
                            fieldErrors.putIfAbsent(e.get("field").toString(), e.get("message").toString());
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Body was not JSON: keep the generic message.
        }
        return new BackendException(ex.getStatusCode().value(), code, message, fieldErrors);
    }
}
