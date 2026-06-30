package com.medinfo.medical.Service;

import com.medinfo.medical.DTO.CreateMedicalProfileDTO;
import com.medinfo.medical.DTO.MedicalProfileResponseDTO;
import com.medinfo.medical.Entity.MedicalProfile;
import com.medinfo.medical.Repository.MedicalProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MedicalProfileService {
    private final MedicalProfileRepository medicalProfileRepository;

    public String getUsername(){
        Authentication authentication= SecurityContextHolder.getContext()
                .getAuthentication();
        return authentication.getName();
    }
    public MedicalProfile createProfile(CreateMedicalProfileDTO createMedicalProfileDTO){
        String email=getUsername();
        Long userId=(Long)
                SecurityContextHolder.getContext()
                        .getAuthentication()
                        .getPrincipal();
        if(medicalProfileRepository.existsByUserId(userId)){
            throw new RuntimeException("Profile Already Exists");
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
                .build();
        medicalProfileRepository.save(medicalProfile);
        return medicalProfile;
    }
    public MedicalProfile updateProfile(CreateMedicalProfileDTO createMedicalProfileDTO){
        String email=getUsername();
        Long userId=(Long)
                SecurityContextHolder.getContext()
                        .getAuthentication()
                        .getPrincipal();
        MedicalProfile medicalProfile=medicalProfileRepository.findByUserId(userId)
                        .orElseThrow(()->new RuntimeException("Profile does not exists"));
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
        return medicalProfile;
    }


    public MedicalProfileResponseDTO getMyProfile(){
        String email=getUsername();
        Long userId=(Long)
                SecurityContextHolder.getContext()
                        .getAuthentication()
                        .getPrincipal();
        MedicalProfile medicalProfile =
                medicalProfileRepository
                        .findByUserId(userId)
                        .orElseThrow(() ->
                                new RuntimeException("Profile Not Found"));
        MedicalProfileResponseDTO medicalProfileResponseDTO=MedicalProfileResponseDTO.builder()
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
        return medicalProfileResponseDTO;

    }

    public String deleteProfile(){
        String email=getUsername();
        Long userId=(Long)
                SecurityContextHolder.getContext()
                        .getAuthentication()
                        .getPrincipal();
        MedicalProfile medicalProfile=medicalProfileRepository.findByUserId(userId)
                .orElseThrow(()-> new RuntimeException("Medical Profile not Found"));
        medicalProfileRepository.delete(medicalProfile);
        return "Medical Profile Deleted Successfully";
    }
}
