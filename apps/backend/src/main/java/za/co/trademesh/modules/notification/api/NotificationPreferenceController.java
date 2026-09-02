package za.co.trademesh.modules.notification.api;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.trademesh.modules.notification.application.NotificationPreferenceService;
import za.co.trademesh.modules.notification.domain.NotificationCategory;
import za.co.trademesh.shared.security.AuthorizationService;

@RestController
@RequestMapping("/api/notification-preferences")
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferences;
    private final AuthorizationService authorization;

    public NotificationPreferenceController(
            NotificationPreferenceService preferences, AuthorizationService authorization) {
        this.preferences = preferences;
        this.authorization = authorization;
    }

    @GetMapping
    NotificationPreferenceContracts.PreferencesResponse get(Authentication authentication) {
        return NotificationPreferenceContracts.PreferencesResponse.from(
                preferences.get(authorization.authenticatedUserId(authentication)));
    }

    @PutMapping("/{category}")
    NotificationPreferenceContracts.PreferenceResponse set(
            @org.springframework.web.bind.annotation.PathVariable NotificationCategory category,
            @Valid @RequestBody NotificationPreferenceContracts.UpdatePreferenceRequest request,
            Authentication authentication) {
        return NotificationPreferenceContracts.PreferenceResponse.from(
                preferences.set(authorization.authenticatedUserId(authentication), category, request.emailEnabled()));
    }
}
