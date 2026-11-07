import { HOME_IMAGE, pageMetadata } from "@/lib/seo/site";
import WhereInMiddleEarthGame from "./WhereInMiddleEarthGame";

export const metadata = pageMetadata({
    title: "Where in Middle-earth",
    description:
        "Ten places, one original map. Click where you think each place of Middle-earth lies, and the closer you are, the more you score. Three difficulty levels.",
    path: "/games/where-in-middle-earth",
    image: HOME_IMAGE,
});

// Rendering this page never talks to the backend: a game is created only when the player
// presses the start button (see WhereInMiddleEarthGame), so reloads, crawlers and prefetches
// cost nothing.
export default function WhereInMiddleEarthPage() {
    return <WhereInMiddleEarthGame />;
}
