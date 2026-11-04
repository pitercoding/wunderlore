# Lore Challenge — HTTP contract fixtures

JSON bodies of the 200 responses of the Lore Challenge game. Backend and frontend tests both read
these files, so they are the single description of the contract both sides are checked against
(same method as `contracts/who-said-it`).

| Fixture | Endpoint |
| --- | --- |
| `question.json` | `POST /api/games/lore-challenge/matches` and `GET /api/games/lore-challenge/matches/{matchId}/question` |
| `answer.json` | `POST /api/games/lore-challenge/matches/{matchId}/answer` |
| `result.json` | `GET /api/games/lore-challenge/matches/{matchId}/result` |

All fields are required and non-null. A question never exposes the correct option or the
explanation; both arrive only with the answer. `category` is `ALL` or one of `CHARACTERS`, `PLACES`,
`PEOPLES`, `HISTORY`, `EVENTS`; `difficulty` is `EASY`, `MEDIUM` or `HARD`.

Backend: `LoreChallengeContractTest`. Frontend: `loreChallenge.contract.test.ts`. To change the
contract, follow the steps in `contracts/who-said-it/README.md`.
