export type AccountProfile = {
    firstName: string;
    lastName: string;
    businessName: string;
    registrationNumber: string;
    email: string;
    phoneNumber: string;
    businessId?: string;
};

const storageKey = "trademesh.account-profile";

export function readAccountProfile(): AccountProfile | undefined {
    const raw = sessionStorage.getItem(storageKey);
    if (!raw) {
        return undefined;
    }
    try {
        return JSON.parse(raw) as AccountProfile;
    } catch {
        return undefined;
    }
}

export function saveAccountDetails(
    profile: AccountProfile,
): { ok: true } | { ok: false } {
    try {
        sessionStorage.setItem(storageKey, JSON.stringify(profile));
        return { ok: true };
    } catch {
        return { ok: false };
    }
}

export function clearAccountProfile() {
    sessionStorage.removeItem(storageKey);
}

export function initialsFor(profile: AccountProfile | undefined) {
    const first = profile?.firstName?.trim().charAt(0) ?? "";
    const last = profile?.lastName?.trim().charAt(0) ?? "";
    return `${first}${last}`.toUpperCase() || "TM";
}

export function isLikelyEmail(value: string) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim());
}

export function isLikelyPhone(value: string) {
    return /^[+0-9][0-9\s-]{6,}$/.test(value.trim());
}
