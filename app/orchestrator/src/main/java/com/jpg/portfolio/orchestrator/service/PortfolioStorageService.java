package com.jpg.portfolio.orchestrator.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class PortfolioStorageService {

    // Configurable root directory where static websites are orchestrated
    @Value("${portfolio.shared.dir:/app/shared}")
    private String sharedDirRoot;

    public void deployPortfolio(String username, byte[] zipBytes) throws IOException {
        if (username == null || zipBytes == null || zipBytes.length == 0) {
            throw new IllegalArgumentException("Invalid deployment parameters");
        }

        File baseDir = new File(sharedDirRoot);
        File userDir = new File(baseDir, username);

        // 1. Force a clean, empty directory for this username to prevent leftover artifacts from previous versions
        deleteDirectoryRecursively(userDir);

        if (!userDir.mkdirs()) {
            throw new IOException("Failed to create deployment directory: " + userDir.getAbsolutePath());
        }

        // 2. Perform safe, traversal-checked decompression
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File targetFile = new File(userDir, entry.getName());

                // Double check to prevent Zip Slip / path traversal
                String canonicalDestination = targetFile.getCanonicalPath();
                String canonicalUserDir = userDir.getCanonicalPath();
                if (!canonicalDestination.startsWith(canonicalUserDir + File.separator) && 
                    !canonicalDestination.equals(canonicalUserDir)) {
                    throw new SecurityException("Security Violation: Entry is outside the destination directory: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    if (!targetFile.isDirectory() && !targetFile.mkdirs()) {
                        throw new IOException("Failed to create directory: " + targetFile.getAbsolutePath());
                    }
                } else {
                    // Create parent directories if they don't exist
                    File parent = targetFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create parent directories for: " + targetFile.getAbsolutePath());
                    }

                    // Write file out
                    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(targetFile))) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = zis.read(buffer)) != -1) {
                            bos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    public boolean isPortfolioDeployed(String username) {
        if (username == null) {
            return false;
        }
        File baseDir = new File(sharedDirRoot);
        File userDir = new File(baseDir, username);
        return userDir.exists() && userDir.isDirectory() && userDir.list() != null && userDir.list().length > 0;
    }

    public void deletePortfolio(String username) {
        if (username == null) {
            return;
        }
        File baseDir = new File(sharedDirRoot);
        File userDir = new File(baseDir, username);
        deleteDirectoryRecursively(userDir);
    }

    private void deleteDirectoryRecursively(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectoryRecursively(file);
                }
            }
            directory.delete();
        }
    }
}
