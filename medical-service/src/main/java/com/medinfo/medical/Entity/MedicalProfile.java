package com.medinfo.medical.Entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicalProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Integer age;
    private String gender;
    private String bloodGroup;
    private Double height;
    private Double weight;
    private String allergies;
    private String medicalConditions;
    private String currentMedications;
    private boolean organDonor;

    @Column(nullable = false)
    private Long userId;
}
