# Where in Middle-earth — HTTP contract fixtures and map geometry

JSON bodies of the 200 responses of the Where in Middle-earth game, and the geometry of the map it is
played on. Backend and frontend tests both read these files, so they are the single description of the
contract both sides are checked against (same method as `contracts/who-am-i`).

| File | What it is |
| --- | --- |
| `round.json` | `POST /api/games/where-in-middle-earth/games` and `GET /api/games/where-in-middle-earth/games/{gameId}/round` |
| `answer.json` | `POST /api/games/where-in-middle-earth/games/{gameId}/answer`, a round that is not the last |
| `answer-final.json` | the same, ending the tenth round: `result` carries the final result |
| `result.json` | `GET /api/games/where-in-middle-earth/games/{gameId}/result` |
| `map-geometry.json` | the map's coordinate system, land outline, regions and the labels written on it |

The fixtures are abridged where noted: a real result lists all ten rounds, these list two.

## The map

The map is an SVG with `viewBox="0 0 1000 700"`: x grows to the east, y to the south, and both are in "map
units". Every position in the API (the click sent, the place revealed, every distance) is in these units, so
it does not depend on the screen the map is shown on.

`map-geometry.json` holds what both sides must agree on:

- `viewBox`: the size above.
- `land`: the outline of the land as a polygon. The frontend draws the land from it, and the backend test checks
  that every place is on land (and at least 4 units from the shore).
- `regions`: nine broad areas, each a polygon. Every place belongs to one; the backend test checks the place is
  inside it, and that the names match the ones the API sends as `regionName`.
- `labels`: the only text written on the map. None of them may be the name of a place; the backend test checks
  it, and the frontend test checks that the map writes exactly these.

## What a round never carries

While a round waits for an answer the backend sends only the name and a short description of the place, the
progress and the score. It never sends the place's coordinates, region or explanation, nor its id. They arrive
only in `reveal`, once the round is over.

## Requests

- `POST /api/games/where-in-middle-earth/games`: `{ "difficulty": "EASY" | "MEDIUM" | "HARD" }`
- `POST /api/games/where-in-middle-earth/games/{gameId}/answer`: `{ "roundNumber": 1-10, "x": 0-1000, "y": 0-700 }`.
  Only the round that is waiting can be answered, once: anything else is a 409. A position outside the map, a
  missing or non-numeric field, or a body that is not JSON is a 400 and does not consume the round.

## Answer response

`reveal` is always present (the round is over as soon as it is answered). `result` is `null` unless this was the
tenth round, when `gameFinished` is `true`.

- `submitted`: the point as received.
- `distance`: from `submitted` to `reveal.x`, `reveal.y`, in map units, rounded to a tenth.
- `accuracy` and `pointsAwarded`: from the distance, by the bands below. The score is only ever computed by the
  backend: an answer request carries only `roundNumber`, `x` and `y`.

## Scoring

One rule for every difficulty (difficulty changes which places are asked, never the points). The distance is
measured in map units; the bands are inclusive of their upper limit.

| `accuracy` | Distance | Points |
| --- | --- | --- |
| `EXCELLENT` | up to 15 | 100 |
| `VERY_GOOD` | up to 30 | 80 |
| `GOOD` | up to 55 | 60 |
| `CLOSE` | up to 90 | 40 |
| `FAR` | more than 90 | 0 |

A game has ten rounds, so the maximum is 1,000. The bands were calibrated to the map: two places of the same
difficulty are always more than 30 units apart (twice the best radius), so no click can be "excellent" for two
places, and the widest band is 9% of the map's width. `closeHits` in the result counts the rounds that earned
points (every band but `FAR`); `averageDistance` is the mean distance of the ten answers; `percentage` is the
score over the maximum.

Errors are `application/problem+json`: 400 invalid request or position, 404 unknown or expired game, 409 stale
answer / game finished / result requested too early, 422 not enough places, 429 too many games created, 503
too many active games.

Backend: `WhereInMiddleEarthContractTest`. Frontend: `whereInMiddleEarth.contract.test.ts`. To change the
contract, follow the steps in `contracts/who-said-it/README.md`.
