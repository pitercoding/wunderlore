# Who Said It — HTTP contract fixtures

JSON bodies of the 200 responses of the Who Said It game. Backend and frontend
tests both read these files, so they are the single description of the contract
that both sides are checked against.

| Fixture | Endpoint |
| --- | --- |
| `round-created.json` | `POST /api/games/who-said-it/rounds` |
| `answer-correct.json` | `POST /api/games/who-said-it/rounds/{roundId}/answer`, `correct: true` |
| `answer-incorrect.json` | same endpoint, `correct: false` |

All fields are required and non-null; there are no optional or nullable fields.
The round never exposes the correct character.

## Who reads them

- Backend: `WhoSaidItContractTest` runs the real controller and DTOs through
  Spring's configured `JsonMapper` and requires the JSON to equal the fixture
  exactly, including any extra or missing property.
- Frontend: `whoSaidIt.contract.test.ts` serves each fixture to the real
  `createRound`/`submitAnswer` (and so the production validators) and requires
  the parsed value to equal the fixture.

## Changing the contract on purpose

1. Change the backend DTO (and its controller/service as needed).
2. Update the affected fixtures here by hand, keeping the exact property names.
3. Run the backend contract test; it passes only when the served JSON equals
   the fixtures.
4. Update the TypeScript type and validator in `frontend/src/lib/api/whoSaidIt.ts`.
5. Run the frontend contract test; it fails if a validator rejects a fixture or
   if the validated shape no longer matches the fixture.
6. Update the explicit field-by-field checks in both contract tests.

Do not edit a fixture just to make one side pass: a failing side means the two
contracts diverged.
