package com.wunderlore.ranking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// One published result. Maps the ranking_entries table created by V1__create_ranking_entries.sql.
// Only the database layer loads it, and only when wunderlore.persistence.enabled is true.
@Entity
@Table(name = "ranking_entries")
public class RankingEntry {

    // Percentages are stored with six decimals: with scores of at most a few thousand points, two
    // different ratios always differ by far more than that, so ordering by the stored value is
    // ordering by the real one.
    static final int PERCENTAGE_SCALE = 6;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "game_type", nullable = false, length = 32, updatable = false)
    private GameType gameType;

    @Column(name = "game_id", nullable = false, length = 64, updatable = false)
    private String gameId;

    @Column(nullable = false, length = 16, updatable = false)
    private String nickname;

    @Column(nullable = false, updatable = false)
    private int score;

    @Column(name = "max_score", nullable = false, updatable = false)
    private int maxScore;

    @Column(nullable = false, precision = 9, scale = PERCENTAGE_SCALE, updatable = false)
    private BigDecimal percentage;

    @Column(name = "played_at", nullable = false, updatable = false)
    private Instant playedAt;

    // Null only in rows published before V2, whose division could not be recovered. Every new
    // entry has one (the constructor requires it); the column itself stays nullable.
    @Enumerated(EnumType.STRING)
    @Column(name = "division", length = 16, updatable = false)
    private RankingDivision division;

    protected RankingEntry() {
    }

    // Throws IllegalArgumentException when the game's division does not exist in that game
    // (RankingDivision decides): such an entry is never created, so it is never saved.
    public RankingEntry(GameType gameType, String gameId, String nickname, FinishedGame game) {
        if (!game.division().isValidFor(gameType)) {
            throw new IllegalArgumentException("Division " + game.division() + " does not exist in " + gameType);
        }

        this.gameType = gameType;
        this.gameId = gameId;
        this.nickname = nickname;
        this.score = game.score();
        this.maxScore = game.maxScore();
        this.percentage = percentageOf(game.score(), game.maxScore());
        this.playedAt = game.playedAt();
        this.division = game.division();
    }

    // (score / maxScore) * 100 with the precision the table keeps.
    static BigDecimal percentageOf(int score, int maxScore) {
        return BigDecimal.valueOf(score)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(maxScore), PERCENTAGE_SCALE, RoundingMode.HALF_UP);
    }

    public UUID getId() {
        return id;
    }

    public GameType getGameType() {
        return gameType;
    }

    public String getGameId() {
        return gameId;
    }

    public String getNickname() {
        return nickname;
    }

    public int getScore() {
        return score;
    }

    public int getMaxScore() {
        return maxScore;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    public Instant getPlayedAt() {
        return playedAt;
    }

    public RankingDivision getDivision() {
        return division;
    }
}
