package com.medinfo.medical.Controller;

import com.medinfo.medical.DTO.CreateMedicalProfileDTO;
import com.medinfo.medical.DTO.MedicalProfileResponseDTO;
import com.medinfo.medical.Entity.MedicalProfile;
import com.medinfo.medical.Service.MedicalProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class MedicalProfileController {


    private final MedicalProfileService medicalProfileService;
    @PostMapping()
    public ResponseEntity<MedicalProfile> createProfile(@Valid @RequestBody CreateMedicalProfileDTO createMedicalProfileDTO){
        return ResponseEntity.ok(
                medicalProfileService.createProfile(createMedicalProfileDTO)
        );
    }
    @PutMapping()
    public ResponseEntity<MedicalProfile> updateProfile(@Valid @RequestBody CreateMedicalProfileDTO createMedicalProfileDTO){
        return ResponseEntity.ok(
                medicalProfileService.updateProfile(createMedicalProfileDTO)
        );
    }
    @DeleteMapping()
    public ResponseEntity<String> deleteProfile(){
        return ResponseEntity.ok(
                medicalProfileService.deleteProfile()
        );
    }

    @GetMapping
    public ResponseEntity<MedicalProfileResponseDTO> getMyProfile(){
        return ResponseEntity.ok(
                medicalProfileService.getMyProfile()
        );
    }
}
