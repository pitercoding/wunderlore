import { useSyncExternalStore } from "react";

// The last nickname used, kept only in this browser's localStorage (no cookie, no account).
// It is read through useSyncExternalStore, so the server render and the first client render both
// see "" and the saved value appears right after hydration, without a mismatch.

const STORAGE_KEY = "wunderlore:nickname";

const listeners = new Set<() => void>();

function subscribe(listener: () => void): () => void {
    listeners.add(listener);
    window.addEventListener("storage", listener);

    return () => {
        listeners.delete(listener);
        window.removeEventListener("storage", listener);
    };
}

// localStorage can be missing or throw (private windows, blocked site data): that only means
// there is no suggestion.
function readNickname(): string {
    try {
        return window.localStorage.getItem(STORAGE_KEY) ?? "";
    } catch {
        return "";
    }
}

export function saveNickname(nickname: string): void {
    try {
        window.localStorage.setItem(STORAGE_KEY, nickname);
    } catch {
        // Not being able to remember the nickname must never break publishing.
    }

    listeners.forEach((listener) => listener());
}

export function useSavedNickname(): string {
    return useSyncExternalStore(subscribe, readNickname, () => "");
}
