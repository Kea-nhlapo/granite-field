package za.co.trademesh.modules.notification.application;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
class MobileTemplateCatalog {

    private static final String RESOURCE_PATTERN = "classpath*:/notification-templates/mobile/*.properties";
    private static final int MAX_TEXT_LENGTH = 918;

    private final Map<TemplateIdentity, TemplateDefinition> templates;

    MobileTemplateCatalog(ResourcePatternResolver resources) {
        this.templates = load(resources);
    }

    RenderedMobileTemplate render(String templateKey, int templateVersion, Map<String, String> data) {
        TemplateDefinition template = templates.get(new TemplateIdentity(templateKey, templateVersion));
        if (template == null) {
            throw new IllegalArgumentException("Unsupported mobile template key or version");
        }
        Map<String, String> supplied = data == null ? Map.of() : Map.copyOf(data);
        if (!supplied.keySet().equals(template.variables())) {
            throw new IllegalArgumentException("Invalid mobile template data");
        }
        template.urlVariables().forEach(key -> validateUrl(supplied.get(key)));

        String text = template.text();
        for (String variable : template.variables()) {
            String value = supplied.get(variable);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Invalid mobile template data");
            }
            text = text.replace("{{" + variable + "}}", value.strip());
        }
        if (text.isBlank() || text.length() > MAX_TEXT_LENGTH || text.contains("{{")) {
            throw new IllegalArgumentException("Invalid rendered mobile template");
        }
        List<String> whatsappParameters = template.whatsappParameters().stream()
                .map(supplied::get)
                .map(String::strip)
                .toList();
        return new RenderedMobileTemplate(text, whatsappParameters, template.whatsappLanguage());
    }

    private static Map<TemplateIdentity, TemplateDefinition> load(ResourcePatternResolver resources) {
        Map<TemplateIdentity, TemplateDefinition> loaded = new LinkedHashMap<>();
        try {
            for (Resource resource : resources.getResources(RESOURCE_PATTERN)) {
                Properties properties = new Properties();
                try (var reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                    properties.load(reader);
                }
                String key = required(properties, "key");
                int version = positiveVersion(required(properties, "version"));
                Set<String> variables = csvSet(properties.getProperty("variables", ""));
                List<String> whatsappParameters = csvList(properties.getProperty("whatsapp.parameters", ""));
                Set<String> urlVariables = csvSet(properties.getProperty("url.variables", ""));
                if (!variables.containsAll(whatsappParameters) || !variables.containsAll(urlVariables)) {
                    throw new IllegalStateException("Mobile template metadata refers to an undeclared variable");
                }
                TemplateDefinition definition = new TemplateDefinition(
                        required(properties, "sms.text"),
                        variables,
                        urlVariables,
                        whatsappParameters,
                        required(properties, "whatsapp.language"));
                if (loaded.putIfAbsent(new TemplateIdentity(key, version), definition) != null) {
                    throw new IllegalStateException("Duplicate mobile template key and version");
                }
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Could not load mobile notification templates", failure);
        }
        if (loaded.isEmpty()) {
            throw new IllegalStateException("No mobile notification templates were found");
        }
        return Map.copyOf(loaded);
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Mobile template property is required: " + key);
        }
        return value.strip();
    }

    private static int positiveVersion(String raw) {
        try {
            int version = Integer.parseInt(raw);
            if (version > 0) {
                return version;
            }
        } catch (NumberFormatException ignored) {
            // Replaced by the safe startup error below.
        }
        throw new IllegalStateException("Mobile template version must be positive");
    }

    private static Set<String> csvSet(String raw) {
        return csvList(raw).stream().collect(Collectors.toUnmodifiableSet());
    }

    private static List<String> csvList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private static void validateUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Mobile template URL must use HTTPS");
        }
        URI uri;
        try {
            uri = URI.create(raw.strip());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Mobile template URL must use HTTPS", invalid);
        }
        boolean localHttp = "http".equalsIgnoreCase(uri.getScheme())
                && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
        if ((!"https".equalsIgnoreCase(uri.getScheme()) && !localHttp) || uri.getHost() == null) {
            throw new IllegalArgumentException("Mobile template URL must use HTTPS");
        }
    }

    record RenderedMobileTemplate(String text, List<String> whatsappParameters, String whatsappLanguage) {
        RenderedMobileTemplate {
            whatsappParameters = List.copyOf(whatsappParameters);
        }
    }

    private record TemplateIdentity(String key, int version) {}

    private record TemplateDefinition(
            String text,
            Set<String> variables,
            Set<String> urlVariables,
            List<String> whatsappParameters,
            String whatsappLanguage) {}
}
