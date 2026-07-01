package com.medinfo.medical.Service;

import com.medinfo.medical.Client.AuthClient;
import com.medinfo.medical.DTO.EContactsDTO;
import com.medinfo.medical.DTO.EmergencyProfileResponseDTO;
import com.medinfo.medical.DTO.MedicalProfileResponseDTO;
import com.medinfo.medical.DTO.UserPublicResponseDTO;
import com.medinfo.medical.Entity.EmergencyAccessLog;
import com.medinfo.medical.Entity.EmergencyContacts;
import com.medinfo.medical.Entity.MedicalProfile;
import com.medinfo.medical.Enum.AccessMethod;
import com.medinfo.medical.Exception.ResourceNotFoundException;
import com.medinfo.medical.Exception.ServiceUnavailableException;
import com.medinfo.medical.Repository.EmergencyAccessLogRepository;
import com.medinfo.medical.Repository.EmergencyContactsRepository;
import com.medinfo.medical.Repository.MedicalProfileRepository;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@AllArgsConstructor
public class EmergencyService {
    private final EmergencyContactsRepository emergencyContactsRepository;
    private final MedicalProfileRepository medicalProfileRepository;
    private final EmergencyAccessLogService emergencyAccessLogService;
    private final AuthClient authClient;
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
        emergencyAccessLogService.logAccess(
                userId,
                request,
                AccessMethod.URL
        );

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
