import {
    createContext,
    useContext,
    useEffect,
    useMemo,
    useState,
    type ReactNode,
} from "react";

import { FluentProvider } from "@fluentui/react-components";

import { momoDarkTheme, momoLightTheme } from "../../app/momo-theme";
import {
    applyTheme,
    persistTheme,
    resolveTheme,
    type ThemeName,
} from "./theme";

type ThemeContextValue = {
    theme: ThemeName;
    setTheme: (theme: ThemeName) => void;
};

const ThemeContext = createContext<ThemeContextValue | null>(null);

export function ThemeProvider({ children }: { children: ReactNode }) {
    const [theme, setThemeState] = useState<ThemeName>("light");

    useEffect(() => {
        const next = resolveTheme();
        setThemeState(next);
        applyTheme(next);
    }, []);

    const value = useMemo<ThemeContextValue>(
        () => ({
            theme,
            setTheme: (next) => {
                setThemeState(next);
                persistTheme(next);
            },
        }),
        [theme],
    );

    return (
        <ThemeContext.Provider value={value}>
            <FluentProvider
                theme={theme === "dark" ? momoDarkTheme : momoLightTheme}
                style={{ minHeight: "100dvh" }}
            >
                {children}
            </FluentProvider>
        </ThemeContext.Provider>
    );
}

export function useTheme() {
    const value = useContext(ThemeContext);
    if (!value) {
        throw new Error("useTheme must be used within ThemeProvider");
    }
    return value;
}
