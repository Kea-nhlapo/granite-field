package za.co.trademesh.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.runtime")
public record RuntimeProperties(String environment) {
}
