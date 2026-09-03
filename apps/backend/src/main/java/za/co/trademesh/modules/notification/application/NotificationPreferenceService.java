package za.co.trademesh.modules.notification.application;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.notification.domain.MobileChannel;
import za.co.trademesh.modules.notification.domain.NotificationCategory;
import za.co.trademesh.modules.notification.domain.NotificationPreference;
import za.co.trademesh.modules.notification.domain.NotificationRepository;

@Service
public class NotificationPreferenceService {

    private final NotificationRepository repository;
    private final Clock clock;

    public NotificationPreferenceService(NotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<NotificationPreference> get(UUID userId) {
        Map<NotificationCategory, NotificationPreference> stored = repository.findPreferences(userId).stream()
                .collect(Collectors.toMap(NotificationPreference::category, Function.identity()));
        return Arrays.stream(NotificationCategory.values())
                .map(category -> stored.getOrDefault(
                        category, new NotificationPreference(userId, category, true, false, false, null)))
                .toList();
    }

    @Transactional
    public NotificationPreference set(
            UUID userId,
            NotificationCategory category,
            Boolean emailEnabled,
            Boolean smsEnabled,
            Boolean whatsappEnabled) {
        if (emailEnabled == null && smsEnabled == null && whatsappEnabled == null) {
            throw NotificationException.emptyPreference();
        }
        NotificationPreference current = get(userId).stream()
                .filter(preference -> preference.category() == category)
                .findFirst()
                .orElseThrow();
        var contact = repository.findContact(userId);
        if (Boolean.TRUE.equals(smsEnabled)
                && contact.filter(value -> value.consented(MobileChannel.SMS)).isEmpty()) {
            throw NotificationException.consentRequired();
        }
        if (Boolean.TRUE.equals(whatsappEnabled)
                && contact.filter(value -> value.consented(MobileChannel.WHATSAPP))
                        .isEmpty()) {
            throw NotificationException.consentRequired();
        }
        return repository.savePreference(new NotificationPreference(
                userId,
                category,
                emailEnabled == null ? current.emailEnabled() : emailEnabled,
                smsEnabled == null ? current.smsEnabled() : smsEnabled,
                whatsappEnabled == null ? current.whatsappEnabled() : whatsappEnabled,
                clock.instant()));
    }

    @Transactional
    public void enableShipmentSms(UUID userId) {
        set(userId, NotificationCategory.SHIPMENT_UPDATE, true, true, false);
    }
}
