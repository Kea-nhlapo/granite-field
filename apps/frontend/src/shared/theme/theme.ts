export type ThemeName = "light" | "dark";

const storageKey = "trademesh.theme";

export function readStoredTheme(): ThemeName | undefined {
    const value = localStorage.getItem(storageKey);
    return value === "light" || value === "dark" ? value : undefined;
}

export function systemTheme(): ThemeName {
    return window.matchMedia("(prefers-color-scheme: dark)").matches
        ? "dark"
        : "light";
}

export function resolveTheme(): ThemeName {
    return readStoredTheme() ?? systemTheme();
}

export function applyTheme(theme: ThemeName) {
    document.documentElement.dataset.theme = theme;
}

export function persistTheme(theme: ThemeName) {
    localStorage.setItem(storageKey, theme);
    applyTheme(theme);
}
