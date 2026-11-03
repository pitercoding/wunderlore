import WhoSaidItGame from "./WhoSaidItGame";

// Rendering this page never talks to the backend: a round is created only when the
// player presses Play (see WhoSaidItGame), so reloads, crawlers and prefetches cost nothing.
export default function WhoSaidItPage() {
    return <WhoSaidItGame />;
}
