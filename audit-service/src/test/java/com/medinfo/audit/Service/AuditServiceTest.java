package com.medinfo.audit.Service;


import com.medinfo.audit.DTO.CreateAuditLogRequestDTO;
import com.medinfo.audit.Entity.AuditLog;
import com.medinfo.audit.Enum.AccessMethod;
import com.medinfo.audit.Repository.AuditRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class AuditServiceTest {

    @Mock
    private AuditRepository auditRepository;

    @InjectMocks
    private AuditService auditService;

    @Test
    void createAuditLog_ShouldSaveAuditLogSuccessfully() {

        // Arrange
        CreateAuditLogRequestDTO request = CreateAuditLogRequestDTO.builder()
                .userId(1L)
                .ipAddress("127.0.0.1")
                .userAgent("Postman")
                .accessMethod(AccessMethod.URL)
                .build();

        AuditLog savedAuditLog = AuditLog.builder()
                .id(1L)
                .userId(1L)
                .ipAddress("127.0.0.1")
                .userAgent("Postman")
                .accessMethod(AccessMethod.URL)
                .build();

        when(auditRepository.save(any(AuditLog.class)))
                .thenReturn(savedAuditLog);

        // Act
        AuditLog result = auditService.createAuditLog(request);

        // Assert
        assertEquals(1L, result.getId());
        assertEquals(1L, result.getUserId());
        assertEquals("127.0.0.1", result.getIpAddress());
        assertEquals("Postman", result.getUserAgent());
        assertEquals(AccessMethod.URL, result.getAccessMethod());

        // Verify
        verify(auditRepository).save(any(AuditLog.class));
    }

    @Test
    void createAuditLog_ShouldThrowException_WhenRepositoryFails() {

        // Arrange
        CreateAuditLogRequestDTO request = CreateAuditLogRequestDTO.builder()
                .userId(1L)
                .ipAddress("127.0.0.1")
                .userAgent("Postman")
                .accessMethod(AccessMethod.URL)
                .build();

        when(auditRepository.save(any(AuditLog.class)))
                .thenThrow(new RuntimeException("Database Error"));

        // Act & Assert
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> auditService.createAuditLog(request)
        );

        assertEquals("Database Error", exception.getMessage());

        // Verify
        verify(auditRepository).save(any(AuditLog.class));
    }
}
