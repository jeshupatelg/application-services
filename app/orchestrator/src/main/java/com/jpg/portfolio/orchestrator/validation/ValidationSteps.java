package com.jpg.portfolio.orchestrator.validation;

import com.jpg.portfolio.common.model.Artifact;
import com.jpg.portfolio.common.model.DeletedArtifact;
import com.jpg.portfolio.common.model.UserImpl;
import com.jpg.portfolio.orchestrator.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// ==========================================
// 1. SECURITY FILTERS (RUN FIRST)
// ==========================================

@Component
@Order(1)
class ZipBombValidation implements ValidationStep {

    private static final long MAX_UNCOMPRESSED_SIZE = 20 * 1024 * 1024; // 20 MB
    private static final int MAX_FILE_COUNT = 500;
    private static final int MAX_NESTED_DEPTH = 5;
    private static final double MAX_COMPRESSION_RATIO = 100.0;

    @Override
    public ValidationResult validate(ValidationContext context) {
        byte[] bytes = context.getZipFileBytes();
        if (bytes == null || bytes.length == 0) {
            return ValidationResult.fail("Zip file is empty or null");
        }

        long compressedSize = bytes.length;
        long totalUncompressedSize = 0;
        int fileCount = 0;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                // 1. Directory Traversal protection
                if (name.contains("../") || name.contains("..\\") || name.contains("..")) {
                    return ValidationResult.fail("Security violation: Path traversal detected in entry: " + name);
                }

                // 2. Nesting Depth check
                int depth = name.split("[/\\\\]").length;
                if (depth > MAX_NESTED_DEPTH) {
                    return ValidationResult.fail("Security violation: Nested folder depth exceeds limit of " + MAX_NESTED_DEPTH + " in entry: " + name);
                }

                // If it is a directory, skip size accumulation
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }

                fileCount++;
                if (fileCount > MAX_FILE_COUNT) {
                    return ValidationResult.fail("Security violation: Total file count inside Zip exceeds limit of " + MAX_FILE_COUNT);
                }

                // Accumulate size by reading raw bytes to safely calculate uncompressed sizes (avoids header spoofing)
                byte[] buffer = new byte[4096];
                int len;
                long entryUncompressedSize = 0;
                while ((len = zis.read(buffer)) != -1) {
                    entryUncompressedSize += len;
                    totalUncompressedSize += len;

                    if (totalUncompressedSize > MAX_UNCOMPRESSED_SIZE) {
                        return ValidationResult.fail("Security violation: Total uncompressed file size exceeds limit of 20MB");
                    }
                }
                zis.closeEntry();

                // Calculate compression ratio dynamically
                if (entryUncompressedSize > 0) {
                    double ratio = (double) entryUncompressedSize / Math.max(1, entry.getCompressedSize());
                    if (ratio > MAX_COMPRESSION_RATIO && entry.getCompressedSize() > 0) {
                        return ValidationResult.fail("Security violation: Abnormal compression ratio detected in entry: " + name);
                    }
                }
            }
        } catch (IOException e) {
            return ValidationResult.fail("Failed to read Zip file structure: " + e.getMessage());
        }

        return ValidationResult.success();
    }

    @Override
    public boolean supports(ValidationScenario scenario) {
        return scenario == ValidationScenario.UPLOAD;
    }
}

@Component
@Order(2)
class StaticFileOnlyValidation implements ValidationStep {

    private static final Set<String> WHITELISTED_EXTENSIONS = new HashSet<>(Arrays.asList(
            "html", "htm", "css", "js", "json", "png", "jpg", "jpeg", "gif", "svg", "webp", "ico", "woff", "woff2", "ttf"
    ));

    private static final Set<String> BLACKLISTED_EXTENSIONS = new HashSet<>(Arrays.asList(
            "php", "jsp", "asp", "aspx", "sh", "bash", "exe", "bat", "cmd", "py", "pl", "rb", "class", "jar", "war"
    ));

    @Override
    public ValidationResult validate(ValidationContext context) {
        byte[] bytes = context.getZipFileBytes();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }

                String name = entry.getName();
                String ext = getFileExtension(name);

                if (ext == null || ext.isEmpty()) {
                    // Files without extension are generally blocked unless they are text/static (we enforce extensions for safety)
                    return ValidationResult.fail("Security violation: File without extension is not permitted: " + name);
                }

                String lowerExt = ext.toLowerCase();

