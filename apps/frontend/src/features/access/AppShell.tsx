import { Button } from "@fluentui/react-components";
import { NavLink, Outlet } from "react-router-dom";

import { useAccessStyles } from "./access.styles";
import { hasAnyRole } from "./roles";
import { useSession } from "./SessionProvider";

export function AppShell() {
    const styles = useAccessStyles();
    const { logout, session } = useSession();

    const showInternalRisk =
        session !== null &&
        hasAnyRole(session.roles, ["INTERNAL_RISK_ANALYST", "ADMINISTRATOR"]);

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
