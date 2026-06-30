package com.medinfo.medical.Service;

import com.medinfo.medical.DTO.EContactsDTO;
import com.medinfo.medical.Entity.EmergencyContacts;
import com.medinfo.medical.Repository.EmergencyContactsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmergencyContactsService {

    private final EmergencyContactsRepository emergencyContactsRepository;

    public String getUsername(){
        Authentication authentication= SecurityContextHolder.getContext().getAuthentication();
        return authentication.getName();
    }
    public String createContact(EContactsDTO EContactsDTO){
        String email=getUsername();
        Long userId=(Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        EmergencyContacts emergencyContacts=EmergencyContacts.builder()
                .name(EContactsDTO.getName())
                .phoneNumber(EContactsDTO.getPhoneNumber())
                .relationship(EContactsDTO.getRelationship())
                .userId(userId)
                .build();
        emergencyContactsRepository.save(emergencyContacts);
        return "Emergency Contact Created Successfully ";
    }

    public List<EContactsDTO> getContacts(){
        String email=getUsername();
        Long userId=(Long)SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<EmergencyContacts> emergencyContacts=emergencyContactsRepository.findAllByUserId(userId);
        if(emergencyContacts.isEmpty()){
            throw new RuntimeException("No Contacts Found");
        }
        return emergencyContacts.stream()
                .map(contact ->
                        EContactsDTO.builder()
                                .name(contact.getName())
                                .relationship(contact.getRelationship())
                                .phoneNumber(contact.getPhoneNumber())
                                .build()
                )
                .toList();

    }

    public String deleteContact(Long id){
        String email=getUsername();
        Long userId=(Long)
                SecurityContextHolder.getContext()
                        .getAuthentication()
                        .getPrincipal();
        EmergencyContacts emergencyContacts=emergencyContactsRepository.findById(id)
                .orElseThrow(()->new RuntimeException("Invalid Contact"));
        if(emergencyContacts.getUserId() != userId){
            throw new RuntimeException("Unauthorized");
        }
        emergencyContactsRepository.delete(emergencyContacts);
        return "Emergency Contact Deleted";
    }
    public String updateContact(Long id,EContactsDTO eContactsDTO){
        String email=getUsername();
        Long userId=(Long) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        EmergencyContacts emergencyContacts=emergencyContactsRepository.findById(id)
                .orElseThrow(()->new RuntimeException("Invalid Contact"));
        if(emergencyContacts.getUserId() != userId){
            throw new RuntimeException("Unauthorized");
        }
        emergencyContacts.setName(eContactsDTO.getName());
        emergencyContacts.setRelationship(eContactsDTO.getRelationship());
        emergencyContacts.setPhoneNumber(eContactsDTO.getPhoneNumber());
        emergencyContactsRepository.save(emergencyContacts);
        return "Emergency Contact Updated";
    }
}
