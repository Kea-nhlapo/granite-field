import { Body1, Card, Title1 } from "@fluentui/react-components";

import { useAccessStyles } from "./access.styles";

export default function InternalRiskPlaceholderPage() {
    const styles = useAccessStyles();

    return (
        <Card className={styles.card}>
            <div className={styles.stack}>
                <Title1 as="h1" className={styles.title}>
                    Internal risk
                </Title1>
                <Body1 as="p">
                    This area is limited to internal risk analysts and
                    administrators.
                </Body1>
            </div>
        </Card>
    );
}
