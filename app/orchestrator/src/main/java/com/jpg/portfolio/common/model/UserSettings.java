package com.jpg.portfolio.common.model;

import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public class UserSettings implements Serializable {
    private static final long serialVersionUID = 1L;

    private Boolean isLatestVersionActive = true;

    public UserSettings() {}

    public UserSettings(Boolean isLatestVersionActive) {
        this.isLatestVersionActive = isLatestVersionActive;
    }

    public Boolean getIsLatestVersionActive() {
        return isLatestVersionActive;
    }

    public void setIsLatestVersionActive(Boolean isLatestVersionActive) {
        this.isLatestVersionActive = isLatestVersionActive;
    }

    @Override
    public String toString() {
        return "UserSettings{" +
                "isLatestVersionActive=" + isLatestVersionActive +
                '}';
    }
}
