/* @MENTEE_POWER (C)2025 */
package ru.mentee.power.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ConfigFilePath {
    private static final String DEFAULT_APP_PATH = "/application.properties";
    private static final String DEFAULT_SECRET_PATH = "/secret.properties";

    @Getter private final String appMainConfigPath;
    @Getter private final String appSecretPath;

    public ConfigFilePath() {
        this.appMainConfigPath = DEFAULT_APP_PATH;
        this.appSecretPath = DEFAULT_SECRET_PATH;
    }
}
