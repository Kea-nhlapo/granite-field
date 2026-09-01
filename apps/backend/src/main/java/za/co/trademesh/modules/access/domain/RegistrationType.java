package za.co.trademesh.modules.access.domain;

import za.co.trademesh.shared.security.AccountRole;

/** Roles that a person may safely choose during public self-registration. */
public enum RegistrationType {
    BUSINESS_OWNER(AccountRole.BUSINESS_OWNER),
    SUPPLIER(AccountRole.SUPPLIER),
    TRANSPORTER(AccountRole.TRANSPORTER);

    private final AccountRole accountRole;

    RegistrationType(AccountRole accountRole) {
        this.accountRole = accountRole;
    }

    public AccountRole accountRole() {
        return accountRole;
    }
}
