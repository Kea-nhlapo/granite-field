package za.co.trademesh.modules.notification.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.notification.domain.NotificationContactPoint;
import za.co.trademesh.modules.notification.domain.NotificationPreference;
import za.co.trademesh.modules.notification.domain.NotificationRepository;

@Service
public class NotificationContactService {

    private final NotificationRepository repository;
    private final NotificationDataProtector dataProtector;
    private final Clock clock;

    public NotificationContactService(
            NotificationRepository repository, NotificationDataProtector dataProtector, Clock clock) {
        this.repository = repository;
        this.dataProtector = dataProtector;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<NotificationContactPoint> get(UUID userId) {
        return repository.findContact(userId);
    }

    @Transactional
    public NotificationContactPoint save(UUID userId, String rawPhone, boolean smsConsent, boolean whatsappConsent) {
        String phone = PhoneNumbers.normalize(rawPhone);
        Instant now = clock.instant();
        Optional<NotificationContactPoint> current = repository.findContact(userId);
        Instant createdAt = current.map(NotificationContactPoint::createdAt).orElse(now);
        NotificationContactPoint contact = new NotificationContactPoint(
                userId,
                phone,
                dataProtector.fingerprint(phone),
                PhoneNumbers.lastFour(phone),
                smsConsent ? now : null,
                whatsappConsent ? now : null,
                createdAt,
                now);
        NotificationContactPoint saved = repository.saveContact(contact);
        if (!smsConsent || !whatsappConsent) {
            disableRevokedPreferences(userId, smsConsent, whatsappConsent, now);
        }
        return saved;
    }

    @Transactional
    public void delete(UUID userId) {
        Instant now = clock.instant();
        repository.deleteContact(userId);
        repository.disableMobilePreferences(userId, now);
    }

    private void disableRevokedPreferences(UUID userId, boolean smsConsent, boolean whatsappConsent, Instant now) {
        repository.findPreferences(userId).stream()
                .filter(preference ->
                        (!smsConsent && preference.smsEnabled()) || (!whatsappConsent && preference.whatsappEnabled()))
                .forEach(preference -> repository.savePreference(new NotificationPreference(
                        userId,
                        preference.category(),
                        preference.emailEnabled(),
                        smsConsent && preference.smsEnabled(),
                        whatsappConsent && preference.whatsappEnabled(),
                        now)));
    }
}
