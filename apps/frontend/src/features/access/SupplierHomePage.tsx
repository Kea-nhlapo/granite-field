import { Card, Text, Title2, Title3 } from "@fluentui/react-components";
import { Link } from "react-router-dom";

import { useAccessStyles } from "./access.styles";

export default function SupplierHomePage() {
    const styles = useAccessStyles();

    return (
        <div className={styles.pageStack}>
            <Title2 as="h1">Home</Title2>
            <Text size={200} weight="semibold">
                What do you want to do?
            </Text>
            <div className={styles.tileGrid}>
                <Link
                    aria-label="Open a quote request"
                    className={styles.tileLink}
                    to="/supplier-invitations/guest/invitepath"
                >
                    <Card className={styles.tile}>
                        <Title3>Open a quote request</Title3>
                        <Text size={200}>Reply using your invite link</Text>
                    </Card>
                </Link>
            </div>
        </div>
    );
}
