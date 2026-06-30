package com.medinfo.medical.Repository;

import com.medinfo.medical.Entity.EmergencyContacts;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmergencyContactsRepository extends JpaRepository<EmergencyContacts,Long> {
    List<EmergencyContacts> findAllByUserId(Long userId);
    boolean existsByUserId(Long userId);
}
