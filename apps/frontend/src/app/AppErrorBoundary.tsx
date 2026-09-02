import {
    MessageBar,
    MessageBarBody,
    MessageBarTitle,
} from "@fluentui/react-components";
import { Component, type ErrorInfo, type ReactNode } from "react";

import { useAppStyles } from "./app.styles";

type AppErrorBoundaryProps = {
    children: ReactNode;
};

type AppErrorBoundaryState = {
    hasError: boolean;
};

function AppErrorState() {
    const styles = useAppStyles();

    return (
        <main className={styles.page} role="alert">
            <div className={styles.shell}>
                <div className={styles.brandBar} />
                <div className={styles.card}>
                    <MessageBar intent="error">
                        <MessageBarTitle>Something went wrong</MessageBarTitle>
                        <MessageBarBody>
                            Refresh the page and try again.
                        </MessageBarBody>
                    </MessageBar>
                </div>
            </div>
        </main>
    );
}

export class AppErrorBoundary extends Component<
    AppErrorBoundaryProps,
    AppErrorBoundaryState
> {
    public state: AppErrorBoundaryState = {
        hasError: false,
    };

    public static getDerivedStateFromError(): AppErrorBoundaryState {
        return {
            hasError: true,
        };
    }

    public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
        console.error("Unhandled render error", error, errorInfo);
    }

    public render() {
        if (this.state.hasError) {
            return <AppErrorState />;
        }

        return this.props.children;
    }
}
