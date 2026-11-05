import { pageMetadata, WHO_AM_I_IMAGE } from "@/lib/seo/site";
import WhoAmIGame from "./WhoAmIGame";

export const metadata = pageMetadata({
    title: "Who Am I?",
    description:
        "Five clues. One character. Guess who from progressive clues about Tolkien's world: the fewer clues you need, the more you score. Three difficulty levels.",
    path: "/games/who-am-i",
    image: WHO_AM_I_IMAGE,
});

// Rendering this page never talks to the backend: a game is created only when the player
// presses the start button (see WhoAmIGame), so reloads, crawlers and prefetches cost nothing.
export default function WhoAmIPage() {
    return <WhoAmIGame />;
}
