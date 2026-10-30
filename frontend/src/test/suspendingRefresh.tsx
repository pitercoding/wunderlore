import { Suspense, use, useState, type ReactNode } from "react";

// A mocked router.refresh() returns at once, so its transition would never be
// seen as pending. The real one updates the router state and suspends until
// the server answers: Wrapper does the same, and respond() is the answer.
export function suspendingRefresh() {
    let respond!: () => void;
    const response = new Promise<void>((resolve) => {
        respond = resolve;
    });
    const router = { update: () => {} };

    function RouterState({ children }: { children: ReactNode }) {
        const [refreshing, setRefreshing] = useState(false);
        router.update = () => setRefreshing(true);

        if (refreshing) {
            use(response);
        }

        return children;
    }

    return {
        refresh: () => router.update(),
        respond,
        Wrapper: ({ children }: { children: ReactNode }) => (
            <Suspense fallback={null}>
                <RouterState>{children}</RouterState>
            </Suspense>
        ),
    };
}
