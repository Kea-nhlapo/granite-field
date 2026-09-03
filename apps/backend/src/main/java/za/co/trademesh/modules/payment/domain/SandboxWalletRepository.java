package za.co.trademesh.modules.payment.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SandboxWalletRepository {

    boolean create(UUID userId, String displayName, String currency, BigDecimal openingBalance, Instant now);

    Optional<SandboxWallet> find(UUID userId);

    Optional<SandboxWallet> findForUpdate(UUID userId);

    boolean entryExists(String referenceKey);

    void update(UUID userId, BigDecimal availableBalance, BigDecimal heldBalance, Instant now);

    void add(SandboxWalletEntry entry);

    List<SandboxWalletEntry> entries(UUID userId, int limit);
}
