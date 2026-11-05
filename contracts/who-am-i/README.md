# Who Am I? — HTTP contract fixtures

JSON bodies of the 200 responses of the Who Am I? game. Backend and frontend tests both read these
files, so they are the single description of the contract both sides are checked against (same
method as `contracts/lore-challenge`).

| Fixture | Endpoint |
| --- | --- |
| `round.json` | `POST /api/games/who-am-i/games` and `GET /api/games/who-am-i/games/{gameId}/round` |
| `answer-incorrect.json` | `POST /api/games/who-am-i/games/{gameId}/answer`, a wrong answer with clues left: the next clue is in `next` |
| `answer-correct.json` | the same, identifying the character (or failing the fifth clue): the character is in `reveal` |
| `answer-final.json` | the same, ending the tenth round: `result` carries the final result |
| `result.json` | `GET /api/games/who-am-i/games/{gameId}/result` |

The fixtures are abridged where noted: a real result lists all ten rounds, these list two.

## What a round never carries

Before a round ends the backend sends only the clues revealed so far (`clues` grows by one with every
wrong answer), the four options (`id` is `a` to `d`, `text` is a character name) and public progress.
It never sends the character, an id that identifies it, the later clues or which option is correct.
The name and all five clues arrive only in `reveal`, once the round is over.

## Answer response

`next`, `reveal` and `result` are always present; the ones that do not apply are `null`:

- wrong answer, clues left: `next` set, `reveal` and `result` null;
- round over (identified, or the fifth clue answered wrongly): `reveal` set, `next` null;
- last round over: `result` also set, `gameFinished` true.

`pointsAwarded` is 100, 80, 60, 40 or 20 for identifying the character at clue 1 to 5, and 0 otherwise.
The score is only ever computed by the backend: an answer request carries only `roundNumber`,
`clueNumber` and `optionId`.

## Requests

- `POST /api/games/who-am-i/games`: `{ "difficulty": "EASY" | "MEDIUM" | "HARD" }`
- `POST /api/games/who-am-i/games/{gameId}/answer`: `{ "roundNumber": 1-10, "clueNumber": 1-5, "optionId": "a"-"d" }`.
  Only the clue that is waiting can be answered, once: anything else is a 409.

Errors are `application/problem+json`: 400 invalid request, 404 unknown or expired game, 409 stale
answer / game finished / result requested too early, 422 not enough characters, 429 too many games
created, 503 too many active games.

Backend: `WhoAmIContractTest`. Frontend: `whoAmI.contract.test.ts`. To change the contract, follow the
steps in `contracts/who-said-it/README.md`.
