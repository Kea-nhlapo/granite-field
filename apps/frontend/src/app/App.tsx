import { lazy, Suspense } from "react";

import { AppErrorBoundary } from "./AppErrorBoundary";
import { AppLoading } from "./AppLoading";

const RootRoute = lazy(() => import("./routes/RootRoute"));

export function App() {
    return (
        <AppErrorBoundary>
            <Suspense fallback={<AppLoading />}>
                <RootRoute />
            </Suspense>
        </AppErrorBoundary>
    );
}
