package com.disasterrelief.backend.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.disasterrelief.backend.ai.AnalysisDtos.AnalysisResponse;
import com.disasterrelief.backend.audit.AuditLogger;
import com.disasterrelief.backend.common.ApiException;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class AnalysisServiceTest {

    private static final String REPORT = "Village near river is flooded, 300 people stranded. Call 98765 43210 for access.";
    private static final String GOOD = "{\"severity\":\"CRITICAL\",\"summary\":\"Flood.\",\"needs\":[{\"resource\":\"Boats\",\"quantity\":3,\"urgency\":\"CRITICAL\"}],\"uncertainties\":[]}";

    private ModelClient client;
    private AnalysisRepository repo;
    private AnalysisService service;

    @BeforeEach
    void setUp() {
        client = mock(ModelClient.class);
        repo = mock(AnalysisRepository.class);
        when(client.configured()).thenReturn(true);
        when(client.model()).thenReturn("m");
        service = new AnalysisService(client, repo, new AiRateLimiter(10, 3_600_000L), mock(AuditLogger.class));
    }

    private static AnalysisResponse stored(String status, String review) {
        return new AnalysisResponse(9, status, review, null, "m", "t", Map.of(), AnalysisDtos.ADVISORY, null, null);
    }

    @Test
    void successStoresScrubbedTextAndSendsOnlyScrubbedTextToTheModel() {
        when(client.complete(anyString(), anyString())).thenReturn(GOOD);
        when(repo.insert(any(), any(), any(), any(), any(), anyLong())).thenReturn(9L);
        when(repo.find(9)).thenReturn(Optional.of(stored("SUCCEEDED", "UNREVIEWED")));

        assertEquals(9, service.analyze(REPORT, 5).id());

        ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
        verify(client).complete(eq(AnalysisService.SYSTEM_PROMPT), sent.capture());
        assertFalse(sent.getValue().contains("98765"), sent.getValue());
        assertTrue(sent.getValue().contains("[PHONE]"));
        assertTrue(sent.getValue().startsWith("<report>"));
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(repo).insert(text.capture(), eq("m"), eq("SUCCEEDED"), contains("\"severity\":\"CRITICAL\""), isNull(), eq(5L));
        assertFalse(text.getValue().contains("98765"));
    }

    @Test
    void notConfiguredIsStoredAsFailedAndAnswers503WithoutCallingTheModel() {
        when(client.configured()).thenReturn(false);
        ApiException ex = assertThrows(ApiException.class, () -> service.analyze(REPORT, 5));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.status());
        assertEquals("AI_UNAVAILABLE", ex.code());
        verify(repo).insert(any(), eq("m"), eq("FAILED"), isNull(), eq("AI_NOT_CONFIGURED"), eq(5L));
        verify(client, never()).complete(any(), any());
    }

    @Test
    void unusableAnswerIsABadGatewayAndNothingIsSuggested() {
        when(client.complete(anyString(), anyString())).thenReturn("sorry, no JSON here");
        ApiException ex = assertThrows(ApiException.class, () -> service.analyze(REPORT, 5));
        assertEquals(HttpStatus.BAD_GATEWAY, ex.status());
        assertEquals("AI_BAD_OUTPUT", ex.code());
        verify(repo).insert(any(), eq("m"), eq("FAILED"), isNull(), eq("AI_BAD_OUTPUT"), eq(5L));
    }

    @Test
    void modelOutageIs503() {
        when(client.complete(anyString(), anyString())).thenThrow(new ModelUnavailableException("down", null));
        ApiException ex = assertThrows(ApiException.class, () -> service.analyze(REPORT, 5));
        assertEquals("AI_UNAVAILABLE", ex.code());
        verify(repo).insert(any(), eq("m"), eq("FAILED"), isNull(), eq("AI_UNAVAILABLE"), eq(5L));
    }

    @Test
    void textLengthIsChecked() {
        assertEquals("VALIDATION_ERROR", assertThrows(ApiException.class, () -> service.analyze("too short", 5)).code());
        assertEquals("VALIDATION_ERROR", assertThrows(ApiException.class, () -> service.analyze("x".repeat(5001), 5)).code());
        verify(client, never()).complete(any(), any());
    }

    @Test
    void rateLimitAnswers429() {
        service = new AnalysisService(client, repo, new AiRateLimiter(1, 3_600_000L), mock(AuditLogger.class));
        when(client.complete(anyString(), anyString())).thenReturn(GOOD);
        when(repo.insert(any(), any(), any(), any(), any(), anyLong())).thenReturn(9L);
        when(repo.find(9)).thenReturn(Optional.of(stored("SUCCEEDED", "UNREVIEWED")));
        service.analyze(REPORT, 5);
        ApiException ex = assertThrows(ApiException.class, () -> service.analyze(REPORT, 5));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.status());
        assertEquals("RATE_LIMITED", ex.code());
    }

    @Test
    void reviewAcceptsOnceAndValidatesTheDecision() {
        when(repo.find(9)).thenReturn(Optional.of(stored("SUCCEEDED", "UNREVIEWED")));
        when(repo.review(9, "ACCEPTED", 7)).thenReturn(true);
        service.review(9, " accepted ", 7);
        verify(repo).review(9, "ACCEPTED", 7);

        when(repo.review(9, "REJECTED", 7)).thenReturn(false);
        assertEquals("INVALID_STATE", assertThrows(ApiException.class, () -> service.review(9, "REJECTED", 7)).code());
        assertEquals("VALIDATION_ERROR", assertThrows(ApiException.class, () -> service.review(9, "MAYBE", 7)).code());
    }

    @Test
    void unknownAnalysisIsNotFound() {
        when(repo.find(1)).thenReturn(Optional.empty());
        assertEquals("NOT_FOUND", assertThrows(ApiException.class, () -> service.get(1)).code());
    }
}
