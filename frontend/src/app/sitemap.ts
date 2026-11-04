import type { MetadataRoute } from "next";
import { PUBLIC_PATHS, SITE_URL } from "@/lib/seo/site";

// No lastModified: the project has no reliable per-page modification date, and an invented
// one is worse than none. Priority and changeFrequency are ignored by Google, so they are omitted.
export default function sitemap(): MetadataRoute.Sitemap {
  return PUBLIC_PATHS.map((path) => ({
    url: path === "/" ? SITE_URL : `${SITE_URL}${path}`,
  }));
}
