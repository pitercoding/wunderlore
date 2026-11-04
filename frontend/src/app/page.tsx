import { existsSync } from "node:fs";
import path from "node:path";
import type { CSSProperties } from "react";
import Image from "next/image";
import Link from "next/link";
import PageShell from "@/components/PageShell/PageShell";
import styles from "./page.module.css";

// The forest photograph is an asset the project has to supply:
// frontend/public/images/enchanted-forest.{avif,webp,jpg}, 2400x1350 (16:9).
// Until it exists the scene still renders (gradients, mist, fireflies) and no
// request is made for it. Rebuild after adding the file.
const SCENE_FILES = ["enchanted-forest.avif", "enchanted-forest.webp", "enchanted-forest.jpg"];

function findSceneImage() {
  const dir = path.join(process.cwd(), "public", "images");
  const file = SCENE_FILES.find((name) => existsSync(path.join(dir, name)));

  return file ? `/images/${file}` : null;
}

// x/y are positions in the scene, s the size in px, d the loop in seconds.
const FIREFLIES = [
  { x: "12%", y: "62%", s: 3, d: 11, delay: 0.5, dx: 18, dy: 14 },
  { x: "24%", y: "78%", s: 2, d: 13, delay: 2.2, dx: -14, dy: 10 },
  { x: "31%", y: "48%", s: 2.5, d: 10, delay: 4, dx: 12, dy: 16 },
  { x: "8%", y: "40%", s: 2, d: 14, delay: 1, dx: 16, dy: 12 },
  { x: "42%", y: "70%", s: 3, d: 12, delay: 6, dx: -20, dy: 14 },
  { x: "58%", y: "66%", s: 2, d: 15, delay: 3, dx: 14, dy: 10 },
  { x: "66%", y: "80%", s: 3.5, d: 11, delay: 7.5, dx: -18, dy: 12 },
  { x: "74%", y: "52%", s: 2.5, d: 13, delay: 0.8, dx: 16, dy: 14 },
  { x: "84%", y: "68%", s: 3, d: 12, delay: 5, dx: -12, dy: 16 },
  { x: "90%", y: "44%", s: 2, d: 16, delay: 9, dx: 10, dy: 12 },
  { x: "19%", y: "28%", s: 1.8, d: 14, delay: 3.3, dx: 12, dy: 10 },
  { x: "78%", y: "30%", s: 2, d: 13, delay: 8, dx: -14, dy: 12 },
  { x: "50%", y: "88%", s: 2, d: 12, delay: 2.8, dx: 18, dy: 10 },
  { x: "36%", y: "34%", s: 1.6, d: 15, delay: 10, dx: 10, dy: 14 },
];

function Logo() {
  return (
    <svg className={styles.logo} viewBox="0 0 32 32" aria-hidden="true" focusable="false">
      <circle cx="16" cy="16" r="14.5" fill="none" stroke="currentColor" strokeOpacity="0.35" />
      <path
        d="M16 6c-5.5 4-7.5 9.5-1.5 19.5C20.5 15.5 21.5 10 16 6Z"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.4"
        strokeLinejoin="round"
      />
      <path
        d="M15.2 11.5v12.5M15.2 16.5l-3-2.4M15.2 20l3.4-2.6"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.1"
        strokeLinecap="round"
      />
    </svg>
  );
}

