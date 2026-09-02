export class ApiError extends Error {
    public readonly code: string;
    public readonly status: number;

    public constructor(code: string, message: string, status: number) {
        super(message);
        this.name = "ApiError";
        this.code = code;
        this.status = status;
    }
}

export function isUnauthorized(error: unknown): boolean {
    return error instanceof ApiError && error.status === 401;
}

export function isForbidden(error: unknown): boolean {
    return error instanceof ApiError && error.status === 403;
}
