import { apiFetch } from "@/lib/api/client";
import {
    isIntegerFrom,
    isNonBlankString,
    isOptionalString,
    isRecord,
} from "@/lib/api/guards";

// The catalog serves upstream quotes unfiltered, so only _id is guaranteed:
// a quote without dialog or character is valid and must not fail the page.
export interface Quote {
    _id: string;
    dialog?: string | null;
    movie?: string | null;
    character?: string | null;
    id?: string | null;
    // Null (or absent) when the backend could not resolve the character.
    characterName?: string | null;
}

export interface QuotePage {
    docs: Quote[];
    total: number;
    limit: number;
    offset?: number | null;
    page: number;
    pages: number;
}

function isQuote(value: unknown): value is Quote {
    return (
        isRecord(value) &&
        isNonBlankString(value._id) &&
        isOptionalString(value.dialog) &&
        isOptionalString(value.movie) &&
        isOptionalString(value.character) &&
        isOptionalString(value.id) &&
        isOptionalString(value.characterName)
    );
}

// page may exceed pages (the caller redirects), and pages 0 means an empty catalog.
function isQuotePage(value: unknown): value is QuotePage {
    return (
        isRecord(value) &&
        Array.isArray(value.docs) &&
        value.docs.every(isQuote) &&
        isIntegerFrom(0, value.total) &&
        isIntegerFrom(1, value.limit) &&
        (value.offset === null ||
            value.offset === undefined ||
            isIntegerFrom(0, value.offset)) &&
        isIntegerFrom(1, value.page) &&
        isIntegerFrom(0, value.pages)
    );
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

    return apiFetch(
        `/api/catalog/quotes?${params.toString()}`,
        isQuotePage,
    );
}
