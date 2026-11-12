import type { Metadata } from "next";

// Single source of truth for everything SEO-related: layout, pages, sitemap, robots and JSON-LD
// all read from here, so the domain and the site texts are never repeated.

// Production origin, confirmed by the project owner. No trailing slash.
export const SITE_URL = "https://wunderlore.vercel.app";
export const SITE_NAME = "Wunderlore";
export const SITE_LOCALE = "en_US";

export const DEFAULT_TITLE = "Wunderlore: Tolkien-inspired quotes and lore games";

export const SITE_DESCRIPTION =
  "Wunderlore is an interactive space for fantasy and literary lore, with quotes, characters and games inspired by Tolkien's world. An unofficial fan project.";

// Share-card images in public/images/share: 1200x630 JPEGs cropped from the page images in
// public/images (see ATTRIBUTIONS.md there). They are separate files because AVIF is not
// reliably supported by link-preview crawlers. Sizes are the real pixel dimensions of the
// files. Only the pages listed below have their own card; the others fall back to the home image.
export interface ShareImage {
  path: string;
  width: number;
  height: number;
  type: string;
  alt: string;
}

export const HOME_IMAGE: ShareImage = {
  path: "/images/share/home.jpg",
  width: 1200,
  height: 630,
  type: "image/jpeg",
  alt: "A sunlit enchanted forest with mist and soft light between the trees.",
};

export const WHICH_CHARACTER_IMAGE: ShareImage = {
  path: "/images/share/which-character.jpg",
  width: 1200,
  height: 630,
  type: "image/jpeg",
  alt: "A round hobbit-hole entrance under a leafy canopy.",
};

export const LORE_CHALLENGE_IMAGE: ShareImage = {
  path: "/images/share/lore-challenge.jpg",
  width: 1200,
  height: 630,
  type: "image/jpeg",
  alt: "An old library reading hall with tall windows and shelves fading into shadow.",
};

export const WHO_AM_I_IMAGE: ShareImage = {
  path: "/images/share/who-am-i.jpg",
  width: 1200,
  height: 630,
  type: "image/jpeg",
  alt: "Old iron keys laid out on dark crumpled paper.",
};

// Public, indexable routes. The sitemap is built from this list.
export const PUBLIC_PATHS = [
  "/",
  "/quotes",
  "/leaderboard",
  "/games/who-said-it",
  "/games/which-character",
  "/games/lore-challenge",
  "/games/who-am-i",
  "/games/where-in-middle-earth",
] as const;

interface PageSeo {
  // Short page name; the layout template appends " | Wunderlore". Omit for the home page.
  title?: string;
  description: string;
  path: (typeof PUBLIC_PATHS)[number];
  image?: ShareImage;
}

// Next.js replaces (does not merge) openGraph and twitter between a layout and a page, so each
// page has to state the full set. This helper keeps that in one place. Relative URLs are
// resolved against metadataBase set in the root layout.
export function pageMetadata({
  title,
  description,
  path,
  image = HOME_IMAGE,
}: PageSeo): Metadata {
  // openGraph/twitter titles do not go through the layout template, so build the full title.
  const fullTitle = title ? `${title} | ${SITE_NAME}` : DEFAULT_TITLE;
  const images = [
    {
      url: image.path,
      width: image.width,
      height: image.height,
      type: image.type,
      alt: image.alt,
    },
  ];

  return {
    ...(title ? { title } : {}),
    description,
    alternates: { canonical: path },
    openGraph: {
      type: "website",
      siteName: SITE_NAME,
      locale: SITE_LOCALE,
      url: path,
      title: fullTitle,
      description,
      images,
    },
    twitter: {
      card: "summary_large_image",
      title: fullTitle,
      description,
      images: images.map(({ url, alt }) => ({ url, alt })),
    },
  };
}
