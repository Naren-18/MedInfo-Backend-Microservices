package com.medinfo.audit.service;


import com.medinfo.audit.entity.AuditLog;
import com.medinfo.audit.repository.AuditRepository;
import com.medinfo.common.enums.AccessMethod;
import com.medinfo.common.events.AuditLogEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditRepository auditRepository;

    @InjectMocks
    private AuditService auditService;

    private AuditLogEvent buildEvent(UUID eventId) {
        return AuditLogEvent.builder()
                .userId(1L)
                .ipAddress("127.0.0.1")
                .userAgent("Postman")
                .accessMethod(AccessMethod.URL)
                .eventId(eventId)
                .build();
    }

    @Test
    void createAuditLog_ShouldSaveAuditLogSuccessfully() {
        UUID eventId = UUID.randomUUID();
        AuditLogEvent event = buildEvent(eventId);

        AuditLog savedAuditLog = AuditLog.builder()
                .id(1L)
                .userId(1L)
                .ipAddress("127.0.0.1")
                .userAgent("Postman")
                .accessMethod(AccessMethod.URL)
                .eventId(eventId)
                .build();

        when(auditRepository.existsByEventId(eventId)).thenReturn(false);
        when(auditRepository.save(any(AuditLog.class))).thenReturn(savedAuditLog);

        AuditLog result = auditService.createAuditLog(event);

        assertEquals(1L, result.getId());
        assertEquals(1L, result.getUserId());
        assertEquals("127.0.0.1", result.getIpAddress());
        assertEquals("Postman", result.getUserAgent());
        assertEquals(AccessMethod.URL, result.getAccessMethod());
        assertEquals(eventId, result.getEventId());

        verify(auditRepository).save(any(AuditLog.class));
    }

    @Test
    void createAuditLog_ShouldIgnoreDuplicateEvent() {
        UUID eventId = UUID.randomUUID();
        AuditLogEvent event = buildEvent(eventId);

        when(auditRepository.existsByEventId(eventId)).thenReturn(true);

        AuditLog result = auditService.createAuditLog(event);

        assertNull(result);
        verify(auditRepository, never()).save(any(AuditLog.class));
    }

    @Test
    void createAuditLog_ShouldThrowException_WhenRepositoryFails() {
        UUID eventId = UUID.randomUUID();
        AuditLogEvent event = buildEvent(eventId);

        when(auditRepository.existsByEventId(eventId)).thenReturn(false);
        when(auditRepository.save(any(AuditLog.class)))
                .thenThrow(new RuntimeException("Database Error"));

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> auditService.createAuditLog(event)
        );

        assertEquals("Database Error", exception.getMessage());
        verify(auditRepository).save(any(AuditLog.class));
    }
}
