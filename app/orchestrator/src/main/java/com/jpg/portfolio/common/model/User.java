package com.jpg.portfolio.common.model;

public interface User {
    UserSettings getUserSettings();
    void setUserSettings(UserSettings settings);

    String getAlias();
    void setAlias(String alias);

    byte[] getPhoto();
    void setPhoto(byte[] photo);

    String getUserName();
    void setUserName(String username);

    String getLatest();
    void setLatest(String latestVersion);

    String getActive();
    void setActive(String activeVersion);

    boolean isLatestVersionActive();
}