export default function HomePage() {
  const sceneImage = findSceneImage();

  return (
    <div className={styles.page}>
      <div className={styles.stage}>
        <div className={styles.scene} aria-hidden="true">
          {sceneImage && (
            <Image
              src={sceneImage}
              alt=""
              fill
              priority
              sizes="100vw"
              className={styles.image}
            />
          )}
          <div className={styles.light} />
          <div className={`${styles.fog} ${styles.fogA}`} />
          <div className={`${styles.fog} ${styles.fogB}`} />
          <div className={styles.fireflies}>
            {FIREFLIES.map((f) => (
              <span
                key={`${f.x}-${f.y}`}
                className={styles.firefly}
                style={
                  {
                    "--x": f.x,
                    "--y": f.y,
                    "--s": `${f.s}px`,
                    "--d": `${f.d}s`,
                    "--delay": `${f.delay}s`,
                    "--dx": `${f.dx}px`,
                    "--dy": `${f.dy}px`,
                  } as CSSProperties
                }
              />
            ))}
          </div>
          <div className={styles.shade} />
          <div className={styles.vignette} />
        </div>

        <header className={styles.navWrap}>
          <div className={styles.capsule}>
            <Link href="/" className={styles.brand}>
              <Logo />
              Wunderlore
            </Link>

            <nav aria-label="Primary" className={styles.primaryNav}>
              <Link href="/games/who-said-it" className={styles.navLink}>
                Who Said It?
              </Link>
              <Link href="/games/which-character" className={styles.navLink}>
                Which Character?
              </Link>
              <Link href="/games/lore-challenge" className={styles.navLink}>
                Lore Challenge
              </Link>
              <Link href="/quotes" className={styles.navLink}>
                Quotes
              </Link>
            </nav>

            {/* Small screens: one compact control instead of three crowded links. */}
            <details className={styles.menu}>
              <summary className={styles.menuButton}>
                Menu
                <span className={styles.chevron} aria-hidden="true" />
              </summary>
              <nav aria-label="Menu" className={styles.menuPanel}>
                <Link href="/games/who-said-it" className={styles.menuLink}>
                  Who Said It?
                </Link>
                <Link href="/games/which-character" className={styles.menuLink}>
                  Which Character?
                </Link>
                <Link href="/games/lore-challenge" className={styles.menuLink}>
                  Lore Challenge
                </Link>
                <Link href="/quotes" className={styles.menuLink}>
                  Quotes
                </Link>
              </nav>
            </details>
          </div>
        </header>

        <PageShell size="wide" className={styles.main}>
          <section className={styles.hero}>
            <p className={styles.badge}>
              <span className={styles.badgeDot} aria-hidden="true" />A world of stories
            </p>

            <div className={styles.heading}>
              <p className={styles.lead}>Wander deeper into</p>
              <h1 className={styles.title}>Wunderlore</h1>
            </div>

            <p className={styles.subtitle}>
              A quiet corner for readers of Tolkien. Test your memory of famous
              lines, find the character you resemble, or simply wander through
              the words.
            </p>

            <div className={styles.actions}>
              <Link href="#experiences" className={styles.cta}>
                Explore the games
              </Link>
              <Link href="/quotes" className={styles.ctaGhost}>
                <span className={styles.ghostLabel}>Explore the quotes</span>
              </Link>
            </div>
          </section>

          <section
            id="experiences"
            aria-labelledby="experiences-title"
            className={styles.strip}
          >
            <h2 id="experiences-title" className={styles.stripLabel}>
              Choose your path
            </h2>

            <ul className={styles.items}>
              <li className={styles.item}>
                <p className={styles.kicker}>
                  <span className={styles.glyph} aria-hidden="true">&ldquo;</span>
                  Quote game
                </p>
                <Link href="/games/who-said-it" className={styles.itemLink}>
                  Play Who Said It? →
                </Link>
                <p className={styles.itemText}>
                  Read a line of dialogue and guess who spoke it.
                </p>
              </li>

              <li className={styles.item}>
                <p className={styles.kicker}>
                  <span className={styles.glyph} aria-hidden="true">&#10022;</span>
                  Personality quiz
                </p>
                <Link href="/games/which-character" className={styles.itemLink}>
                  Which Character Are You? →
                </Link>
                <p className={styles.itemText}>
                  Answer personality questions to discover which Tolkien
                  character matches you.
                </p>
              </li>

              <li className={styles.item}>
                <p className={styles.kicker}>
                  <span className={styles.glyph} aria-hidden="true">&#9884;</span>
                  Lore trivia
                </p>
                <Link href="/games/lore-challenge" className={styles.itemLink}>
                  Take the Lore Challenge →
                </Link>
                <p className={styles.itemText}>
                  Ten questions on characters, places, peoples, history and
                  events, in three levels of difficulty.
                </p>
              </li>

              <li className={styles.item}>
                <p className={styles.kicker}>
                  <span className={styles.glyph} aria-hidden="true">&sect;</span>
                  Reading
                </p>
                <Link href="/quotes" className={styles.itemLink}>
                  Explore quotes →
                </Link>
                <p className={styles.itemText}>
                  Browse memorable lines from Middle-earth, page by page.
                </p>
              </li>
            </ul>
          </section>
        </PageShell>
      </div>
    </div>
  );
}
