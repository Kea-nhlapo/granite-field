import { FluentProvider } from "@fluentui/react-components";
import type { ReactNode } from "react";

import { momoLightTheme } from "./momo-theme";

type FluentAppProviderProps = {
    children: ReactNode;
};

export function FluentAppProvider({ children }: FluentAppProviderProps) {
    return (
        <FluentProvider theme={momoLightTheme} style={{ minHeight: "100dvh" }}>
            {children}
        </FluentProvider>
    );
}
