export function StatusMessage({
    children,
    tone = "info",
}: {
    children: string;
    tone?: "info" | "error";
}) {
    if (tone === "error") {
        return (
            <p
                role="alert"
                className="text-sm"
                style={{ color: "var(--error)" }}
            >
                {children}
            </p>
        );
    }
    return (
        <p role="status" className="text-sm text-gray-600">
            {children}
        </p>
    );
}
