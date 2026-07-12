package com.medinfo.medical.Service;

import com.medinfo.medical.Client.AuthClient;
import com.medinfo.medical.DTO.EmergencyProfileResponseDTO;
import com.medinfo.medical.DTO.UserBasicResponseDTO;
import com.medinfo.medical.Entity.EmergencyContacts;
import com.medinfo.medical.Entity.MedicalProfile;
import com.medinfo.medical.Exception.ResourceNotFoundException;
import com.medinfo.medical.Exception.ServiceUnavailableException;
import com.medinfo.medical.Producer.AuditEventProducer;
import com.medinfo.medical.Repository.EmergencyContactsRepository;
import com.medinfo.medical.Repository.MedicalProfileRepository;
import com.medinfo.medical.cache.EmergencyProfileCacheService;
import feign.Request;
import feign.RequestTemplate;
import feign.RetryableException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmergencyServiceTest {

    @Mock
    private EmergencyContactsRepository emergencyContactsRepository;

    @Mock
    private MedicalProfileRepository medicalProfileRepository;

    @Mock
    private AuditEventProducer auditEventProducer;

    @Mock
    private EmergencyProfileCacheService cacheService;

    @Mock
    private AuthClient authClient;

    @InjectMocks
    private EmergencyService emergencyService;

    private static final String PUBLIC_PROFILE_ID = "public-uuid-123";
    private static final Long USER_ID = 1L;

    private HttpServletRequest mockHttpRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        lenient().when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        lenient().when(request.getHeader("User-Agent")).thenReturn("Postman");
        return request;
    }

    private UserBasicResponseDTO buildUser() {
        UserBasicResponseDTO dto = new UserBasicResponseDTO();
        dto.setUserId(USER_ID);
        dto.setFullName("John Doe");
        return dto;
    }

    private MedicalProfile buildMedicalProfile() {
        return MedicalProfile.builder()
                .id(1L)
                .age(25)
                .gender("Male")
                .bloodGroup("O+")
                .height(175.0)
                .weight(70.0)
                .allergies("None")
                .medicalConditions("Diabetes")
                .currentMedications("Metformin")
                .organDonor(true)
                .userId(USER_ID)
                .publicProfileId(PUBLIC_PROFILE_ID)
                .build();
    }

    private EmergencyContacts buildContact() {
        return EmergencyContacts.builder()
                .id(1L)
                .name("Jane Doe")
                .relationship("Sister")
                .phoneNumber("9876543210")
                .userId(USER_ID)
                .build();
    }

    // -------------------------------------------------------------------------
    // getEmergencyProfile
    // -------------------------------------------------------------------------

    @Test
    void getEmergencyProfile_ShouldReturnCachedProfile_OnCacheHit() {
        EmergencyProfileResponseDTO cached = EmergencyProfileResponseDTO.builder()
                .fullName("Cached Name")
                .build();
        when(cacheService.getEmergencyProfile(PUBLIC_PROFILE_ID)).thenReturn(cached);

        EmergencyProfileResponseDTO result =
                emergencyService.getEmergencyProfile(PUBLIC_PROFILE_ID, mockHttpRequest());

        assertEquals("Cached Name", result.getFullName());
        verify(medicalProfileRepository, never()).findByPublicProfileId(any());
        verify(authClient, never()).getUserById(any());
        verify(auditEventProducer, never()).publishAuditEvent(any());
    }

    @Test
    void getEmergencyProfile_ShouldReturnFullProfile_OnCacheMiss() {
        when(cacheService.getEmergencyProfile(PUBLIC_PROFILE_ID)).thenReturn(null);
        when(medicalProfileRepository.findByPublicProfileId(PUBLIC_PROFILE_ID))
                .thenReturn(Optional.of(buildMedicalProfile()));
        when(authClient.getUserById(USER_ID)).thenReturn(buildUser());
        when(emergencyContactsRepository.findAllByUserId(USER_ID)).thenReturn(List.of(buildContact()));

        EmergencyProfileResponseDTO result =
                emergencyService.getEmergencyProfile(PUBLIC_PROFILE_ID, mockHttpRequest());

        assertEquals("John Doe", result.getFullName());
        assertEquals(25, result.getAge());
        assertEquals("Male", result.getGender());
        assertEquals("O+", result.getBloodGroup());
        assertEquals("None", result.getAllergies());
        assertEquals("Diabetes", result.getMedicalConditions());
        assertEquals("Metformin", result.getCurrentMedications());
        assertTrue(result.isOrganDonor());
        assertEquals(1, result.getEmergencyContacts().size());
        assertEquals("Jane Doe", result.getEmergencyContacts().get(0).getName());

        verify(medicalProfileRepository).findByPublicProfileId(PUBLIC_PROFILE_ID);
        verify(authClient).getUserById(USER_ID);
        verify(emergencyContactsRepository).findAllByUserId(USER_ID);
        verify(auditEventProducer).publishAuditEvent(any());
        verify(cacheService).cacheEmergencyProfile(eq(PUBLIC_PROFILE_ID), any());
    }

    @Test
    void getEmergencyProfile_ShouldReturnEmptyContactList_WhenUserHasNoContacts() {
        when(cacheService.getEmergencyProfile(PUBLIC_PROFILE_ID)).thenReturn(null);
        when(medicalProfileRepository.findByPublicProfileId(PUBLIC_PROFILE_ID))
                .thenReturn(Optional.of(buildMedicalProfile()));
        when(authClient.getUserById(USER_ID)).thenReturn(buildUser());
        when(emergencyContactsRepository.findAllByUserId(USER_ID)).thenReturn(Collections.emptyList());

        EmergencyProfileResponseDTO result =
                emergencyService.getEmergencyProfile(PUBLIC_PROFILE_ID, mockHttpRequest());

        assertEquals("John Doe", result.getFullName());
        assertTrue(result.getEmergencyContacts().isEmpty());
    }

    @Test
    void getEmergencyProfile_ShouldThrow_WhenMedicalProfileNotFound() {
        when(cacheService.getEmergencyProfile(PUBLIC_PROFILE_ID)).thenReturn(null);
        when(medicalProfileRepository.findByPublicProfileId(PUBLIC_PROFILE_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> emergencyService.getEmergencyProfile(PUBLIC_PROFILE_ID, mockHttpRequest()));

        verify(authClient, never()).getUserById(any());
        verify(emergencyContactsRepository, never()).findAllByUserId(any());
    }

    @Test
    void getEmergencyProfile_ShouldThrow_WhenAuthServiceIsUnavailable() {
        when(cacheService.getEmergencyProfile(PUBLIC_PROFILE_ID)).thenReturn(null);
        when(medicalProfileRepository.findByPublicProfileId(PUBLIC_PROFILE_ID))
                .thenReturn(Optional.of(buildMedicalProfile()));

        Request feignRequest = Request.create(
                Request.HttpMethod.GET,
                "/api/auth/internal/users/" + USER_ID,
                Collections.emptyMap(),
                Request.Body.empty(),
                new RequestTemplate()
        );
        RetryableException retryableException = new RetryableException(
                -1, "Connection refused", Request.HttpMethod.GET, (Long) null, feignRequest
        );
        when(authClient.getUserById(USER_ID)).thenThrow(retryableException);

        ServiceUnavailableException exception = assertThrows(
                ServiceUnavailableException.class,
                () -> emergencyService.getEmergencyProfile(PUBLIC_PROFILE_ID, mockHttpRequest())
        );

        assertEquals("Auth Service is not available", exception.getMessage());
        verify(emergencyContactsRepository, never()).findAllByUserId(any());
        verify(auditEventProducer, never()).publishAuditEvent(any());
    }

    @Test
    void getEmergencyProfile_ShouldAlwaysLogAudit_BeforeReturningProfile() {
        when(cacheService.getEmergencyProfile(PUBLIC_PROFILE_ID)).thenReturn(null);
        when(medicalProfileRepository.findByPublicProfileId(PUBLIC_PROFILE_ID))
                .thenReturn(Optional.of(buildMedicalProfile()));
        when(authClient.getUserById(USER_ID)).thenReturn(buildUser());
        when(emergencyContactsRepository.findAllByUserId(USER_ID)).thenReturn(Collections.emptyList());

        emergencyService.getEmergencyProfile(PUBLIC_PROFILE_ID, mockHttpRequest());

        // Audit must always be published whenever a profile is successfully accessed
        verify(auditEventProducer, times(1)).publishAuditEvent(any());
    }
}
