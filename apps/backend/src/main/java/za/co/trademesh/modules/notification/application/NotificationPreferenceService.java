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
                .map(category ->
                        stored.getOrDefault(category, new NotificationPreference(userId, category, true, null)))
                .toList();
    }

    @Transactional
    public NotificationPreference set(UUID userId, NotificationCategory category, boolean emailEnabled) {
        return repository.savePreference(new NotificationPreference(userId, category, emailEnabled, clock.instant()));
    }
}
