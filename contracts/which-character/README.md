# Which Character Are You — HTTP contract fixtures

JSON bodies of the 200 responses of the Which Character Are You game. Backend
and frontend tests both read these files, so they are the single description of
the contract that both sides are checked against.

| Fixture | Endpoint |
| --- | --- |
| `questions.json` | `GET /api/games/which-character/questions` |
| `result.json` | `POST /api/games/which-character/result` |

All fields are required and non-null. The questions never expose scoring or the
characters an option is associated with. `result.json` is the result for one
character (Gandalf); the character varies with the answers.

## Who reads them

- Backend: `WhichCharacterContractTest` runs the real controller and DTOs through
  Spring's configured `JsonMapper` and requires the JSON to equal the fixture
  exactly, including any extra or missing property.
- Frontend: `whichCharacter.contract.test.ts` serves each fixture to the real
  `getQuestions`/`submitAnswers` (and so the production validators) and requires
  the parsed value to equal the fixture.

## Changing the contract on purpose

Follow the steps in `contracts/who-said-it/README.md`, using
`frontend/src/lib/api/whichCharacter.ts` for the TypeScript side.
