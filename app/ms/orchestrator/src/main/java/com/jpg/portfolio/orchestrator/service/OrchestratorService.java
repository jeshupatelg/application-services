package com.jpg.portfolio.orchestrator.service;

import com.jpg.portfolio.model.ActiveArtifact;
import com.jpg.portfolio.model.DeletedArtifact;
import com.jpg.portfolio.model.UserImpl;
import com.jpg.portfolio.orchestrator.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OrchestratorService {

    private final UserRepository userRepository;
    private final EntityManager entityManager;

    public OrchestratorService(UserRepository userRepository, EntityManager entityManager) {
        this.userRepository = userRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public UserImpl getOrCreateUser(String username) {
        return userRepository.findByUsername(username).orElseGet(() -> 
            userRepository.save(new UserImpl(username))
        );
    }

    @Transactional
    public UserImpl getOrCreateUserWithAlias(String username, String alias) {
        UserImpl dbUser = userRepository.findByUsername(username).orElse(null);
        if (dbUser == null) {
            dbUser = new UserImpl(username);
            dbUser.setAlias(alias);
            return userRepository.save(dbUser);
        } else if (alias != null && !alias.equals(dbUser.getAlias())) {
            dbUser.setAlias(alias);
            return userRepository.save(dbUser);
        }
        return dbUser;
    }

    @Transactional(readOnly = true)
    public Optional<UserImpl> findUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional
    public UserImpl saveUser(UserImpl user) {
        return userRepository.save(user);
    }

    @Transactional
    public void softDeleteSingleArtifact(String username, String version) {
        UserImpl dbUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User profile not found"));

        ActiveArtifact activeArt = (ActiveArtifact) dbUser.getArtifacts().stream()
                .filter(a -> a.getVersion().equals(version) && a instanceof ActiveArtifact)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Active artifact version not found"));

        DeletedArtifact deleted = new DeletedArtifact(
                activeArt.getVersion(),
                activeArt.getTags(),
                null, // Clears the download link
                activeArt.getDesc(),
                activeArt.getTimestamp(),
                activeArt.isMajorVersion(),
                dbUser
        );

        dbUser.getArtifacts().remove(activeArt);
        entityManager.remove(activeArt);
        entityManager.flush();
        entityManager.detach(activeArt);
        dbUser.getArtifacts().add(deleted);
        userRepository.save(dbUser);
    }
}

