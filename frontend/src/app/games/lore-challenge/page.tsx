import { LORE_CHALLENGE_IMAGE, pageMetadata } from "@/lib/seo/site";
import LoreChallengeGame from "./LoreChallengeGame";

export const metadata = pageMetadata({
    title: "Lore Challenge",
    description:
        "Test what you know about the characters, places, people, history and events of Tolkien's world in a ten-question trivia game with three difficulty levels.",
    path: "/games/lore-challenge",
    image: LORE_CHALLENGE_IMAGE,
});

// Rendering this page never talks to the backend: a match is created only when the player
// presses the start button (see LoreChallengeGame), so reloads, crawlers and prefetches cost
// nothing.
export default function LoreChallengePage() {
    return <LoreChallengeGame />;
}
