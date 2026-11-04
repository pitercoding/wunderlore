import { pageMetadata } from "@/lib/seo/site";
import WhoSaidItGame from "./WhoSaidItGame";

export const metadata = pageMetadata({
    title: "Who Said It?",
    description:
        "Read a line of dialogue from Middle-earth and work out which character said it. A quote-guessing game for readers of Tolkien.",
    path: "/games/who-said-it",
});

// Rendering this page never talks to the backend: a round is created only when the
// player presses Play (see WhoSaidItGame), so reloads, crawlers and prefetches cost nothing.
export default function WhoSaidItPage() {
    return <WhoSaidItGame />;
}
