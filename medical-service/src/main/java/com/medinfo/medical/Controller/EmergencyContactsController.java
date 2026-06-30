package com.medinfo.medical.Controller;

import com.medinfo.medical.DTO.EContactsDTO;
import com.medinfo.medical.Entity.EmergencyContacts;
import com.medinfo.medical.Service.EmergencyContactsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
public class EmergencyContactsController {

    private final EmergencyContactsService emergencyContactsService;


    @PostMapping
    public ResponseEntity<String> createContacts(@Valid @RequestBody EContactsDTO EContactsDTO){
        return ResponseEntity.ok(
                emergencyContactsService.createContact(EContactsDTO)
        );
    }

    @GetMapping
    public ResponseEntity<List<EContactsDTO>> getContacts(){
        return ResponseEntity.ok(
             emergencyContactsService.getContacts()
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> updateContacts(@Valid @PathVariable Long id,@RequestBody EContactsDTO eContactsDTO){
        return ResponseEntity.ok(
                emergencyContactsService.updateContact(id,eContactsDTO)
        );
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteContacts(@Valid @PathVariable Long id){
        return ResponseEntity.ok(
                emergencyContactsService.deleteContact(id)
        );
    }

}
