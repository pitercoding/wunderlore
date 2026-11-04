import type { MetadataRoute } from "next";
import { SITE_URL } from "@/lib/seo/site";

// Everything public is crawlable, including /_next assets, which Google needs to render pages.
export default function robots(): MetadataRoute.Robots {
  return {
    rules: { userAgent: "*", allow: "/" },
    sitemap: `${SITE_URL}/sitemap.xml`,
  };
}
