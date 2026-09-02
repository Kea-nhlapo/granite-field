import { Body1, Card, Title1 } from "@fluentui/react-components";

import { useAccessStyles } from "./access.styles";
import CustomerHomePage from "./CustomerHomePage";
import { hasAnyRole } from "./roles";
import { useSession } from "./SessionProvider";
import SupplierHomePage from "./SupplierHomePage";

export default function WorkspacePage() {
    const styles = useAccessStyles();
    const { session } = useSession();

    if (!session) {
        return null;
    }

    if (hasAnyRole(session.roles, ["BUSINESS_OWNER", "BUSINESS_MEMBER"])) {
        return <CustomerHomePage />;
    }

    if (hasAnyRole(session.roles, ["SUPPLIER"])) {
        return <SupplierHomePage />;
    }

    const roles = [...session.roles].join(", ");

    return (
        <Card className={styles.card}>
            <div className={styles.stack}>
                <Title1 as="h1" className={styles.title}>
                    Workspace
                </Title1>
                <Body1 as="p">Signed in as user {session.userId}</Body1>
                <Body1 as="p">Roles: {roles || "none"}</Body1>
            </div>
        </Card>
    );
}
