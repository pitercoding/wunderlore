import styles from "./SiteFooter.module.css";

// Global footer, rendered once by the root layout so every page carries the notice.
export default function SiteFooter() {
    return (
        <footer className={styles.footer}>
            <p className={styles.notice}>
                Wunderlore is an unofficial fan project. It is not affiliated with,
                endorsed or sponsored by the Tolkien Estate or the rights holders of
                the film adaptations.
            </p>
        </footer>
    );
}
