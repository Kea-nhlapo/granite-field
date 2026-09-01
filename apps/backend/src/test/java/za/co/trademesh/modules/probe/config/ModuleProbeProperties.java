package za.co.trademesh.modules.probe.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.probe")
public record ModuleProbeProperties(String name) {
}
