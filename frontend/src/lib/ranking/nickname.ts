// Mirrors the backend's NicknamePolicy so the form can answer before a request is made. The
// backend is the authority: it validates again, and its answer wins.

export const NICKNAME_MIN_LENGTH = 3;
export const NICKNAME_MAX_LENGTH = 16;

export const NICKNAME_RULES =
    "3 to 16 characters: letters, numbers, spaces, underscores or hyphens.";

const ALLOWED = /^[\p{L}\p{N}_ -]+$/u;

// Same list and same comparison as the backend: lower case, without spaces, underscores, hyphens
// and trailing digits.
const RESERVED = new Set([
    "admin", "administrator", "administrador", "adm", "moderator", "moderador", "mod",
    "system", "sistema", "root", "staff", "support", "suporte", "owner", "official",
    "oficial", "wunderlore", "wunderloreteam", "server", "servidor", "null", "undefined",
]);

// Unicode NFC, trimmed, runs of spaces reduced to one.
export function normalizeNickname(value: string): string {
    return value.normalize("NFC").trim().replace(/ {2,}/g, " ");
}

// The message to show, or null when the nickname is acceptable.
export function validateNickname(value: string): string | null {
    const nickname = normalizeNickname(value);
    const length = [...nickname].length;

    if (length < NICKNAME_MIN_LENGTH || length > NICKNAME_MAX_LENGTH || !ALLOWED.test(nickname)) {
        return `Use ${NICKNAME_RULES}`;
    }

    const key = nickname
        .toLowerCase()
        .replace(/[ _-]/g, "")
        .replace(/\p{N}+$/u, "");

    if (RESERVED.has(key)) {
        return "That nickname is not available. Please choose another one.";
    }

    return null;
}
