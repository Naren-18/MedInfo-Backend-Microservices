package com.medinfo.medical.Service;

import com.medinfo.medical.DTO.EContactsDTO;
import com.medinfo.medical.Entity.EmergencyContacts;
import com.medinfo.medical.Exception.ResourceNotFoundException;
import com.medinfo.medical.Exception.UnauthorizedException;
import com.medinfo.medical.Repository.EmergencyContactsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmergencyContactsService {

    private final EmergencyContactsRepository emergencyContactsRepository;


    public EContactsDTO createContact(EContactsDTO EContactsDTO){
        Long userId=getCurrentUserId();
        log.info("Creating Emergency Contact. UserId={}", userId);
        EmergencyContacts emergencyContacts=EmergencyContacts.builder()
                .name(EContactsDTO.getName())
                .phoneNumber(EContactsDTO.getPhoneNumber())
                .relationship(EContactsDTO.getRelationship())
                .userId(userId)
                .build();
        emergencyContacts=emergencyContactsRepository.save(emergencyContacts);
        log.info("Emergency Contact Created Successfully. UserId={}", userId);
        return com.medinfo.medical.DTO.EContactsDTO.builder()
                .id(emergencyContacts.getId())
                .name(emergencyContacts.getName())
                .relationship(emergencyContacts.getRelationship())
                .phoneNumber(emergencyContacts.getPhoneNumber())
                .build();
    }

    public List<EContactsDTO> getContacts(){
        Long userId=getCurrentUserId();
        List<EmergencyContacts> emergencyContacts=emergencyContactsRepository.findAllByUserId(userId);
        if(emergencyContacts.isEmpty()){
            throw new ResourceNotFoundException(
                    "Emergency Contacts",
                    "userId",
                    userId);
        }
        return emergencyContacts.stream()
                .map(contact ->
                        EContactsDTO.builder()
                                .id(contact.getId())
                                .name(contact.getName())
                                .relationship(contact.getRelationship())
                                .phoneNumber(contact.getPhoneNumber())
                                .build()
                )
                .toList();

    }

    public String deleteContact(Long id){
        Long userId=getCurrentUserId();
        log.info("Deleting Emergency Contact. ContactId={}, UserId={}", id, userId);
        EmergencyContacts emergencyContacts=emergencyContactsRepository.findById(id)
                .orElseThrow(()->new ResourceNotFoundException("Contact","userId",userId));
        if (!emergencyContacts.getUserId().equals(userId)) {
            log.warn("Unauthorized attempt to delete contact. ContactId={}, UserId={}", id, userId);
            throw new UnauthorizedException(
                    "You are not authorized to access this emergency contact."
            );
        }
        emergencyContactsRepository.delete(emergencyContacts);
        return "Emergency Contact Deleted";
    }
    public EContactsDTO updateContact(Long id,EContactsDTO eContactsDTO){
        Long userId=getCurrentUserId();
        log.info("Updating Emergency Contact. ContactId={}, UserId={}", id, userId);
        EmergencyContacts emergencyContacts=emergencyContactsRepository.findById(id)
                .orElseThrow(()->new ResourceNotFoundException("Contact","userId",userId));
        if (!emergencyContacts.getUserId().equals(userId)){
            log.warn("Unauthorized attempt to update contact. ContactId={}, UserId={}", id, userId);
            throw new UnauthorizedException("Unauthorized");
        }
        emergencyContacts.setName(eContactsDTO.getName());
        emergencyContacts.setRelationship(eContactsDTO.getRelationship());
        emergencyContacts.setPhoneNumber(eContactsDTO.getPhoneNumber());
        emergencyContacts=emergencyContactsRepository.save(emergencyContacts);
        return EContactsDTO.builder()
                .id(emergencyContacts.getId())
                .name(emergencyContacts.getName())
                .relationship(emergencyContacts.getRelationship())
                .phoneNumber(emergencyContacts.getPhoneNumber())
                .build();
    }

    private Long getCurrentUserId() {
        return (Long) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();
    }
}
