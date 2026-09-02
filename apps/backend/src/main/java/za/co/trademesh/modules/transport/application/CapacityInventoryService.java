package za.co.trademesh.modules.transport.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.transport.domain.TransportRepository;

@Service
class CapacityInventoryService implements CapacityOfferInventory {

    private final TransportRepository repository;
    private final Clock clock;

    CapacityInventoryService(TransportRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public boolean tryReserve(UUID offerId, BigDecimal weightKg, BigDecimal volumeCubicMetres) {
        return repository.tryReserveCapacity(
                requiredId(offerId), positive(weightKg), positive(volumeCubicMetres), clock.instant());
    }

    @Override
    @Transactional
    public boolean release(UUID offerId, BigDecimal weightKg, BigDecimal volumeCubicMetres) {
        return repository.releaseCapacity(requiredId(offerId), positive(weightKg), positive(volumeCubicMetres));
    }

    private static UUID requiredId(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("An offer ID is required");
        }
        return value;
    }

    private static BigDecimal positive(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        try {
            BigDecimal normalized = value.setScale(3, RoundingMode.UNNECESSARY);
            if (normalized.signum() <= 0 || normalized.precision() > 15) {
                throw new IllegalArgumentException("Capacity must be positive");
            }
            return normalized;
        } catch (ArithmeticException tooPrecise) {
            throw new IllegalArgumentException("Capacity supports at most three decimal places", tooPrecise);
        }
    }
}
