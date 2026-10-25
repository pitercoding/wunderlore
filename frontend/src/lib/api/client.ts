const API_URL = process.env.API_URL;

export class ApiError extends Error {
    constructor(
        message: string,
        public readonly status: number,
    ) {
        super(message);
        this.name = "ApiError";
    }
}

export async function apiFetch<T>(
    path: string,
    init?: RequestInit,
): Promise<T> {
    if (!API_URL) {
        throw new Error("API_URL is not configured");
    }

    const response = await fetch(
        `${API_URL.replace(/\/$/, "")}${path}`,
        {
            ...init,
            headers: {
                Accept: "application/json",
                ...init?.headers,
            },
            cache: "no-store",
        },
    );

    if (!response.ok) {
        throw new ApiError(
            `API request failed with status ${response.status}`,
            response.status,
        );
    }

    return response.json() as Promise<T>;
}
