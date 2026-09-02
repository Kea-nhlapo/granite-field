package za.co.trademesh.modules.supplier.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.trademesh.modules.supplier.application.SupplierSearchCatalog;

@Repository
class JdbcSupplierSearchCatalog implements SupplierSearchCatalog {

    private static final Set<String> SEARCH_FILLER = Set.of(
            "need",
            "find",
            "want",
            "supplier",
            "please",
            "ngifuna",
            "umhlinzeki",
            "ndifuna",
            "umthengisi",
            "verskaffer");

    private final JdbcTemplate jdbcTemplate;

    JdbcSupplierSearchCatalog(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Candidate> search(String query, int limit) {
        List<String> terms = java.util.Arrays.stream(
                        query.toLowerCase(java.util.Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(term -> term.length() >= 3)
                .filter(term -> !SEARCH_FILLER.contains(term))
                .distinct()
                .limit(5)
                .toList();
        if (terms.isEmpty()) {
            terms = List.of(query.toLowerCase(java.util.Locale.ROOT));
        }
        String match = terms.stream()
                .map(ignored ->
                        "(LOWER(business.legal_name) LIKE ? OR LOWER(COALESCE(business.trading_name, '')) LIKE ?)")
                .collect(java.util.stream.Collectors.joining(" OR "));
        List<Object> parameters = new ArrayList<>();
        terms.forEach(term -> {
            String pattern = "%" + term + "%";
            parameters.add(pattern);
            parameters.add(pattern);
        });
        parameters.add(limit);
        return jdbcTemplate.query(
                """
                SELECT supplier.id AS supplier_profile_id,
                       business.id AS business_id,
                       COALESCE(business.trading_name, business.legal_name) AS display_name,
                       business.registered_address,
                       trust.average_rating,
                       trust.delivery_success_rate
                  FROM supplier_profile supplier
                  JOIN business_profile business ON business.id = supplier.business_id
                 LEFT JOIN trust_public_summary trust ON trust.business_id = business.id
                 WHERE supplier.profile_status = 'REGISTERED'
                   AND (
                """ + match + """
                       )
                 ORDER BY trust.average_rating DESC NULLS LAST,
                          trust.delivery_success_rate DESC NULLS LAST,
                          display_name ASC,
                          supplier.id ASC
                 LIMIT ?
                """,
                (resultSet, rowNumber) -> new Candidate(
                        resultSet.getObject("supplier_profile_id", java.util.UUID.class),
                        resultSet.getObject("business_id", java.util.UUID.class),
                        resultSet.getString("display_name"),
                        resultSet.getString("registered_address"),
                        resultSet.getBigDecimal("average_rating"),
                        resultSet.getBigDecimal("delivery_success_rate")),
                parameters.toArray());
    }

    @Override
    public List<Candidate> listRegistered(int limit) {
        return jdbcTemplate.query(
                """
                SELECT supplier.id AS supplier_profile_id,
                       business.id AS business_id,
                       COALESCE(business.trading_name, business.legal_name) AS display_name,
                       business.registered_address,
                       trust.average_rating,
                       trust.delivery_success_rate
                  FROM supplier_profile supplier
                  JOIN business_profile business ON business.id = supplier.business_id
                  LEFT JOIN trust_public_summary trust ON trust.business_id = business.id
                 WHERE supplier.profile_status = 'REGISTERED'
                 ORDER BY trust.average_rating DESC NULLS LAST,
                          trust.delivery_success_rate DESC NULLS LAST,
                          display_name ASC,
                          supplier.id ASC
                 LIMIT ?
                """,
                (resultSet, rowNumber) -> new Candidate(
                        resultSet.getObject("supplier_profile_id", java.util.UUID.class),
                        resultSet.getObject("business_id", java.util.UUID.class),
                        resultSet.getString("display_name"),
                        resultSet.getString("registered_address"),
                        resultSet.getBigDecimal("average_rating"),
                        resultSet.getBigDecimal("delivery_success_rate")),
                limit);
    }
}
