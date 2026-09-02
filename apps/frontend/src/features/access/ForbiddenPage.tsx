import { Body1, Card, Title1 } from "@fluentui/react-components";

import { useAccessStyles } from "./access.styles";

export function ForbiddenPage() {
    const styles = useAccessStyles();

    return (
        <main className={styles.page} data-testid="forbidden-page">
            <Card className={styles.card}>
                <div className={styles.stack}>
                    <Title1 as="h1" className={styles.title}>
                        Access denied
                    </Title1>
                    <Body1 as="p">
                        You do not have permission to open this workspace area.
                    </Body1>
                </div>
            </Card>
        </main>
    );
}
