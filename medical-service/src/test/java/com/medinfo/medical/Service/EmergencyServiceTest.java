package com.medinfo.medical.Service;

import com.medinfo.medical.Client.AuditClient;
import com.medinfo.medical.Client.AuthClient;
import com.medinfo.medical.DTO.CreateAuditLogRequestDTO;
import com.medinfo.medical.DTO.EmergencyProfileResponseDTO;
import com.medinfo.medical.DTO.UserPublicResponseDTO;
import com.medinfo.medical.Entity.EmergencyContacts;
import com.medinfo.medical.Entity.MedicalProfile;
import com.medinfo.medical.Exception.ResourceNotFoundException;
import com.medinfo.medical.Exception.ServiceUnavailableException;
import com.medinfo.medical.Repository.EmergencyContactsRepository;
import com.medinfo.medical.Repository.MedicalProfileRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmergencyServiceTest {

    @Mock
    private EmergencyContactsRepository emergencyContactsRepository;

    @Mock
    private MedicalProfileRepository medicalProfileRepository;

    @Mock
    private AuthClient authClient;

    @Mock
    private AuditClient auditClient;

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

    private UserPublicResponseDTO buildUserPublicResponse() {
        UserPublicResponseDTO dto = new UserPublicResponseDTO();
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
    void getEmergencyProfile_ShouldReturnFullProfile() {
        when(authClient.getUserByPublicProfileId(PUBLIC_PROFILE_ID)).thenReturn(buildUserPublicResponse());
        when(auditClient.createAuditLog(any(CreateAuditLogRequestDTO.class))).thenReturn("logged");
        when(medicalProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(buildMedicalProfile()));
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

        verify(authClient).getUserByPublicProfileId(PUBLIC_PROFILE_ID);
        verify(auditClient).createAuditLog(any(CreateAuditLogRequestDTO.class));
        verify(medicalProfileRepository).findByUserId(USER_ID);
        verify(emergencyContactsRepository).findAllByUserId(USER_ID);
    }

    @Test
    void getEmergencyProfile_ShouldReturnEmptyContactList_WhenUserHasNoContacts() {
        when(authClient.getUserByPublicProfileId(PUBLIC_PROFILE_ID)).thenReturn(buildUserPublicResponse());
        when(auditClient.createAuditLog(any(CreateAuditLogRequestDTO.class))).thenReturn("logged");
        when(medicalProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(buildMedicalProfile()));
        when(emergencyContactsRepository.findAllByUserId(USER_ID)).thenReturn(Collections.emptyList());

        EmergencyProfileResponseDTO result =
                emergencyService.getEmergencyProfile(PUBLIC_PROFILE_ID, mockHttpRequest());

        assertEquals("John Doe", result.getFullName());
        assertTrue(result.getEmergencyContacts().isEmpty());
    }

    @Test
    void getEmergencyProfile_ShouldThrow_WhenAuthServiceIsUnavailable() {
        // Construct a minimal feign.RetryableException to simulate a connection failure
        Request feignRequest = Request.create(
                Request.HttpMethod.GET,
                "/api/auth/users/public/" + PUBLIC_PROFILE_ID,
                Collections.emptyMap(),
                Request.Body.empty(),
                new RequestTemplate()
        );
        RetryableException retryableException = new RetryableException(
                -1, "Connection refused", Request.HttpMethod.GET, (Long) null, feignRequest
        );

        when(authClient.getUserByPublicProfileId(PUBLIC_PROFILE_ID)).thenThrow(retryableException);

        ServiceUnavailableException exception = assertThrows(
                ServiceUnavailableException.class,
                () -> emergencyService.getEmergencyProfile(PUBLIC_PROFILE_ID, mockHttpRequest())
        );

        assertEquals("Auth Service is not available", exception.getMessage());
        verify(medicalProfileRepository, never()).findByUserId(any());
        verify(auditClient, never()).createAuditLog(any());
    }

    @Test
    void getEmergencyProfile_ShouldThrow_WhenMedicalProfileNotFound() {
        when(authClient.getUserByPublicProfileId(PUBLIC_PROFILE_ID)).thenReturn(buildUserPublicResponse());
        when(auditClient.createAuditLog(any(CreateAuditLogRequestDTO.class))).thenReturn("logged");
        when(medicalProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> emergencyService.getEmergencyProfile(PUBLIC_PROFILE_ID, mockHttpRequest()));

        verify(emergencyContactsRepository, never()).findAllByUserId(any());
    }

    @Test
    void getEmergencyProfile_ShouldAlwaysLogAudit_BeforeReturningProfile() {
        when(authClient.getUserByPublicProfileId(PUBLIC_PROFILE_ID)).thenReturn(buildUserPublicResponse());
        when(auditClient.createAuditLog(any(CreateAuditLogRequestDTO.class))).thenReturn("logged");
        when(medicalProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(buildMedicalProfile()));
        when(emergencyContactsRepository.findAllByUserId(USER_ID)).thenReturn(Collections.emptyList());

        emergencyService.getEmergencyProfile(PUBLIC_PROFILE_ID, mockHttpRequest());

        // Audit must always be called whenever a profile is successfully accessed
        verify(auditClient, times(1)).createAuditLog(any(CreateAuditLogRequestDTO.class));
    }
}
