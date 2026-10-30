"use client";

interface GlobalErrorProps {
    error: unknown;
    retry: () => void;
}

// Replaces the root layout, so globals.css and its theme variables are not
// loaded here: inline styles and system colors keep light and dark mode working.
export default function GlobalError({ retry }: GlobalErrorProps) {
    return (
        <html lang="en" style={{ colorScheme: "light dark" }}>
            <head>
                <title>Wunderlore</title>
            </head>
            <body
                style={{
                    margin: 0,
                    background: "Canvas",
                    color: "CanvasText",
                    fontFamily: "Arial, Helvetica, sans-serif",
                }}
            >
                <main
                    style={{
                        width: "min(100% - 2rem, 760px)",
                        margin: "0 auto",
                        padding: "3rem 0",
                    }}
                >
                    <h1 style={{ margin: "0 0 0.75rem" }}>Something went wrong</h1>
                    <p role="alert" style={{ margin: 0 }}>
                        An unexpected error occurred. You can try again.
                    </p>

                    <button
                        type="button"
                        onClick={() => retry()}
                        style={{
                            marginTop: "1.5rem",
                            padding: "0.9rem 1rem",
                            border: "1px solid currentColor",
                            borderRadius: "0.65rem",
                            background: "transparent",
                            color: "inherit",
                            font: "inherit",
                            cursor: "pointer",
                        }}
                    >
                        Try again
                    </button>
                </main>
            </body>
        </html>
    );
}
