package com.medinfo.medical.Service;

import com.medinfo.medical.Client.AuditClient;
import com.medinfo.medical.Client.AuthClient;
import com.medinfo.medical.DTO.*;
import com.medinfo.medical.Entity.EmergencyContacts;
import com.medinfo.medical.Entity.MedicalProfile;
import com.medinfo.common.Enum.AccessMethod;
import com.medinfo.medical.Exception.ResourceNotFoundException;
import com.medinfo.medical.Exception.ServiceUnavailableException;
import com.medinfo.medical.Repository.EmergencyContactsRepository;
import com.medinfo.medical.Repository.MedicalProfileRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class EmergencyService {
    private final EmergencyContactsRepository emergencyContactsRepository;
    private final MedicalProfileRepository medicalProfileRepository;
    private final AuthClient authClient;
    private final AuditClient auditClient;
    public EmergencyProfileResponseDTO getEmergencyProfile(String publicProfileId, HttpServletRequest request){

        UserPublicResponseDTO responseDTO;
        try {
             responseDTO =
                    authClient.getUserByPublicProfileId(publicProfileId);

        } catch (feign.RetryableException ex) {
            throw new ServiceUnavailableException(
                    "Auth Service is not available"
            );
        }
        Long userId=responseDTO.getUserId();
        CreateAuditLogRequestDTO createAuditLogRequestDTO=CreateAuditLogRequestDTO.builder()
                .userId(userId)
                .ipAddress(request.getRemoteAddr())
                .userAgent(request.getHeader("User-Agent"))
                .accessMethod(AccessMethod.URL)
                .build();

        auditClient.createAuditLog(createAuditLogRequestDTO);
        MedicalProfile medicalProfile=medicalProfileRepository.findByUserId(userId)
                .orElseThrow(()->new ResourceNotFoundException(
                        "Medical Profile",
                        "userId",
                        userId
                ));
        List<EmergencyContacts> emergencyContacts=emergencyContactsRepository.findAllByUserId(userId);

        return EmergencyProfileResponseDTO.builder()
                .fullName(responseDTO.getFullName())
                .age(medicalProfile.getAge())
                .gender(medicalProfile.getGender())
                .bloodGroup(medicalProfile.getBloodGroup())
                .allergies(medicalProfile.getAllergies())
                .currentMedications(medicalProfile.getCurrentMedications())
                .medicalConditions(medicalProfile.getMedicalConditions())
                .organDonor(medicalProfile.isOrganDonor())
                .emergencyContacts(emergencyContacts.stream()
                        .map(contact ->
                                EContactsDTO.builder()
                                        .name(contact.getName())
                                        .relationship(contact.getRelationship())
                                        .phoneNumber(contact.getPhoneNumber())
                                        .build()
                        )
                        .toList())
                .build();
    }
}
