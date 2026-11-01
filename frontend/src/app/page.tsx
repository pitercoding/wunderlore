import Link from "next/link";

export default function HomePage() {
  return (
    <main>
      <h1>Wunderlore</h1>
      <p>Explore stories, characters, and worlds.</p>

      <Link href="/quotes">Explore quotes →</Link>
      <Link href="/games/who-said-it">Play Who Said It? →</Link>
    </main>
  );
}