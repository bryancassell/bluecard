# Writing the catalog

The catalog of merit badges and ranks is a single JSON file bundled with the
app: [`app/src/main/assets/catalog.json`](../app/src/main/assets/catalog.json).
This page explains its format so badges and ranks can be added without reading
code. Why the catalog works this way is in
[`ARCHITECTURE.md`](../ARCHITECTURE.md#merit-badge-catalog).

## Ground rules

- **Our own words only.** Each badge gets a short summary and each requirement a
  one-line summary, written for this project rather than copied from Scouting
  America. A summary keeps the official wording only where that's the plain,
  obvious way to say it: see [What counts as copying](#what-counts-as-copying).
  The app links to the official page for the full wording.
- **Match the official structure.** Requirement numbers, nesting and "do N of the
  following" counts must match the official page exactly, because scouts and
  counselors use those numbers. The one exception is either/or children (see
  `requiredCount` below).
- **Current requirements only, until the first release.** Write each badge's
  current requirements as its only version.
- **After the first release, keep every shipped version.** When a badge's
  requirements change, add the new version and leave the old one in place:
  scouts' progress is saved against it. Never remove a shipped version or
  change its requirement numbers or structure, and don't add or remove
  `ownWork` on one: completion is worked out from the catalog each time, so it
  would change which badges scouts have completed.
- **Keep a shipped tracker's columns.** Scouts' entries are saved by column
  `id`, so once a version ships, never remove a column or change its `id` or
  `type`, and never change the tracker's `rowCount`. You can change a column's
  label or add a column, and change a `text` column to `multiline-text`, but
  not back: its saved values may then hold line breaks. You can add, change or
  remove a column's `total`, which only shows how far along the scout is.

## What counts as copying

Write each summary from what the requirement asks, not by editing the official
sentence. Then read it next to the official text. If the summary keeps the
official wording of a sentence or clause, and a clearly different wording would
read at least as naturally, reword it. Swapping or dropping a word or two
doesn't make the wording different.

Keep the official wording where it's the plain, obvious way to say it. These
are fine, even word for word:

- Names of things, such as conditions, places, items and concepts.
- Lists of names. Don't reorder a list just to make it differ.
- Numbers, and common phrases such as "with your counselor" or "in your own
  words".
- Plain instructions that would read worse any other way, such as "Tour a
  federal facility."

Examples from the catalog (compare them with the official pages):

| Requirement | Summary | Verdict |
|---|---|---|
| Canoeing 3a | Identify the main parts of a canoe. | Reworded: the first draft was the official sentence with one word changed, and this says it just as naturally. |
| Citizenship in the Community 1 | Discuss what good citizenship looks like in your community. | Reworded: the first draft carried over an 11-word official clause that's easy to say other ways. |
| Citizenship in the Nation 7c | Tour a federal facility. | Fine, though it's the official sentence: any other wording reads worse. |
| First Aid 3g | Immersion foot, frostnip, frostbite and ice burns. | Fine: the official list of names. |
| Environmental Science 1 | Explain environmental science in your own words and how it helps. | Fine: a name and a common phrase from the official text, in a sentence of our own. |

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
  ],
  "ranks": []
}
```

### Badge

| Field | Required | Meaning |
|---|---|---|
| `id` | Yes | Stable ID that progress is saved against, in lowercase words joined by `-` (usually the official page's slug). It must not match a rank's ID. **Never change it** once released. |
| `name` | Yes | The badge's official name. |
| `summary` | Yes | Our own one- or two-sentence description. |
| `officialUrl` | Yes | The badge's page on `https://www.scouting.org/`. Copy it from the browser: it doesn't always match the name. |
| `eagleRequired` | No | `true` if the badge counts toward Eagle Scout. Defaults to `false`. |
| `eagleGroup` | No | For Eagle badges that are alternatives to each other (such as Cycling, Hiking and Swimming), the same ID on each, such as `cycling-hiking-swimming`. Leave it out for badges that are required on their own. |
| `requirementVersions` | Yes | One entry per requirements version. |

### Rank

`ranks`, which is required, lists the ranks, Scout through Eagle, in the order
they're earned. A rank has the same fields as a badge, except `eagleRequired`
and `eagleGroup`. Its `id` must not match any badge's ID, because a rank's
progress is saved in the same place as a badge's. Its requirements use the same
format as a badge's.