                // Explicit blacklist check
                if (BLACKLISTED_EXTENSIONS.contains(lowerExt)) {
                    return ValidationResult.fail("Security violation: Executable or server-side script file not permitted in static sandbox: " + name);
                }

                // Whitelist validation
                if (!WHITELISTED_EXTENSIONS.contains(lowerExt)) {
                    return ValidationResult.fail("Security violation: Non-static file extension '" + ext + "' is not permitted in entry: " + name);
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            return ValidationResult.fail("Failed to parse Zip entries: " + e.getMessage());
        }

        return ValidationResult.success();
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filename.length() - 1) {
            return null;
        }
        return filename.substring(lastDot + 1);
    }

    @Override
    public boolean supports(ValidationScenario scenario) {
        return scenario == ValidationScenario.UPLOAD;
    }
}

// ==========================================
// 2. BUSINESS FLOW FILTERS (RUN LATER)
// ==========================================

@Component
@Order(3)
class UserExistsValidation implements ValidationStep {

    private final UserRepository userRepository;

    @Autowired
    public UserExistsValidation(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public ValidationResult validate(ValidationContext context) {
        String username = context.getUsername();
        if (username == null || username.trim().isEmpty()) {
            return ValidationResult.fail("Username cannot be empty");
        }

        UserImpl user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return ValidationResult.fail("User '" + username + "' not found in database");
        }

        // Cache the user in context so subsequent validators do not execute redundant DB queries
        context.setUser(user);
        return ValidationResult.success();
    }

    @Override
    public boolean supports(ValidationScenario scenario) {
        return scenario == ValidationScenario.ACTIVATE || scenario == ValidationScenario.DELETE;//should also be enabled for upload
    }
}

@Component
@Order(4)
class VersionExistsValidation implements ValidationStep {

    @Override
    public ValidationResult validate(ValidationContext context) {
        UserImpl user = context.getUser();
        String requestedVersion = context.getVersion();

        if (requestedVersion == null || requestedVersion.trim().isEmpty()) {
            return ValidationResult.fail("Version string cannot be empty");
        }

        Artifact targetArtifact = null;

        // Polymorphically scan the single unified list to resolve the requested version
        for (Artifact artifact : user.getArtifacts()) {
            if (artifact.getVersion().equalsIgnoreCase(requestedVersion)) {
                targetArtifact = artifact;
                break;
            }
        }

        if (targetArtifact == null) {
            return ValidationResult.fail("Version '" + requestedVersion + "' not found for user '" + user.getUserName() + "'");
        }

        // Cache the fully resolved polymorphic Artifact inside the context
        context.setTargetArtifact(targetArtifact);
        return ValidationResult.success();
    }

    @Override
    public boolean supports(ValidationScenario scenario) {
        return scenario == ValidationScenario.ACTIVATE || scenario == ValidationScenario.DELETE;
    }
}

@Component
@Order(5)
class VersionNotActiveValidation implements ValidationStep {

    @Override
    public ValidationResult validate(ValidationContext context) {
        UserImpl user = context.getUser();
        String targetVersion = context.getVersion();
        String activeVersion = user.getActive();

        if (context.isSetActive()) {
            if (activeVersion != null && activeVersion.equalsIgnoreCase(targetVersion)) {
                return ValidationResult.fail("Version '" + targetVersion + "' is already the active version");
            }
        } else {
            if (activeVersion != null && activeVersion.equalsIgnoreCase(targetVersion)) {
                return ValidationResult.fail("Version '" + targetVersion + "' is currently active and cannot be deleted");
            }
        }

        return ValidationResult.success();
    }

    @Override
    public boolean supports(ValidationScenario scenario) {
        return scenario == ValidationScenario.ACTIVATE || scenario == ValidationScenario.DELETE;
    }
}

@Component
@Order(6)
class VersionNotDeletedValidation implements ValidationStep {

    @Override
    public ValidationResult validate(ValidationContext context) {
        Artifact artifact = context.getTargetArtifact();
        String targetVersion = context.getVersion();

        // Safe, native polymorphic type inspection instead of list scans
        if (artifact instanceof DeletedArtifact) {
            if (context.isSetActive()) {
                return ValidationResult.fail("Cannot activate version '" + targetVersion + "' because it has been deleted");
            } else {
                return ValidationResult.fail("Version '" + targetVersion + "' is already deleted");
            }
        }

        return ValidationResult.success();
    }

    @Override
    public boolean supports(ValidationScenario scenario) {
        return scenario == ValidationScenario.ACTIVATE || scenario == ValidationScenario.DELETE;
    }
}
