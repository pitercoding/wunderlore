import { readFileSync } from "node:fs";
import path from "node:path";
import { describe, expect, it, vi } from "vitest";

// layout.tsx calls the next/font loaders at import time, which only work inside Next's compiler.
vi.mock("next/font/google", () => {
    const font = () => ({ variable: "font-var" });
    return { Geist: font, Geist_Mono: font, Newsreader: font };
});

import { metadata as rootMetadata } from "@/app/layout";
import { metadata as homeMetadata } from "@/app/page";
import { metadata as quotesMetadata } from "@/app/quotes/page";
import { metadata as whoSaidItMetadata } from "@/app/games/who-said-it/page";
import { metadata as whichCharacterMetadata } from "@/app/games/which-character/page";
import { metadata as loreChallengeMetadata } from "@/app/games/lore-challenge/page";
import { metadata as whoAmIMetadata } from "@/app/games/who-am-i/page";
import { metadata as whereInMiddleEarthMetadata } from "@/app/games/where-in-middle-earth/page";
import robots from "@/app/robots";
import sitemap from "@/app/sitemap";
import {
    HOME_IMAGE,
    LORE_CHALLENGE_IMAGE,
    PUBLIC_PATHS,
    SITE_URL,
    WHICH_CHARACTER_IMAGE,
    WHO_AM_I_IMAGE,
} from "./site";

const pages = [
    { name: "home", metadata: homeMetadata, path: "/" },
    { name: "quotes", metadata: quotesMetadata, path: "/quotes" },
    { name: "who-said-it", metadata: whoSaidItMetadata, path: "/games/who-said-it" },
    { name: "which-character", metadata: whichCharacterMetadata, path: "/games/which-character" },
    { name: "lore-challenge", metadata: loreChallengeMetadata, path: "/games/lore-challenge" },
    { name: "who-am-i", metadata: whoAmIMetadata, path: "/games/who-am-i" },
    {
      name: "where-in-middle-earth",
      metadata: whereInMiddleEarthMetadata,
      path: "/games/where-in-middle-earth",
    },
];

describe("root metadata", () => {
    it("sets the production origin, a title template and robots", () => {
        expect(String(rootMetadata.metadataBase)).toBe(`${SITE_URL}/`);
        expect(rootMetadata.title).toMatchObject({ template: "%s | Wunderlore" });
        expect(rootMetadata.robots).toEqual({ index: true, follow: true });
    });
});

describe("page metadata", () => {
    it.each(pages)("$name has a canonical, Open Graph and Twitter card", ({ metadata, path }) => {
        expect(metadata.alternates?.canonical).toBe(path);
        expect(metadata.openGraph).toMatchObject({ url: path, siteName: "Wunderlore" });
        expect(metadata.openGraph?.description).toBe(metadata.description);
        expect(metadata.twitter).toMatchObject({ card: "summary_large_image" });
    });

    it("gives every page its own description", () => {
        const descriptions = pages.map(({ metadata }) => metadata.description);

        expect(new Set(descriptions).size).toBe(pages.length);
    });

    it("uses the full title in share cards and the short one in <title>", () => {
        expect(quotesMetadata.title).toBe("Quotes");
        expect(quotesMetadata.openGraph?.title).toBe("Quotes | Wunderlore");
        expect(homeMetadata.title).toBeUndefined();
    });

    it("uses page-specific images only where they exist", () => {
        const firstImage = (m: typeof homeMetadata) =>
            (m.openGraph?.images as { url: string }[])[0].url;

        expect(firstImage(loreChallengeMetadata)).toBe(LORE_CHALLENGE_IMAGE.path);
        expect(firstImage(whichCharacterMetadata)).toBe(WHICH_CHARACTER_IMAGE.path);
        expect(firstImage(whoAmIMetadata)).toBe(WHO_AM_I_IMAGE.path);
        expect(firstImage(homeMetadata)).toBe(HOME_IMAGE.path);
    });

    it("never claims to be official (only 'unofficial' is allowed)", () => {
        for (const { metadata } of pages) {
            expect(metadata.description).not.toMatch(/(?<!un)official|authorized|affiliated/i);
        }
    });
});

describe("share images", () => {
    it.each([HOME_IMAGE, WHICH_CHARACTER_IMAGE, LORE_CHALLENGE_IMAGE, WHO_AM_I_IMAGE])(
        "$path exists in public/",
        ({ path: imagePath }) => {
            expect(() =>
                readFileSync(path.join(process.cwd(), "public", imagePath)),
            ).not.toThrow();
        },
    );

    it.each([HOME_IMAGE, WHICH_CHARACTER_IMAGE, LORE_CHALLENGE_IMAGE, WHO_AM_I_IMAGE])(
        "$path is a 1200x630 JPEG, not AVIF",
        ({ path: imagePath, width, height, type }) => {
            expect(imagePath).toMatch(/\.jpe?g$/);
            expect(type).toBe("image/jpeg");
            expect([width, height]).toEqual([1200, 630]);

            const file = readFileSync(path.join(process.cwd(), "public", imagePath));
            // JPEG files start with the SOI marker FF D8 FF.
            expect([...file.subarray(0, 3)]).toEqual([0xff, 0xd8, 0xff]);
        },
    );
});

describe("sitemap", () => {
    it("lists exactly the public pages with absolute URLs and no invented dates", () => {
        const entries = sitemap();

        expect(entries.map((e) => e.url)).toEqual([
            SITE_URL,
            `${SITE_URL}/quotes`,
            `${SITE_URL}/leaderboard`,
            `${SITE_URL}/games/who-said-it`,
            `${SITE_URL}/games/which-character`,
            `${SITE_URL}/games/lore-challenge`,
            `${SITE_URL}/games/who-am-i`,
            `${SITE_URL}/games/where-in-middle-earth`,
        ]);
        expect(entries).toHaveLength(PUBLIC_PATHS.length);
        expect(entries.every((e) => e.lastModified === undefined)).toBe(true);
    });
});

describe("robots", () => {
    it("allows everything and points to the sitemap", () => {
        expect(robots()).toEqual({
            rules: { userAgent: "*", allow: "/" },
            sitemap: `${SITE_URL}/sitemap.xml`,
        });
    });
});
