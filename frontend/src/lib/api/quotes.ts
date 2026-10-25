import { apiFetch } from "@/lib/api/client";

export interface Quote {
    _id: string;
    dialog: string;
    movie: string;
    character: string;
    id: string;
}

export interface QuotePage {
    docs: Quote[];
    total: number;
    limit: number;
    offset: number | null;
    page: number;
    pages: number;
}

export async function getQuotes(
    page = 1,
    limit = 20,
): Promise<QuotePage> {
    if (!Number.isInteger(page) || page < 1) {
        throw new Error("page must be a positive integer");
    }

    if (!Number.isInteger(limit) || limit < 1 || limit > 100) {
        throw new Error("limit must be an integer between 1 and 100");
    }

    const params = new URLSearchParams({
        page: String(page),
        limit: String(limit),
    });

    return apiFetch<QuotePage>(
        `/api/catalog/quotes?${params.toString()}`,
    );
}
