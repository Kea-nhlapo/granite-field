import { Component, type ErrorInfo, type ReactNode } from "react";

type AppErrorBoundaryProps = {
    children: ReactNode;
};

type AppErrorBoundaryState = {
    hasError: boolean;
};

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
            return (
                <main className="p-8" role="alert">
                    <h1 className="text-xl font-semibold">
                        Something went wrong
                    </h1>
                    <p className="text-sm mt-2">
                        Refresh the page and try again.
                    </p>
                </main>
            );
        }

        return this.props.children;
    }
}
