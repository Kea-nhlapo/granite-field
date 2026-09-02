import { BrowserRouter, useRoutes } from "react-router-dom";

import { SessionProvider } from "../features/access/SessionProvider";
import { appRoutes } from "./app-routes";
import { AppErrorBoundary } from "./AppErrorBoundary";
import { FluentAppProvider } from "./FluentAppProvider";

export function AppRoutes() {
    return useRoutes(appRoutes);
}

export function App() {
    return (
        <FluentAppProvider>
            <AppErrorBoundary>
                <SessionProvider>
                    <BrowserRouter>
                        <AppRoutes />
                    </BrowserRouter>
                </SessionProvider>
            </AppErrorBoundary>
        </FluentAppProvider>
    );
}
