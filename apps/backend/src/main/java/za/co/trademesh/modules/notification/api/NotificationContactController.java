package za.co.trademesh.modules.notification.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import za.co.trademesh.modules.notification.application.NotificationContactService;
import za.co.trademesh.shared.security.AuthorizationService;

@RestController
@RequestMapping("/api/notification-contacts/phone")
public class NotificationContactController {

    private final NotificationContactService contacts;
    private final AuthorizationService authorization;

    public NotificationContactController(NotificationContactService contacts, AuthorizationService authorization) {
        this.contacts = contacts;
        this.authorization = authorization;
    }

    @GetMapping
    NotificationContactContracts.PhoneResponse get(Authentication authentication) {
        return contacts.get(authorization.authenticatedUserId(authentication))
                .map(NotificationContactContracts.PhoneResponse::from)
                .orElseGet(NotificationContactContracts.PhoneResponse::empty);
    }

    @PutMapping
    NotificationContactContracts.PhoneResponse save(
            @Valid @RequestBody NotificationContactContracts.SavePhoneRequest request, Authentication authentication) {
        return NotificationContactContracts.PhoneResponse.from(contacts.save(
                authorization.authenticatedUserId(authentication),
                request.phoneNumber(),
                request.smsConsent(),
                request.whatsappConsent()));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(Authentication authentication) {
        contacts.delete(authorization.authenticatedUserId(authentication));
    }
}