- **`officialUrl`:** scouting.org has no web page for each rank, so a rank
  links to its requirements PDF, which the
  [Advancement and Awards page](https://www.scouting.org/programs/scouts-bsa/advancement-and-awards/)
  lists. Eagle has no PDF of its own, so it links to the PDF of all the ranks.
- **Numbering:** most rank requirements are numbered `1a`, `1b` and so on with
  no requirement `1` above them, under unnumbered headings such as "Camping and
  Outdoor Ethics". Keep them at the top level, as the PDF has them, and leave
  the headings out. Unnumbered bullets, and letters inside a requirement's
  sentence, such as Eagle 3's "(a) First Aid, (b) Citizenship in the
  Community…", aren't requirements of their own: summarize them in the
  requirement's summary.
- **Time in rank:** give `monthsInRank` to each requirement that asks for
  months in the rank below: Star 1 and 5, Life 1 and 5, and Eagle 1 and 4.
  That includes Star 5 and Life 5, though a leadership project can replace
  their months in a position of responsibility.
- **Where the PDF is out of date:** summarize the current rule and give the
  version the date it took effect.
  - **Eagle 3** counts 13 Eagle-required badges, not the PDF's 14, because
    Citizenship in Society was dropped on February 27, 2026.
  - **Life 3** asks for seven Eagle-required badges in all, as the
    [Scouts BSA Requirements book](https://www.scouting.org/wp-content/uploads/2026/02/3321625-Scouts-BSA-Requirements.pdf)
    and the Life PDF in effect from 2021 do. The December 2025 Life PDF asks
    for "three additional" instead.

### Requirements version

| Field | Required | Meaning |
|---|---|---|
| `effectiveDate` | Yes | The date the version took effect, as `YYYY-MM-DD`. Most changes take effect January 1. |
| `requirements` | Yes | The top-level requirements, in official order. |

### Requirement

| Field | Required | Meaning |
|---|---|---|
| `number` | Yes | The official number, written as on the official page: `4`, `4c`, `4c(1)`. It identifies the requirement, so it must be unique within the version. For a requirement split into lettered options ("Option A", "Option B"), add the option's letter to the number, then its parts' labels in parentheses: Cycling 6 Option B's part (1)(c) is `6B(1)(c)`. If the page gives the options labels of their own, such as "(a) Option A—Model Railroading", use those labels instead: Railroading 7 Option A's part (1) is `7a(1)`. |
| `summary` | Yes | Our own one-line summary. Aim for 15 words or fewer. This isn't a hard limit: a summary that reads clearly matters more than its length. |
| `children` | No | Sub-requirements, in official order. |
| `requiredCount` | No | For "do N of the following": how many children must be done. Leave it out when all children are required. Also set it to `1` when the children are either/or cases and only one can apply, even though the page gives no count (Personal Management 2b: one child for a budget that overspends, one for a budget with money left over). |
| `tracker` | No | For requirements that need repeated entries, such as a weekly log. See below. |
| `ownWork` | No | For a requirement with `children` that also asks for work of its own: our own one-line summary of just that work, such as `"Take a hunter education course or get a copy of your state's hunting laws."` for Shotgun Shooting 1g. The scout checks it off on the requirement's page, and the requirement is complete once it is and enough children are. Add it for any ask no child covers, including a closing step such as discussing what you did with your counselor. Leave it out when the requirement only introduces its children ("Do the following", "Discuss these with your counselor:"). The `summary` still describes the whole requirement. |
| `monthsInRank` | No | Only on a rank's requirement that asks for months in the rank below, such as Star 1's four months as a First Class Scout: how many months, at least 1. Its page shows the date the scout becomes eligible, counted from when they earned the rank below, but the scout still checks it off. The lowest rank can't have it, since no rank is below it. See [Rank](#rank) for which requirements have it. |

### Tracker

A tracker is a table the scout fills in, one row per entry.

| Field | Required | Meaning |
|---|---|---|
| `columns` | Yes | The table's columns, each with an `id` (lowercase words joined by `-`, unique within the tracker), a `label` shown to the scout, and a `type`: `date`, `number`, `text` or `multiline-text`. Use `multiline-text` for a description or a list, such as "What you saw", and `text` for a name or a short value. A `date` takes only dates up to today, so use `text` for one that can be in the future, such as an alarm's expiration date. A `number` takes digits and one decimal separator but no minus sign, so use `text` for one that can be negative, such as a temperature. A `number` column can also have a `total` ([Column total](#column-total)). |
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

### Column total

In a log, a `number` column whose values add up to an amount the requirement
asks for, such as hours of service, can have a `total`. A tracker with a
`rowCount` can't have one: its requirement shows how many rows are filled in,
which complete it. The requirement then shows the
column's values added up against it, such as "4.5 of 6 hours": on its row in
place of how many rows there are, and on its page and in the report under that
count. It's only a guide: the scout still checks the requirement
off, as for any log.

| Field | Required | Meaning |
|---|---|---|
| `needed` | Yes | The amount the requirement asks for, a whole number of at least 1. |
| `label` | Yes | What the amount is counted in, in lowercase, such as `hour`. The app shows it when `needed` is 1: "0.5 of 1 hour". |
| `labelPlural` | Yes | `label` in the plural, in lowercase, such as `hours`. |

Example, Life 4's service log, where part of a project's hours can be
conservation:

```json
"columns": [
  { "id": "date", "label": "Date", "type": "date" },
  {
    "id": "hours",
    "label": "Hours",
    "type": "number",
    "total": { "needed": 6, "label": "hour", "labelPlural": "hours" }
  },
  {
    "id": "conservation-hours",
    "label": "Conservation hours (included in Hours)",
    "type": "number",
    "total": { "needed": 3, "label": "conservation hour", "labelPlural": "conservation hours" }
  }
]
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
- `ranks` lists Scout, Tenderfoot, Second Class, First Class, Star, Life and
  Eagle Scout, in that order;
- badge and rank IDs are unique across both and in the right form, and every
  badge and rank has a name, a summary, a `https://www.scouting.org/` URL and
  at least one version;
- a badge or rank doesn't have two versions with the same effective date, and
  `eagleGroup` is only set on Eagle-required badges;
- every version has requirements, and requirement numbers are unique within it;
- every requirement has a number and a summary, `requiredCount` is between 1
  and the number of children, and `ownWork` is only set, and not blank, on a
  requirement with children;
- `monthsInRank` is at least 1, and only on a requirement of a rank other than
  the lowest;
- trackers have at least one column, unique column IDs, labels, row labels
  that start with a lowercase letter, and a `rowCount` of at least 1 when set;
- a `total` is only on a `number` column of a log, needs at least 1, and has
  labels that start with a lowercase letter.

It can't check that the structure matches the official page or that summaries
are in our own words; reviewers check those. To check the wording, read each
summary next to its requirement on the official page, using
[What counts as copying](#what-counts-as-copying). Counting the words a summary
shares with the official text can help find summaries to look at, but decide
each one with that section, not by the count: lists and common phrases make
long matches in summaries that are fine, and rewording to avoid them makes
summaries read awkwardly.
