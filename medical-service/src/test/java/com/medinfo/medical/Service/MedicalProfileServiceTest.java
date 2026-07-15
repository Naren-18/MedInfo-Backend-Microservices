package com.medinfo.medical.Service;

import com.medinfo.medical.DTO.CreateMedicalProfileDTO;
import com.medinfo.medical.DTO.MedicalProfileResponseDTO;
import com.medinfo.medical.Entity.MedicalProfile;
import com.medinfo.medical.Exception.ResourceAlreadyExistsException;
import com.medinfo.medical.Exception.ResourceNotFoundException;
import com.medinfo.medical.Repository.MedicalProfileRepository;
import com.medinfo.medical.cache.EmergencyProfileCacheService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MedicalProfileServiceTest {

    @Mock
    private MedicalProfileRepository medicalProfileRepository;

    @Mock
    private EmergencyProfileCacheService cacheService;

    @InjectMocks
    private MedicalProfileService medicalProfileService;

    private static final Long USER_ID = 1L;

    @BeforeEach
    void setUpSecurityContext() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(USER_ID);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private CreateMedicalProfileDTO buildProfileDTO() {
        CreateMedicalProfileDTO dto = new CreateMedicalProfileDTO();
        dto.setAge(25);
        dto.setGender("Male");
        dto.setBloodGroup("O+");
        dto.setHeight(175.0);
        dto.setWeight(70.0);
        dto.setAllergies("None");
        dto.setMedicalConditions("None");
        dto.setCurrentMedications("None");
        dto.setOrganDonor(true);
        return dto;
    }

    private MedicalProfile buildSavedProfile() {
        return MedicalProfile.builder()
                .id(1L)
                .age(25)
                .gender("Male")
                .bloodGroup("O+")
                .height(175.0)
                .weight(70.0)
                .allergies("None")
                .medicalConditions("None")
                .currentMedications("None")
                .organDonor(true)
                .userId(USER_ID)
                .publicProfileId("public-uuid-123")
                .build();
    }

    // -------------------------------------------------------------------------
    // createProfile
    // -------------------------------------------------------------------------

    @Test
    void createProfile_ShouldSaveAndReturnProfile() {
        when(medicalProfileRepository.existsByUserId(USER_ID)).thenReturn(false);
        when(medicalProfileRepository.save(any(MedicalProfile.class))).thenReturn(buildSavedProfile());

        MedicalProfile result = medicalProfileService.createProfile(buildProfileDTO());

        assertEquals(USER_ID, result.getUserId());
        assertEquals("O+", result.getBloodGroup());
        assertEquals(25, result.getAge());
        assertTrue(result.isOrganDonor());
        verify(medicalProfileRepository).save(any(MedicalProfile.class));
    }

    @Test
    void createProfile_ShouldThrow_WhenProfileAlreadyExists() {
        when(medicalProfileRepository.existsByUserId(USER_ID)).thenReturn(true);

        assertThrows(ResourceAlreadyExistsException.class,
                () -> medicalProfileService.createProfile(buildProfileDTO()));

        verify(medicalProfileRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // updateProfile
    // -------------------------------------------------------------------------

    @Test
    void updateProfile_ShouldUpdateAndReturnProfile() {
        MedicalProfile existing = buildSavedProfile();
        when(medicalProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));
        when(medicalProfileRepository.save(any(MedicalProfile.class))).thenReturn(existing);

        CreateMedicalProfileDTO updatedDTO = buildProfileDTO();
        updatedDTO.setBloodGroup("A+");
        updatedDTO.setAge(30);

        MedicalProfile result = medicalProfileService.updateProfile(updatedDTO);

        assertEquals("A+", result.getBloodGroup());
        assertEquals(30, result.getAge());
        verify(medicalProfileRepository).save(existing);
        verify(cacheService).evictEmergencyProfile("public-uuid-123");
    }

    @Test
    void updateProfile_ShouldThrow_WhenProfileNotFound() {
        when(medicalProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> medicalProfileService.updateProfile(buildProfileDTO()));

        verify(medicalProfileRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // getMyProfile
    // -------------------------------------------------------------------------

    @Test
    void getMyProfile_ShouldReturnMedicalProfileResponseDTO() {
        when(medicalProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(buildSavedProfile()));

        MedicalProfileResponseDTO result = medicalProfileService.getMyProfile();

        assertEquals("public-uuid-123", result.getPublicProfileId());
        assertEquals(25, result.getAge());
        assertEquals("Male", result.getGender());
        assertEquals("O+", result.getBloodGroup());
        assertEquals(175.0, result.getHeight());
        assertEquals(70.0, result.getWeight());
        assertEquals("None", result.getAllergies());
        assertEquals("None", result.getMedicalConditions());
        assertEquals("None", result.getCurrentMedications());
        assertTrue(result.isOrganDonor());
    }

    @Test
    void getMyProfile_ShouldThrow_WhenProfileNotFound() {
        when(medicalProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> medicalProfileService.getMyProfile());
    }

    // -------------------------------------------------------------------------
    // deleteProfile
    // -------------------------------------------------------------------------

    @Test
    void deleteProfile_ShouldDeleteAndReturnSuccessMessage() {
        MedicalProfile existing = buildSavedProfile();
        when(medicalProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.of(existing));

        String result = medicalProfileService.deleteProfile();

        assertEquals("Medical Profile Deleted Successfully", result);
        verify(medicalProfileRepository).delete(existing);
    }

    @Test
    void deleteProfile_ShouldThrow_WhenProfileNotFound() {
        when(medicalProfileRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> medicalProfileService.deleteProfile());

        verify(medicalProfileRepository, never()).delete(any());
    }
}
