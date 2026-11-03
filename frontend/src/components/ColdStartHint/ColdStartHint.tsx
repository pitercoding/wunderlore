import styles from "./ColdStartHint.module.css";

// Shown beside a loading status for the calls that may reach a sleeping backend (see
// COLD_START_TIMEOUT_MS). The wait is expected there, so it is explained instead of
// looking like a hang. Not an alert: nothing has failed.
export default function ColdStartHint() {
    return (
        <p className={styles.hint}>
            The keepers of the archives rest when no one visits. If this is the first
            visit in a while, waking them can take a minute or two.
        </p>
    );
}
