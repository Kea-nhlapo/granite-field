import type { ReactNode } from "react";

import { ThemeProvider } from "../shared/theme/ThemeProvider";

type FluentAppProviderProps = {
    children: ReactNode;
};

export function FluentAppProvider({ children }: FluentAppProviderProps) {
    return <ThemeProvider>{children}</ThemeProvider>;
}
