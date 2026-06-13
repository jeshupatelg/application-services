package com.jpg.portfolio.orchestrator.repository;

import com.jpg.portfolio.model.UserImpl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserImpl, String> {
    Optional<UserImpl> findByUsername(String username);
}
