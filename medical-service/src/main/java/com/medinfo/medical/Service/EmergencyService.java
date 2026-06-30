package com.medinfo.medical.Service;

import com.medinfo.medical.DTO.EContactsDTO;
import com.medinfo.medical.DTO.EmergencyProfileResponseDTO;
import com.medinfo.medical.DTO.MedicalProfileResponseDTO;
import com.medinfo.medical.Entity.EmergencyAccessLog;
import com.medinfo.medical.Entity.EmergencyContacts;
import com.medinfo.medical.Entity.MedicalProfile;
import com.medinfo.medical.Enum.AccessMethod;
import com.medinfo.medical.Repository.EmergencyAccessLogRepository;
import com.medinfo.medical.Repository.EmergencyContactsRepository;
import com.medinfo.medical.Repository.MedicalProfileRepository;
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

    public EmergencyProfileResponseDTO getEmergencyProfile(String publicProfileId, HttpServletRequest request){

        Long userId=1L;
        emergencyAccessLogService.logAccess(
                userId,
                request,
                AccessMethod.URL
        );

        MedicalProfile medicalProfile=medicalProfileRepository.findByUserId(userId)
                .orElseThrow(()->new RuntimeException("Medical Profile does not Exits"));
        List<EmergencyContacts> emergencyContacts=emergencyContactsRepository.findAllByUserId(userId);

        return EmergencyProfileResponseDTO.builder()
                .fullName("Blank user")
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
