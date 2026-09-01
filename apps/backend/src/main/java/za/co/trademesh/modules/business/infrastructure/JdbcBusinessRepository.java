package za.co.trademesh.modules.business.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.trademesh.modules.business.domain.BusinessLifecycleStatus;
import za.co.trademesh.modules.business.domain.BusinessProfile;
import za.co.trademesh.modules.business.domain.BusinessRepository;
import za.co.trademesh.modules.business.domain.BusinessVerificationStatus;
import za.co.trademesh.modules.business.domain.OnboardingState;
import za.co.trademesh.modules.business.domain.RegisteredBusinessOnboarding;
import za.co.trademesh.modules.business.domain.RegistrationNumber;

@Repository
class JdbcBusinessRepository implements BusinessRepository {

    private final JdbcTemplate jdbcTemplate;

    JdbcBusinessRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<RegisteredBusinessOnboarding> findOnboardingById(UUID onboardingId) {
        return findOneOnboarding("""
            SELECT id, owner_user_id, registration_number, legal_name, trading_name,
                   registered_address, registry_reference, state, business_id,
                   created_at, confirmed_at
            FROM business_registered_onboarding
            WHERE id = ?
            """, onboardingId);
    }

    @Override
    public Optional<RegisteredBusinessOnboarding> findOnboardingByRegistrationNumber(
            RegistrationNumber registrationNumber) {
        return findOneOnboarding("""
            SELECT id, owner_user_id, registration_number, legal_name, trading_name,
                   registered_address, registry_reference, state, business_id,
                   created_at, confirmed_at
            FROM business_registered_onboarding
            WHERE registration_number = ?
            """, registrationNumber.value());
    }

    @Override
    public Optional<BusinessProfile> findBusinessById(UUID businessId) {
        List<BusinessProfile> rows = jdbcTemplate.query("""
            SELECT id, registration_number, legal_name, trading_name, registered_address,
                   verification_status, lifecycle_status, confirmed_by_user_id, created_at
            FROM business_profile
            WHERE id = ?
            """, this::mapBusiness, businessId);
        return rows.stream().findFirst();
    }

    @Override
    public boolean businessExists(RegistrationNumber registrationNumber) {
        Boolean exists = jdbcTemplate.queryForObject("""
            SELECT EXISTS (
                SELECT 1 FROM business_profile WHERE registration_number = ?
            )
            """, Boolean.class, registrationNumber.value());
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public void saveOnboarding(RegisteredBusinessOnboarding onboarding) {
        jdbcTemplate.update(
                """
            INSERT INTO business_registered_onboarding (
                id, owner_user_id, registration_number, legal_name, trading_name,
                registered_address, registry_reference, state, business_id,
                created_at, confirmed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
                onboarding.id(),
                onboarding.ownerUserId(),
                onboarding.registrationNumber().value(),
                onboarding.legalName(),
                onboarding.tradingName(),
                onboarding.registeredAddress(),
                onboarding.registryReference(),
                onboarding.state().name(),
                onboarding.businessId(),
                toOffsetDateTime(onboarding.createdAt()),
                toOffsetDateTime(onboarding.confirmedAt()));
    }

    @Override
    public void saveBusiness(BusinessProfile business) {
        jdbcTemplate.update(
                """
            INSERT INTO business_profile (
                id, registration_number, legal_name, trading_name, registered_address,
                verification_status, lifecycle_status, confirmed_by_user_id, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
                business.id(),
                business.registrationNumber().value(),
                business.legalName(),
                business.tradingName(),
                business.registeredAddress(),
                business.verificationStatus().name(),
                business.lifecycleStatus().name(),
                business.confirmedByUserId(),
                toOffsetDateTime(business.createdAt()));
    }

    @Override
    public boolean confirmOnboarding(RegisteredBusinessOnboarding onboarding) {
        int updated = jdbcTemplate.update(
                """
            UPDATE business_registered_onboarding
            SET state = ?, business_id = ?, confirmed_at = ?
            WHERE id = ? AND state = 'PENDING_CONFIRMATION'
            """,
                onboarding.state().name(),
                onboarding.businessId(),
                toOffsetDateTime(onboarding.confirmedAt()),
                onboarding.id());
        return updated == 1;
    }

    private Optional<RegisteredBusinessOnboarding> findOneOnboarding(String sql, Object parameter) {
        List<RegisteredBusinessOnboarding> rows = jdbcTemplate.query(sql, this::mapOnboarding, parameter);
        return rows.stream().findFirst();
    }

    private RegisteredBusinessOnboarding mapOnboarding(ResultSet resultSet, int rowNumber) throws SQLException {
        OffsetDateTime confirmedAt = resultSet.getObject("confirmed_at", OffsetDateTime.class);
        return new RegisteredBusinessOnboarding(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("owner_user_id", UUID.class),
                new RegistrationNumber(resultSet.getString("registration_number")),
                resultSet.getString("legal_name"),
                resultSet.getString("trading_name"),
                resultSet.getString("registered_address"),
                resultSet.getString("registry_reference"),
                OnboardingState.valueOf(resultSet.getString("state")),
                resultSet.getObject("business_id", UUID.class),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                confirmedAt == null ? null : confirmedAt.toInstant());
    }

    private BusinessProfile mapBusiness(ResultSet resultSet, int rowNumber) throws SQLException {
        return new BusinessProfile(
                resultSet.getObject("id", UUID.class),
                new RegistrationNumber(resultSet.getString("registration_number")),
                resultSet.getString("legal_name"),
                resultSet.getString("trading_name"),
                resultSet.getString("registered_address"),
                BusinessVerificationStatus.valueOf(resultSet.getString("verification_status")),
                BusinessLifecycleStatus.valueOf(resultSet.getString("lifecycle_status")),
                resultSet.getObject("confirmed_by_user_id", UUID.class),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private static OffsetDateTime toOffsetDateTime(java.time.Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
