package com.disasterrelief.frontend.api;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * One generic client for the Day 5-10 endpoints. Responses are read as plain maps and lists, so a screen only
 * names the fields it shows. Every failure becomes a {@link BackendException} (see {@link ApiCalls}).
 */
@Service
public class Api {

    /** One page of a paged list: {content, page, totalPages, totalElements}. */
    public record Page(List<Map<String, Object>> content, int page, int totalPages, long total) {
        public static Page of(List<Map<String, Object>> rows) {
            return new Page(rows, 0, 1, rows.size());
        }
    }

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST = new ParameterizedTypeReference<>() { };

    private final RestClient rest;

    public Api(RestClient backendRestClient) {
        this.rest = backendRestClient;
    }

    /** Query pairs: key, value, key, value ... Blank and null values are left out. */
    private static URI uri(UriBuilder builder, String path, Object[] pairs) {
        builder.path(path);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            Object value = pairs[i + 1];
            if (value != null && !value.toString().isBlank()) {
                builder.queryParam(pairs[i].toString(), value);
            }
        }
        return builder.build();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    public Map<String, Object> get(String token, String path, Object... pairs) {
        Map<String, Object> body = ApiCalls.execute(() -> rest.get().uri(b -> uri(b, path, pairs))
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).retrieve().body(MAP));
        return body == null ? new LinkedHashMap<>() : body;
    }

    public List<Map<String, Object>> list(String token, String path, Object... pairs) {
        List<Map<String, Object>> body = ApiCalls.execute(() -> rest.get().uri(b -> uri(b, path, pairs))
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).retrieve().body(LIST));
        return body == null ? new ArrayList<>() : body;
    }

    public Page page(String token, String path, int page, int size, Object... pairs) {
        Object[] all = new Object[pairs.length + 4];
        System.arraycopy(pairs, 0, all, 0, pairs.length);
        all[pairs.length] = "page";
        all[pairs.length + 1] = page;
        all[pairs.length + 2] = "size";
        all[pairs.length + 3] = size;
        Map<String, Object> body = get(token, path, all);
        List<Map<String, Object>> rows = body.get("content") instanceof List<?> raw ? cast(raw) : new ArrayList<>();
        return new Page(rows, num(body.get("page")), num(body.get("totalPages")), num(body.get("totalElements")));
    }

    public Map<String, Object> post(String token, String path, Map<String, Object> payload) {
        Map<String, Object> body = ApiCalls.execute(() -> rest.post().uri(path)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .body(payload == null ? Map.of() : payload).retrieve().body(MAP));
        return body == null ? new LinkedHashMap<>() : body;
    }

    public Map<String, Object> patch(String token, String path, Map<String, Object> payload) {
        Map<String, Object> body = ApiCalls.execute(() -> rest.patch().uri(path)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .body(payload == null ? Map.of() : payload).retrieve().body(MAP));
        return body == null ? new LinkedHashMap<>() : body;
    }

    public byte[] bytes(String token, String path, Object... pairs) {
        return ApiCalls.execute(() -> rest.get().uri(b -> uri(b, path, pairs))
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).retrieve().body(byte[].class));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> cast(List<?> raw) {
        return (List<Map<String, Object>>) raw;
    }

    private static int num(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }
}
