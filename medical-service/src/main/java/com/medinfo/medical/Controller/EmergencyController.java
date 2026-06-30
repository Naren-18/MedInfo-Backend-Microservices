package com.medinfo.medical.Controller;

import com.medinfo.medical.DTO.EmergencyProfileResponseDTO;
import com.medinfo.medical.Service.EmergencyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/emergency")
@RequiredArgsConstructor
public class EmergencyController {
    private final EmergencyService emergencyService;

    @GetMapping("/{publicProfileId}")
    public ResponseEntity<EmergencyProfileResponseDTO> getEmergencyProfile(@Valid @PathVariable String publicProfileId, HttpServletRequest request){
        return ResponseEntity.ok(
                emergencyService.getEmergencyProfile(publicProfileId,request)
        );
    }
}
