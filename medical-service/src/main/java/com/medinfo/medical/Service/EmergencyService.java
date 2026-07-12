package com.medinfo.medical.Service;

import com.medinfo.common.events.AuditLogEvent;
import com.medinfo.medical.Client.AuthClient;
import com.medinfo.medical.DTO.*;
import com.medinfo.medical.Entity.EmergencyContacts;
import com.medinfo.medical.Entity.MedicalProfile;
import com.medinfo.common.enums.AccessMethod;
import com.medinfo.medical.Exception.ResourceNotFoundException;
import com.medinfo.medical.Exception.ServiceUnavailableException;
import com.medinfo.medical.Producer.AuditEventProducer;
import com.medinfo.medical.Repository.EmergencyContactsRepository;
import com.medinfo.medical.Repository.MedicalProfileRepository;
import com.medinfo.medical.cache.EmergencyProfileCacheService;
import feign.RetryableException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
@Slf4j
public class EmergencyService {
    private final EmergencyContactsRepository emergencyContactsRepository;
    private final MedicalProfileRepository medicalProfileRepository;
    private final AuditEventProducer auditEventProducer;
    private final EmergencyProfileCacheService cacheService;
    private final AuthClient authClient;

    public EmergencyProfileResponseDTO getEmergencyProfile(String publicProfileId, HttpServletRequest request){
        EmergencyProfileResponseDTO cacheresponseDTO=cacheService.getEmergencyProfile(publicProfileId);
        if(cacheresponseDTO != null){
            log.info("Cache HIT for {}", publicProfileId);
            return  cacheresponseDTO;
        }
        log.info("Cache MISS for {}", publicProfileId);


        MedicalProfile medicalProfile =
                medicalProfileRepository.findByPublicProfileId(publicProfileId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Medical Profile",
                                        "publicProfileId",
                                        publicProfileId
                                ));
        Long userId = medicalProfile.getUserId();

        UserBasicResponseDTO user;
        try {
            user = authClient.getUserById(userId);
        } catch (RetryableException ex) {
            throw new ServiceUnavailableException("Auth Service is not available");
        }

        List<EmergencyContacts> emergencyContacts=emergencyContactsRepository.findAllByUserId(userId);
        AuditLogEvent event=AuditLogEvent.builder()
                .userId(userId)
                .accessMethod(AccessMethod.URL)
                .ipAddress(request.getRemoteAddr())
                .userAgent(request.getHeader("User-Agent"))
//                .eventId(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .build();
        auditEventProducer.publishAuditEvent(event);
        EmergencyProfileResponseDTO emergencyProfileResponseDTO= EmergencyProfileResponseDTO.builder()
                .fullName(user.getFullName())
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
        cacheService.cacheEmergencyProfile(publicProfileId,emergencyProfileResponseDTO);
        return emergencyProfileResponseDTO;
    }
}
