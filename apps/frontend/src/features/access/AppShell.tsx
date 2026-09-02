import { Button } from "@fluentui/react-components";
import { NavLink, Outlet } from "react-router-dom";

import { useAccessStyles } from "./access.styles";
import { hasAnyRole } from "./roles";
import { useSession } from "./SessionProvider";
import { mockBusinessId } from "../../shared/api/mocks/onboarding-handlers";

export function AppShell() {
    const styles = useAccessStyles();
    const { logout, session } = useSession();

    const showInternalRisk =
        session !== null &&
        hasAnyRole(session.roles, ["INTERNAL_RISK_ANALYST", "ADMINISTRATOR"]);
    const showOnboarding =
        session !== null && hasAnyRole(session.roles, ["BUSINESS_OWNER"]);
    const showDocuments =
        session !== null &&
        hasAnyRole(session.roles, ["BUSINESS_OWNER", "BUSINESS_MEMBER"]);
    const showProcurement = showDocuments;
    const showLogistics = showDocuments;

    return (
        <div className={styles.shell} data-testid="app-shell">
            <a className={styles.skipLink} href="#workspace-main">
                Skip to main content
            </a>
            <header className={styles.header}>
                <p className={styles.brand}>TradeMesh</p>
                <nav aria-label="Workspace" className={styles.nav}>
                    <NavLink
                        className={({ isActive }) =>
                            isActive
                                ? `${styles.navLink} ${styles.navLinkActive}`
                                : styles.navLink
                        }
                        end
                        to="/app"
                    >
                        Workspace
                    </NavLink>
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
                    {showOnboarding ? (
                        <NavLink
                            className={({ isActive }) =>
                                isActive
                                    ? `${styles.navLink} ${styles.navLinkActive}`
                                    : styles.navLink
                            }
                            to="/app/onboarding"
                        >
                            Onboarding
                        </NavLink>
                    ) : null}
                    {showDocuments ? (
                        <NavLink
                            className={({ isActive }) =>
                                isActive
                                    ? `${styles.navLink} ${styles.navLinkActive}`
                                    : styles.navLink
                            }
                            to={`/app/documents/${mockBusinessId}`}
                        >
                            Documents
                        </NavLink>
                    ) : null}
                    {showProcurement ? (
                        <NavLink
                            className={({ isActive }) =>
                                isActive
                                    ? `${styles.navLink} ${styles.navLinkActive}`
                                    : styles.navLink
                            }
                            to={`/app/procurement/${mockBusinessId}`}
                        >
                            Procurement
                        </NavLink>
                    ) : null}
                    {showLogistics ? (
                        <NavLink
                            className={({ isActive }) =>
                                isActive
                                    ? `${styles.navLink} ${styles.navLinkActive}`
                                    : styles.navLink
                            }
                            to={`/app/logistics/${mockBusinessId}`}
                        >
                            Logistics
                        </NavLink>
                    ) : null}
                    <Button
                        className={styles.touchTarget}
                        onClick={() => {
                            void logout();
                        }}
                    >
                        Sign out
                    </Button>
                </nav>
            </header>
            <div className={styles.main} id="workspace-main">
                <Outlet />
            </div>
        </div>
    );
}
