import LoreChallengeGame from "./LoreChallengeGame";

// Rendering this page never talks to the backend: a match is created only when the player
// presses the start button (see LoreChallengeGame), so reloads, crawlers and prefetches cost
// nothing.
export default function LoreChallengePage() {
    return <LoreChallengeGame />;
}
