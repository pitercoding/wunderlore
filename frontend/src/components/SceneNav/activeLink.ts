// Whether a nav link is the page being viewed. A link also stands for the pages beneath it
// (/games/who-said-it/anything), but never for a sibling that merely shares a prefix
// (/quotes-archive is not /quotes).
export function isActiveLink(pathname: string | null, href: string): boolean {
    if (!pathname) {
        return false;
    }

    return pathname === href || pathname.startsWith(`${href}/`);
}
