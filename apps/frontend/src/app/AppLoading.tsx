import { Spinner } from "@fluentui/react-components";

import { useAppStyles } from "./app.styles";

export function AppLoading() {
    const styles = useAppStyles();

    return (
        <main className={styles.page} aria-busy="true" aria-live="polite">
            <div className={styles.shell}>
                <div className={styles.brandBar} />
                <div className={styles.card}>
                    <Spinner
                        label="Loading application..."
                        labelPosition="after"
                    />
                </div>
            </div>
        </main>
    );
}
