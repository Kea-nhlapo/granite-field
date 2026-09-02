import { RouterProvider } from "react-router";
import type { RouterProviderProps } from "react-router";

import { SessionProvider } from "../features/access/session";
import { AppErrorBoundary } from "./AppErrorBoundary";

type AppProps = {
    router: RouterProviderProps["router"];
};

export function App({ router }: AppProps) {
    return (
        <AppErrorBoundary>
            <SessionProvider>
                <RouterProvider router={router} />
            </SessionProvider>
        </AppErrorBoundary>
    );
}
