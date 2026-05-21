package com.jpg.portfolio.orchestrator.validation;

import com.jpg.portfolio.common.model.ActiveArtifact;
import com.jpg.portfolio.common.model.Artifact;
import com.jpg.portfolio.common.model.DeletedArtifact;
import com.jpg.portfolio.common.model.UserImpl;
import com.jpg.portfolio.orchestrator.service.Generators;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class VersionGeneratorAndValidationTest {

    private UserImpl testUser;

    @BeforeEach
    public void setUp() {
        testUser = new UserImpl("jeshupatelg");
    }

    // ==========================================
    // 1. VERSION GENERATOR TESTS
    // ==========================================

    @Test
    public void testVersionGeneratorInitial() {
        testUser.setLatest(null);
        String version = Generators.VersionGenerator.generateVersion(testUser, false);
        assertEquals("v1.0", version);
    }

    @Test
    public void testVersionGeneratorMinorIncrement() {
        testUser.setLatest("v1.3");
        String version = Generators.VersionGenerator.generateVersion(testUser, false);
        assertEquals("v1.4", version);
    }

    @Test
    public void testVersionGeneratorMajorIncrement() {
        testUser.setLatest("v1.3");
        String version = Generators.VersionGenerator.generateVersion(testUser, true);
        assertEquals("v2.0", version);
    }

    @Test
    public void testVersionGeneratorMultiMajorIncrement() {
        testUser.setLatest("v2.5");
        String version = Generators.VersionGenerator.generateVersion(testUser, true);
        assertEquals("v3.0", version);
    }

    // ==========================================
    // 2. ZIP SECURITY VALIDATION TESTS
    // ==========================================

    private byte[] createMockZip(String entryName, String content) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry(entryName);
            zos.putNextEntry(entry);
            zos.write(content.getBytes());
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    @Test
    public void testStaticFileOnlyValidationSuccess() throws IOException {
        byte[] zipBytes = createMockZip("index.html", "<html><body>Hello</body></html>");

        ValidationContext context = new ValidationContext();
        context.setUsername("jeshupatelg");
        context.setZipFileBytes(zipBytes);

        ValidationStep step = new StaticFileOnlyValidation();
        ValidationResult result = step.validate(context);

        assertTrue(result.isValid(), "Should pass for whitelisted extension .html");
    }

    @Test
    public void testStaticFileOnlyValidationBlacklist() throws IOException {
        byte[] zipBytes = createMockZip("exploit.php", "<?php echo 'malicious'; ?>");

        ValidationContext context = new ValidationContext();
        context.setUsername("jeshupatelg");
        context.setZipFileBytes(zipBytes);

        ValidationStep step = new StaticFileOnlyValidation();
        ValidationResult result = step.validate(context);

        assertFalse(result.isValid(), "Should reject blacklisted extension .php");
        assertTrue(result.getReason().contains("script file not permitted"), "Reason should specify blocked script: " + result.getReason());
    }

    @Test
    public void testStaticFileOnlyValidationNonStatic() throws IOException {
        byte[] zipBytes = createMockZip("config.yaml", "port: 8080");

        ValidationContext context = new ValidationContext();
        context.setUsername("jeshupatelg");
        context.setZipFileBytes(zipBytes);

        ValidationStep step = new StaticFileOnlyValidation();
        ValidationResult result = step.validate(context);

        assertFalse(result.isValid(), "Should reject non-static extension .yaml");
        assertTrue(result.getReason().contains("Non-static file extension"), "Reason should specify non-static error: " + result.getReason());
    }

    @Test
    public void testZipBombValidationPathTraversal() throws IOException {
        byte[] zipBytes = createMockZip("../traversal.html", "content");

        ValidationContext context = new ValidationContext();
        context.setUsername("jeshupatelg");
        context.setZipFileBytes(zipBytes);

        ValidationStep step = new ZipBombValidation();
        ValidationResult result = step.validate(context);

        assertFalse(result.isValid(), "Should reject directory traversal pattern");
        assertTrue(result.getReason().contains("Path traversal detected"), "Reason should mention path traversal: " + result.getReason());
    }

    @Test
    public void testZipBombValidationDepthCheck() throws IOException {
        byte[] zipBytes = createMockZip("dir1/dir2/dir3/dir4/dir5/dir6/index.html", "content");

        ValidationContext context = new ValidationContext();
        context.setUsername("jeshupatelg");
        context.setZipFileBytes(zipBytes);

        ValidationStep step = new ZipBombValidation();
        ValidationResult result = step.validate(context);

        assertFalse(result.isValid(), "Should reject directory depth exceeding limit of 5");
        assertTrue(result.getReason().contains("Nested folder depth exceeds limit"), "Reason should mention folder depth limit: " + result.getReason());
    }

    // ==========================================
    // 3. POLYMORPHIC BUSINESS FLOW TESTS
    // ==========================================

    @Test
    public void testVersionExistsValidationActive() {
        ActiveArtifact activeArt = new ActiveArtifact("v1.1", new ArrayList<>(), new byte[]{1, 2}, "/link", "desc", LocalDateTime.now(), false, testUser);
        testUser.getArtifacts().add(activeArt);

        ValidationContext context = new ValidationContext();
        context.setUser(testUser);
        context.setVersion("v1.1");

        ValidationStep step = new VersionExistsValidation();
        ValidationResult result = step.validate(context);

        assertTrue(result.isValid(), "Should find matching active version");
        assertNotNull(context.getTargetArtifact(), "Should cache target artifact inside context");
        assertTrue(context.getTargetArtifact() instanceof ActiveArtifact, "Cached artifact should be ActiveArtifact");
        assertEquals("v1.1", context.getTargetArtifact().getVersion());
    }

    @Test
    public void testVersionNotDeletedValidationOnDeletedArtifact() {
        DeletedArtifact deletedArt = new DeletedArtifact("v2.0", new ArrayList<>(), null, "archived", LocalDateTime.now(), true, testUser);
        testUser.getArtifacts().add(deletedArt);

        ValidationContext context = new ValidationContext();
        context.setUser(testUser);
        context.setVersion("v2.0");
        context.setTargetArtifact(deletedArt); // Simulates VersionExistsValidation resolution
        context.setSetActive(true); // Activating a deleted version

        ValidationStep step = new VersionNotDeletedValidation();
        ValidationResult result = step.validate(context);

        assertFalse(result.isValid(), "Should reject activation of deleted version");
        assertTrue(result.getReason().contains("because it has been deleted"));
    }
}
