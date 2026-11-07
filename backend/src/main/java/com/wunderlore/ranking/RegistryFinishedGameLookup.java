package com.wunderlore.ranking;

import org.springframework.stereotype.Component;

import com.wunderlore.game.lorechallenge.LoreChallengeException;
import com.wunderlore.game.lorechallenge.LoreChallengeMatch;
import com.wunderlore.game.lorechallenge.LoreChallengeMatchRegistry;
import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthException;
import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthGame;
import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthGameRegistry;
import com.wunderlore.game.whoami.WhoAmIException;
import com.wunderlore.game.whoami.WhoAmIGame;
import com.wunderlore.game.whoami.WhoAmIGameRegistry;
import com.wunderlore.game.whosaidit.WhoSaidItMatch;
import com.wunderlore.game.whosaidit.WhoSaidItMatchException;
import com.wunderlore.game.whosaidit.WhoSaidItMatchRegistry;
import com.wunderlore.ranking.RankingException.Reason;

// Reads score and maximum from the in-memory registries of the four games, which already compute
// them. The ranking therefore never accepts a score from a client. The games know nothing about
// the ranking; this is the only place that depends on all of them.
@Component
public class RegistryFinishedGameLookup implements FinishedGameLookup {

    private final LoreChallengeMatchRegistry loreChallenge;
    private final WhoAmIGameRegistry whoAmI;
    private final WhereInMiddleEarthGameRegistry whereInMiddleEarth;
    private final WhoSaidItMatchRegistry whoSaidIt;

    public RegistryFinishedGameLookup(
            LoreChallengeMatchRegistry loreChallenge,
            WhoAmIGameRegistry whoAmI,
            WhereInMiddleEarthGameRegistry whereInMiddleEarth,
            WhoSaidItMatchRegistry whoSaidIt) {
        this.loreChallenge = loreChallenge;
        this.whoAmI = whoAmI;
        this.whereInMiddleEarth = whereInMiddleEarth;
        this.whoSaidIt = whoSaidIt;
    }

    @Override
    public FinishedGame find(GameType type, String gameId) {
        try {
            return switch (type) {
                case LORE_CHALLENGE -> {
                    LoreChallengeMatch.Result result = loreChallenge.result(gameId);

                    yield new FinishedGame(result.score(), result.maxScore(), loreChallenge.createdAt(gameId));
                }
                case WHO_AM_I -> {
                    WhoAmIGame.Result result = whoAmI.result(gameId);

                    yield new FinishedGame(result.score(), result.maxScore(), whoAmI.createdAt(gameId));
                }
                case WHERE_IN_MIDDLE_EARTH -> {
                    WhereInMiddleEarthGame.Result result = whereInMiddleEarth.result(gameId);

                    yield new FinishedGame(
                            result.score(), result.maxScore(), whereInMiddleEarth.createdAt(gameId));
                }
                case WHO_SAID_IT -> {
                    WhoSaidItMatch.Result result = whoSaidIt.result(gameId);

                    yield new FinishedGame(result.score(), result.maxScore(), whoSaidIt.createdAt(gameId));
                }
            };
        } catch (LoreChallengeException exception) {
            throw translate(exception.reason() == LoreChallengeException.Reason.MATCH_NOT_FINISHED);
        } catch (WhoAmIException exception) {
            throw translate(exception.reason() == WhoAmIException.Reason.GAME_NOT_FINISHED);
        } catch (WhereInMiddleEarthException exception) {
            throw translate(exception.reason() == WhereInMiddleEarthException.Reason.GAME_NOT_FINISHED);
        } catch (WhoSaidItMatchException exception) {
            throw translate(exception.reason() == WhoSaidItMatchException.Reason.MATCH_NOT_FINISHED);
        }
    }

    private static RankingException translate(boolean notFinished) {
        return new RankingException(notFinished ? Reason.GAME_NOT_FINISHED : Reason.GAME_NOT_FOUND);
    }
}
