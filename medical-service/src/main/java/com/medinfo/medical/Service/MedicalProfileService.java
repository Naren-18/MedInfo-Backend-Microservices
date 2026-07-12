package com.medinfo.medical.Service;

import com.medinfo.medical.DTO.CreateMedicalProfileDTO;
import com.medinfo.medical.DTO.MedicalProfileResponseDTO;
import com.medinfo.medical.Entity.MedicalProfile;
import com.medinfo.medical.Exception.ResourceAlreadyExistsException;
import com.medinfo.medical.Exception.ResourceNotFoundException;
import com.medinfo.medical.Repository.MedicalProfileRepository;
import com.medinfo.medical.cache.EmergencyProfileCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class MedicalProfileService {
    private final MedicalProfileRepository medicalProfileRepository;
    private final EmergencyProfileCacheService cacheService;

    public MedicalProfile createProfile(CreateMedicalProfileDTO createMedicalProfileDTO){
        Long userId=getCurrentUserId();
        log.info("Creating Medical Profile. UserId={}", userId);
        if(medicalProfileRepository.existsByUserId(userId)){
            throw new ResourceAlreadyExistsException(
                    "Medical Profile",
                    "userId",
                    userId
            );

        }
        MedicalProfile medicalProfile=MedicalProfile.builder()
                .age(createMedicalProfileDTO.getAge())
                .gender(createMedicalProfileDTO.getGender())
                .bloodGroup(createMedicalProfileDTO.getBloodGroup())
                .height(createMedicalProfileDTO.getHeight())
                .weight(createMedicalProfileDTO.getWeight())
                .allergies(createMedicalProfileDTO.getAllergies())
                .medicalConditions(createMedicalProfileDTO.getMedicalConditions())
                .currentMedications(createMedicalProfileDTO.getCurrentMedications())
                .organDonor(createMedicalProfileDTO.isOrganDonor())
                .userId(userId)
                .publicProfileId(UUID.randomUUID().toString())
                .build();

        MedicalProfile saved = medicalProfileRepository.save(medicalProfile);
        log.info("Medical Profile Created Successfully. UserId={}", userId);
        return saved;
    }
    public MedicalProfile updateProfile(CreateMedicalProfileDTO createMedicalProfileDTO){
        Long userId=getCurrentUserId();
        log.info("Updating Medical Profile. UserId={}", userId);
        MedicalProfile medicalProfile=medicalProfileRepository.findByUserId(userId)
                        .orElseThrow(()->new ResourceNotFoundException(
                                "Medical Profile",
                                "userId",
                                userId
                        ));
        medicalProfile.setAge(createMedicalProfileDTO.getAge());
        medicalProfile.setGender(createMedicalProfileDTO.getGender());
        medicalProfile.setBloodGroup(createMedicalProfileDTO.getBloodGroup());
        medicalProfile.setHeight(createMedicalProfileDTO.getHeight());
        medicalProfile.setWeight(createMedicalProfileDTO.getWeight());
        medicalProfile.setAllergies(createMedicalProfileDTO.getAllergies());
        medicalProfile.setCurrentMedications(createMedicalProfileDTO.getCurrentMedications());
        medicalProfile.setMedicalConditions(createMedicalProfileDTO.getMedicalConditions());
        medicalProfile.setOrganDonor(createMedicalProfileDTO.isOrganDonor());
        medicalProfile.setUserId(userId);
        medicalProfileRepository.save(medicalProfile);
        cacheService.evictEmergencyProfile(medicalProfile.getPublicProfileId());
        return medicalProfile;

    }


    public MedicalProfileResponseDTO getMyProfile(){
        Long userId=getCurrentUserId();
        MedicalProfile medicalProfile =
                medicalProfileRepository
                        .findByUserId(userId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Medical Profile",
                                        "userId",
                                        userId
                                ));
        return MedicalProfileResponseDTO.builder()
                .age(medicalProfile.getAge())
                .gender(medicalProfile.getGender())
                .bloodGroup(medicalProfile.getBloodGroup())
                .height(medicalProfile.getHeight())
                .weight(medicalProfile.getWeight())
                .allergies(medicalProfile.getAllergies())
                .medicalConditions(medicalProfile.getMedicalConditions())
                .currentMedications(medicalProfile.getCurrentMedications())
                .organDonor(medicalProfile.isOrganDonor())
                .build();

    }

    public String deleteProfile(){
        Long userId=getCurrentUserId();
        log.info("Deleting Medical Profile. UserId={}", userId);
        MedicalProfile medicalProfile=medicalProfileRepository.findByUserId(userId)
                .orElseThrow(()-> new ResourceNotFoundException("Medical Profile","userId",userId));
        medicalProfileRepository.delete(medicalProfile);
        return "Medical Profile Deleted Successfully";
    }

    private Long getCurrentUserId() {
        return (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}
