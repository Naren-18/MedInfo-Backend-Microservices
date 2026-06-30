package com.medinfo.auth.Repository;

import com.medinfo.auth.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    Optional<User> findByPublicProfileId(String publicProfileId);
    boolean existsByEmail(String email);
}
