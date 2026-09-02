import { Button } from "@fluentui/react-components";
import { NavLink, Outlet } from "react-router-dom";

import { clearAccountProfile } from "./account-profile";
import { useAccessStyles } from "./access.styles";
import { hasAnyRole } from "./roles";
import { useSession } from "./SessionProvider";

export function AppShell() {
    const styles = useAccessStyles();
    const { logout, session } = useSession();

    const isCustomer =
        session !== null &&
        hasAnyRole(session.roles, ["BUSINESS_OWNER", "BUSINESS_MEMBER"]);
    const isSupplier =
        session !== null && hasAnyRole(session.roles, ["SUPPLIER"]);
    const showInternalRisk =
        session !== null &&
        hasAnyRole(session.roles, ["INTERNAL_RISK_ANALYST", "ADMINISTRATOR"]);
    const showInsurance =
        session !== null && hasAnyRole(session.roles, ["INSURER"]);

    return (
        <div className={styles.shell} data-testid="app-shell">
            <a className={styles.skipLink} href="#workspace-main">
                Skip to main content
            </a>
            <header className={styles.header}>
                <p className={styles.brand}>TradeMesh</p>
            </header>
            <div className={styles.main} id="workspace-main">
                <Outlet />
            </div>
            <nav aria-label="Workspace" className={styles.nav}>
                <NavLink
                    className={({ isActive }) =>
                        isActive
                            ? `${styles.navLink} ${styles.navLinkActive}`
                            : styles.navLink
                    }
                    end
                    to={isSupplier ? "/app/supplier" : "/app"}
                >
                    Home
                </NavLink>
                {isCustomer ? (
                    <NavLink
                        className={({ isActive }) =>
                            isActive
                                ? `${styles.navLink} ${styles.navLinkActive}`
                                : styles.navLink
                        }
                        to="/app/settings"
                    >
                        Settings
                    </NavLink>
                ) : null}
                {showInternalRisk ? (
                    <NavLink
                        className={({ isActive }) =>
                            isActive
                                ? `${styles.navLink} ${styles.navLinkActive}`
                                : styles.navLink
                        }
                        to="/app/internal-risk"
                    >
                        Internal risk
                    </NavLink>
                ) : null}
                {showInsurance ? (
                    <NavLink
                        className={({ isActive }) =>
                            isActive
                                ? `${styles.navLink} ${styles.navLinkActive}`
                                : styles.navLink
                        }
                        to="/app/insurance"
                    >
                        Insurance
                    </NavLink>
                ) : null}
                <Button
                    appearance="subtle"
                    className={styles.touchTarget}
                    onClick={() => {
                        clearAccountProfile();
                        void logout();
                    }}
                >
                    Sign out
                </Button>
            </nav>
        </div>
    );
}
