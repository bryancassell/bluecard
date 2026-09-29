# Writing the merit badge catalog

The merit badge catalog is a single JSON file bundled with the app:
[`app/src/main/assets/catalog.json`](../app/src/main/assets/catalog.json). This
page explains its format so badges can be added without reading code. Why the
catalog works this way is in [`ARCHITECTURE.md`](../ARCHITECTURE.md#merit-badge-catalog).

## Ground rules

- **Our own words only.** Never copy text from Scouting America. Each badge gets a
  short summary and each requirement a one-line summary, written for this project.
  The app links to the official page for the full wording.
- **Match the official structure.** Requirement numbers, nesting and "do N of the
  following" counts must match the official page exactly, because scouts and
  counselors use those numbers.
- **Current requirements only, until the first release.** Write each badge's
  current requirements as its only version.
- **After the first release, keep every shipped version.** When a badge's
  requirements change, add the new version and leave the old one in place:
  scouts' progress is saved against it. Never remove a shipped version or
  change its requirement numbers or structure.

## Format

```json
{
  "formatVersion": 1,
  "badges": [
    {
      "id": "personal-fitness",
      "name": "Personal Fitness",
      "summary": "Build healthy habits and follow a 12-week fitness program.",
      "officialUrl": "https://www.scouting.org/merit-badges/personal-fitness/",
      "eagleRequired": true,
      "requirementVersions": [
        {
          "effectiveDate": "2026-01-01",
          "requirements": [
            {
              "number": "8",
              "summary": "Look ahead at fitness in your future.",
              "requiredCount": 1,
              "children": [
                { "number": "8a", "summary": "Explore careers in fitness and study one closely." },
                { "number": "8b", "summary": "Explore how fitness could become a lifelong hobby." }
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

### Badge

| Field | Required | Meaning |
|---|---|---|
| `id` | Yes | Stable ID that progress is saved against, in lowercase words joined by `-` (usually the official page's slug). **Never change it** once released. |
| `name` | Yes | The badge's official name. |
| `summary` | Yes | Our own one- or two-sentence description. |
| `officialUrl` | Yes | The badge's page on `https://www.scouting.org/`. Copy it from the browser: it doesn't always match the name. |
| `eagleRequired` | No | `true` if the badge counts toward Eagle Scout. Defaults to `false`. |
| `eagleGroup` | No | For Eagle badges that are alternatives to each other (such as Cycling, Hiking and Swimming), the same ID on each, such as `cycling-hiking-swimming`. Leave it out for badges that are required on their own. |
| `requirementVersions` | Yes | One entry per requirements version. |

### Requirements version

| Field | Required | Meaning |
|---|---|---|
| `effectiveDate` | Yes | The date the version took effect, as `YYYY-MM-DD`. Most changes take effect January 1. |
| `requirements` | Yes | The top-level requirements, in official order. |

### Requirement

| Field | Required | Meaning |
|---|---|---|
| `number` | Yes | The official number, written as on the official page: `4`, `4c`, `4c(1)`. It identifies the requirement, so it must be unique within the version. |
| `summary` | Yes | Our own one-line summary. Aim for 12 words or fewer. |
| `children` | No | Sub-requirements, in official order. |
| `requiredCount` | No | For "do N of the following": how many children must be done. Leave it out when all children are required. |
| `tracker` | No | For requirements that need repeated entries, such as a weekly log. See below. |

### Tracker

A tracker is a table the scout fills in, one row per entry.

| Field | Required | Meaning |
|---|---|---|
| `columns` | Yes | The table's columns, each with an `id` (lowercase words joined by `-`, unique within the tracker), a `label` shown to the scout, and a `type`: `date`, `number` or `text`. |
| `rowLabel` | Yes | What one row is called, in lowercase, such as `week` or `session`. The app capitalizes it for titles, such as "Week 3". |
| `rowLabelPlural` | Yes | `rowLabel` in the plural, in lowercase, such as `weeks`. The requirement's row shows it in a count, such as "8 of 12 weeks". |
| `rowCount` | No | A fixed number of rows, such as `13` for a 13-week budget. The scout fills in each one ("Week 1" to "Week 13"). Leave it out for a log the scout adds rows to, any number of them. |

Example, a log of exercise sessions:

```json
"tracker": {
  "columns": [
    { "id": "date", "label": "Date", "type": "date" },
    { "id": "activity", "label": "Activity", "type": "text" },
    { "id": "minutes", "label": "Minutes", "type": "number" }
  ],
  "rowLabel": "session",
  "rowLabelPlural": "sessions"
}
```

## Checking your changes

`./gradlew build` runs `BundledCatalogTest`, which fails with a list of every
problem it finds. To run just that test:

```sh
./gradlew testDebugUnitTest --tests '*BundledCatalogTest*'
```

It checks that:

- the file is valid JSON with only the fields above, spelled correctly, and
  dates written as `YYYY-MM-DD`;
- `formatVersion` is `1`;
- badge IDs are unique and in the right form, and every badge has a name, a
  summary, a `https://www.scouting.org/` URL and at least one version;
- a badge doesn't have two versions with the same effective date, and
  `eagleGroup` is only set on Eagle-required badges;
- every version has requirements, and requirement numbers are unique within it;
- every requirement has a number and a summary, and `requiredCount` is between 1
  and the number of children;
- trackers have at least one column, unique column IDs, labels, row labels
  that start with a lowercase letter, and a `rowCount` of at least 1 when set.

It can't check that the structure matches the official page or that summaries
are in our own words; reviewers check those.
