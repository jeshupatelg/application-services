package com.jpg.portfolio.orchestrator.service;

import com.jpg.portfolio.common.model.UserImpl;
import org.springframework.stereotype.Service;

@Service
public class Generators {

    /**
     * VersionGenerator logic to calculate next version strings dynamically
     * <br/>{@code Future Note: Make it user level configurable through user setting and interface for versioning strategy}
     */
    public static class VersionGenerator {

        public static String generateVersion(UserImpl user, boolean isMajor) {
            String latest = user.getLatest();
            if (latest == null || latest.trim().isEmpty()) {
                return "v1.0";
            }

            try {
                // Normalize by stripping "v" or "V" prefixes
                String cleanLatest = latest.replaceAll("(?i)v", "").trim();
                String[] parts = cleanLatest.split("\\.");

                int major = Integer.parseInt(parts[0]);
                int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;

                if (isMajor) {
                    return "v" + (major + 1) + ".0";
                } else {
                    return "v" + major + "." + (minor + 1);
                }
            } catch (Exception e) {
                // Fallback in case of parsing abnormalities
                return "v1.0";
            }
        }
    }

    /**
     * DownloadLinkGenerator logic to formulate internal artifact download resource paths
     */
    public static class DownloadLinkGenerator {

        public static String generateDownloadLink(String username, String version) {
            if (username == null || version == null) {
                return "";
            }
            return "/app/portfolio/admin/download/" + version;
        }
    }
}
