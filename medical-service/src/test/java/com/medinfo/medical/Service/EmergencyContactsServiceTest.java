package com.medinfo.medical.Service;

import com.medinfo.medical.DTO.EContactsDTO;
import com.medinfo.medical.Entity.EmergencyContacts;
import com.medinfo.medical.Exception.ResourceNotFoundException;
import com.medinfo.medical.Exception.UnauthorizedException;
import com.medinfo.medical.Repository.EmergencyContactsRepository;
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmergencyContactsServiceTest {

    @Mock
    private EmergencyContactsRepository emergencyContactsRepository;

    @InjectMocks
    private EmergencyContactsService emergencyContactsService;

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

    private EContactsDTO buildContactDTO() {
        return EContactsDTO.builder()
                .name("Jane Doe")
                .relationship("Sister")
                .phoneNumber("9876543210")
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
    // createContact
    // -------------------------------------------------------------------------

    @Test
    void createContact_ShouldSaveAndReturnSuccessMessage() {
        when(emergencyContactsRepository.save(any(EmergencyContacts.class))).thenReturn(buildContact());

        String result = emergencyContactsService.createContact(buildContactDTO());

        assertEquals("Emergency Contact Created Successfully ", result);
        verify(emergencyContactsRepository).save(any(EmergencyContacts.class));
    }

    // -------------------------------------------------------------------------
    // getContacts
    // -------------------------------------------------------------------------

    @Test
    void getContacts_ShouldReturnMappedContactList() {
        when(emergencyContactsRepository.findAllByUserId(USER_ID)).thenReturn(List.of(buildContact()));

        List<EContactsDTO> result = emergencyContactsService.getContacts();

        assertEquals(1, result.size());
        assertEquals("Jane Doe", result.get(0).getName());
        assertEquals("Sister", result.get(0).getRelationship());
        assertEquals("9876543210", result.get(0).getPhoneNumber());
    }

    @Test
    void getContacts_ShouldThrow_WhenNoContactsExist() {
        when(emergencyContactsRepository.findAllByUserId(USER_ID)).thenReturn(Collections.emptyList());

        assertThrows(ResourceNotFoundException.class,
                () -> emergencyContactsService.getContacts());
    }

    // -------------------------------------------------------------------------
    // deleteContact
    // -------------------------------------------------------------------------

    @Test
    void deleteContact_ShouldDeleteAndReturnSuccessMessage() {
        when(emergencyContactsRepository.findById(1L)).thenReturn(Optional.of(buildContact()));

        String result = emergencyContactsService.deleteContact(1L);

        assertEquals("Emergency Contact Deleted", result);
        verify(emergencyContactsRepository).delete(any(EmergencyContacts.class));
    }

    @Test
    void deleteContact_ShouldThrow_WhenContactNotFound() {
        when(emergencyContactsRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> emergencyContactsService.deleteContact(1L));

        verify(emergencyContactsRepository, never()).delete(any());
    }

    @Test
    void deleteContact_ShouldThrow_WhenContactBelongsToAnotherUser() {
        EmergencyContacts otherUsersContact = EmergencyContacts.builder()
                .id(1L)
                .name("Jane Doe")
                .relationship("Sister")
                .phoneNumber("9876543210")
                .userId(99L)
                .build();

        when(emergencyContactsRepository.findById(1L)).thenReturn(Optional.of(otherUsersContact));

        assertThrows(UnauthorizedException.class,
                () -> emergencyContactsService.deleteContact(1L));

        verify(emergencyContactsRepository, never()).delete(any());
    }

    // -------------------------------------------------------------------------
    // updateContact
    // -------------------------------------------------------------------------

    @Test
    void updateContact_ShouldUpdateFieldsAndReturnSuccessMessage() {
        EmergencyContacts existing = buildContact();
        when(emergencyContactsRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(emergencyContactsRepository.save(any(EmergencyContacts.class))).thenReturn(existing);

        EContactsDTO updatedDTO = EContactsDTO.builder()
                .name("Updated Name")
                .relationship("Brother")
                .phoneNumber("1234567890")
                .build();

        String result = emergencyContactsService.updateContact(1L, updatedDTO);

        assertEquals("Emergency Contact Updated", result);
        assertEquals("Updated Name", existing.getName());
        assertEquals("Brother", existing.getRelationship());
        assertEquals("1234567890", existing.getPhoneNumber());
        verify(emergencyContactsRepository).save(existing);
    }

    @Test
    void updateContact_ShouldThrow_WhenContactNotFound() {
        when(emergencyContactsRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> emergencyContactsService.updateContact(1L, buildContactDTO()));

        verify(emergencyContactsRepository, never()).save(any());
    }

    @Test
    void updateContact_ShouldThrow_WhenContactBelongsToAnotherUser() {
        EmergencyContacts otherUsersContact = EmergencyContacts.builder()
                .id(1L)
                .name("Jane Doe")
                .relationship("Sister")
                .phoneNumber("9876543210")
                .userId(99L)
                .build();

        when(emergencyContactsRepository.findById(1L)).thenReturn(Optional.of(otherUsersContact));

        assertThrows(UnauthorizedException.class,
                () -> emergencyContactsService.updateContact(1L, buildContactDTO()));

        verify(emergencyContactsRepository, never()).save(any());
    }
}
