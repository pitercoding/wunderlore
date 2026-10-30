// A plain JSON object: null and arrays also have typeof "object".
export function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null && !Array.isArray(value);
}

export function isNonBlankString(value: unknown): value is string {
    return typeof value === "string" && value.trim() !== "";
}

// For fields the backend may send as null; a missing key means the same thing.
export function isOptionalString(
    value: unknown,
): value is string | null | undefined {
    return value === null || value === undefined || typeof value === "string";
}

export function isIntegerFrom(minimum: number, value: unknown): value is number {
    return (
        typeof value === "number" &&
        Number.isInteger(value) &&
        value >= minimum
    );
}
