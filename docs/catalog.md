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
  counselors use those numbers. The exceptions are either/or children (see
  `requiredCount` below) and ranks' lettered requirements with no number above
  them (see [Rank](#rank)).
- **Leave out Test Lab pilots.** Scouts BSA Test Lab pilot badges, whose
  requirements are on Scouting America's Test Lab pages rather than a
  `/merit-badges/` page, aren't added: they count toward ranks only once they
  become official, and their requirements are taken down when the pilot ends.
  When one becomes official, add it like any new badge, dated the day it became
  official rather than the day its pilot began (see
  [Dating a version](#dating-a-version)).
- **Current requirements only, until the first release.** Write each badge's
  current requirements as its only version.
- **After the first release, keep every shipped version.** When a badge's
  requirements change, add the new version and leave the old one in place:
  scouts' progress is saved against it. Never remove a shipped version or
  change its requirement numbers or structure, and don't add or remove
  `ownWork` or `meritBadges` on one, or change `meritBadges`' numbers:
  completion is worked out from the catalog each time, so it would change which
  badges and ranks scouts have completed.
- **Keep a shipped tracker's columns.** Scouts' entries are saved by column
  `id`, so once a version ships, never remove a column or change its `id` or
  `type`, and never change the tracker's `rowCount`. You can change a column's
  label or add a column, and change a `text` column to `multiline-text`, but
  not back: its saved values may then hold line breaks. You can add, change or
  remove a column's `total` or the tracker's `rowsNeeded`: they show how far
  along the scout is, but don't decide what's complete.

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
| Citizenship in the Nation 7c | Tour a federal facility, and explain what you saw and how it serves the community and nation. | Fine, though it starts with the official sentence: any other wording of that part reads worse. |
| First Aid 3g | Immersion foot, frostnip, frostbite and ice burns. | Fine: the official list of names. |
| Environmental Science 1 | Explain environmental science in your own words and how it helps. | Fine: a name and a common phrase from the official text, in a sentence of our own. |

## Dating a version

Use the date from Scouting America's yearly list of requirement updates, if the
badge is on one. The page can also change without being listed, and any change
to the words counts, even one word: a requirement's text (Textile 1's "define"
became "explain"), a note built into a requirement, a label (Theater 1's
footnote gained "Note:"), or a page note that sets conditions (Collections'
note on collecting). Changes to punctuation, capitalization, spacing, resource
links or how a word is spelled (Dentistry 6a's "papier-mâché" became
"paper-mâché") don't count.

Date an unlisted change January 1 of the year it first appears in the Internet
Archive's copies of the official page. That date is an estimate: say so in the
pull request, and name the copies on either side of the change.

Date a new badge the day Scouting America launched it, from its announcement
(Competitive Gaming launched 2026-07-24 at the National Jamboree, after a Test
Lab pilot that began in March). Changes in the year it launched keep that date;
a change in a later year dates it as above (Artificial Intelligence launched in
2025, and its note's label changed in 2026).

## When the official page is wrong

When the live page has an obvious mistake, such as one requirement repeating
another's text, summarize the last correct text, from an archived copy of the
page or an official requirements PDF, and say which in the pull request.
Graphic Arts 4d and Insect Study 9b repeat their neighbors' text on the page.
When the page points to the wrong requirement and no copy has it right, point
to the one it means: Plant Science 8C(3) says requirement 5, but means the plant
lists in 6.

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
                { "number": "8a", "summary": "Explore careers in fitness, study one closely, and discuss what you found and whether it interests you." },
                { "number": "8b", "summary": "Explore how fitness could become a lifelong hobby, discuss what you learned, and share your goals for it." }
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
  Outdoor Ethics", or none. Nest them under a requirement `1` anyway, as a
  badge's are, so the rank's page lists one row per number. With no official
  text to summarize, give it our own summary of what its sub-requirements ask,
  such as Scout 1's "Learn what Scouts stand for, from the Oath and Law to the
  sign and badge.", and leave the headings out. Unnumbered bullets, and letters
  inside a requirement's sentence, such as Eagle 3's "(a) First Aid, (b)
  Citizenship in the Community…", aren't requirements of their own: summarize
  them in the requirement's summary.
- **Time in rank:** give `monthsInRank` to each requirement that asks for
  months in the rank below: Star 1 and 5, Life 1 and 5, and Eagle 1 and 4.
  That includes Star 5 and Life 5, though a leadership project can replace
  their months in a position of responsibility.
- **Merit badges:** give `meritBadges` to each requirement that asks for merit
  badges: Star 3, Life 3 and Eagle 3. Its numbers are the badges needed in
  all, so Life 3's "five more for 11 in all, at least seven of them
  Eagle-required" has a `total` of 11 and an `eagleRequired` of 7. Only Eagle
  3 has `eagleGroupsCountOnce`: Star and Life let the scout "choose any of the
  merit badges on the required list for Eagle", and Eagle lets them "choose
  only one" of each either-or choice.
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
| `effectiveDate` | Yes | The date the version took effect, as `YYYY-MM-DD`. Most changes take effect January 1. See [Dating a version](#dating-a-version). |
| `requirements` | Yes | The top-level requirements, in official order. |

### Requirement

| Field | Required | Meaning |
|---|---|---|
| `number` | Yes | The official number, written as on the official page: `4`, `4c`, `4c(1)`. Where a rank's lettered requirements have no number above them, nest them under one anyway (see [Rank](#rank)). It identifies the requirement, so it must be unique within the version. For a requirement split into lettered options ("Option A", "Option B"), add the option's letter to the number, then its parts' labels in parentheses: Cycling 6 Option B's part (1)(c) is `6B(1)(c)`. If the page gives the options labels of their own, such as "(a) Option A—Model Railroading", use those labels instead: Railroading 7 Option A's part (1) is `7a(1)`. |
| `summary` | Yes | Our own one-line summary. Aim for 15 words or fewer. This isn't a hard limit: a summary that reads clearly matters more than its length. |
| `children` | No | Sub-requirements, in official order. |
| `requiredCount` | No | For "do N of the following": how many children must be done. Leave it out when all children are required. Also set it to `1` when the children are either/or cases and only one can apply, even though the page gives no count (Personal Management 2b: one child for a budget that overspends, one for a budget with money left over). |
| `tracker` | No | For requirements that need repeated entries, such as a weekly log. See below. |
| `ownWork` | No | For a requirement with `children`, or with a `tracker` with a `rowCount` and no children, that also asks for work of its own: our own one-line summary of just that work, such as `"Take a hunter education course or get a copy of your state's hunting laws."` for Shotgun Shooting 1g. The scout checks it off on the requirement's page, and the requirement is complete once it is and enough children are, or every row is filled in. Add it for any ask no child or row covers, including a closing step such as discussing what you did with your counselor, or summing up two weeks of rows (Competitive Gaming 3b). Leave it out when the requirement only introduces its children ("Do the following", "Discuss these with your counselor:"). A requirement with rows is completed on the date the scout gives its own work, since rows are often typed in long after the work, so word work done before the rows to end with them: `"Set a baseline, then make and carry out a plan to cut your household's food waste."`, not `"Make a plan…"`. When the other work could come in any order, such as an explanation that isn't about the rows (Bird Study 7's what songs are for), use a log instead, whose checkbox dates the requirement once all of it is done. On a requirement with rows, add it for any step that has the scout share, show, present, review, discuss, explain, describe, tell, state, demonstrate or report something, said to the counselor or not, even when it's the rows themselves (Hiking 5's "Share this with your counselor") or each row is what the scout tells (Cybersecurity 6a's three uses of encryption), and for the counselor's approval (Cycling 6B(4)'s trails) or help (Geology 4C(3)(b)'s identifying): otherwise typing in the rows would complete the requirement before the scout did it. Writing things down, such as listing, recording, naming, identifying or writing a report, isn't such a step unless it's for the counselor (Coin Collecting 7a's "identify for your counselor"), so Cycling 6A(2)'s report of its six rides needs none. A log can't have it, since the scout checks off a log's requirement. The `summary` still describes the whole requirement, including any step with the counselor. |
| `monthsInRank` | No | Only on a rank's requirement that asks for months in the rank below, such as Star 1's four months as a First Class Scout: how many months, at least 1. Its page shows the date the scout becomes eligible, counted from when they earned the rank below, but the scout still checks it off. The lowest rank can't have it, since no rank is below it. See [Rank](#rank) for which requirements have it. |
| `meritBadges` | No | Only on a rank's requirement that asks for merit badges, such as Star 3's six, at least four of them Eagle-required: `{"total": 6, "eagleRequired": 4}`, the badges needed in all and how many of them must be Eagle-required, each at least 1. Every Eagle-required badge counts toward `eagleRequired`, unless it has `"eagleGroupsCountOnce": true`: then only one badge of each `eagleGroup` does, and the group's others count only toward `total`. Set that where the official text lets the scout choose only one badge of each either-or choice. The requirement is complete once the scout has completed enough badges, so they don't check it off, and it can't have `children` or a `tracker`. See [Rank](#rank) for which requirements have it. |

### Tracker

A tracker is a table the scout fills in, one row per entry.

| Field | Required | Meaning |
|---|---|---|
| `columns` | Yes | The table's columns, each with an `id` (lowercase words joined by `-`, unique within the tracker), a `label` shown to the scout, and a `type`: `date`, `number`, `text` or `multiline-text`. Use `multiline-text` for a description or a list, such as "What you saw", and `text` for a name or a short value. A `date` takes only dates up to today, so use `text` for one that can be in the future, such as an alarm's expiration date. A `number` takes digits and one decimal separator but no minus sign, so use `text` for one that can be negative, such as a temperature. A `number` column can also have a `total` ([Column total](#column-total)). |
| `rowLabel` | Yes | What one row is called, in lowercase, such as `week` or `session`. The app capitalizes it for titles, such as "Week 3". |
| `rowLabelPlural` | Yes | `rowLabel` in the plural, in lowercase, such as `weeks`. The requirement's row shows it in a count, such as "8 of 12 weeks". |
| `rowCount` | No | A fixed number of rows, such as `13` for a 13-week budget. The scout fills in each one ("Week 1" to "Week 13"). Set it when the requirement asks for a set number of things, days or weeks, even when a condition across rows can't be checked (Coin Collecting 7a's 20 coins from seven or more countries). Leave it out for a log the scout adds rows to, any number of them: when the number is only a minimum, so the scout can record more (American Labor 1's "at least EIGHT" concerns, but not Genealogy 2b's six weeks of writing "at least once a week"); when it depends on which option the scout picks (Geology 4D(5)'s 10 collected or 15 identified); when the rows are things that happen during a period rather than its days or weeks (Dog Care 4's two months of feeding, vet care and costs, or Oceanography 7f's three weeks of checking satellite images, where Sustainability 3c's two weeks of results are 14 day rows); or when the scout logs things as they happen and only some may count (Second Class 1a's activities, of which at least three must be outdoors, or Orienteering 7a's events, one of them cross-country). |
| `rowsNeeded` | No | In a log, how many rows its requirement asks for, such as `10` for Second Class 4's "at least 10 kinds of wild animals". The requirement's count is then out of it, "6 of 10 animals", and goes on past it ("12 of 10 animals"), and each row up to it adds to the progress bar. It's only a guide: the scout still checks the requirement off. Set it whenever a log's requirement asks for a number of rows, including one that's a log because of its other work (Engineering 6b's 10 appliances) or because only some rows may count (Second Class 1a's five activities). When the number has parts, use their sum: `25` for Plant Science 6's 10 native, 10 cultivated and five invasive plants. When it's in a requirement this one refers to, use it: `5` for Backpacking 11c's daily journal of 11a's trek of at least five days. Leave it out when the number depends on which option the scout picks (Geology 4D(5)) or on what they find (Textile 2b's types of fiber, which 2a's five swatches can share), or the requirement gives none (Dog Care 4). It can't be on a tracker with a `rowCount` or a `total`, or on a requirement with `children`. |

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
and needs all of them. The requirement then shows the
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
  at least one version, and every badge's URL is a
  `https://www.scouting.org/merit-badges/` page;
- a badge or rank doesn't have two versions with the same effective date, and
  `eagleGroup` is only set on Eagle-required badges;
- every version has requirements, requirement numbers are unique within it,
  top-level numbers are whole numbers, such as `1` but not `1a`, and each
  sub-requirement's number starts with its parent's, not followed by a digit,
  such as `2a` under `2` but not `20`;
- every requirement has a number and a summary, `requiredCount` is between 1
  and the number of children, and `ownWork` is only set, and not blank, on a
  requirement with children or a tracker with a `rowCount`;
- `monthsInRank` is at least 1, and only on a requirement of a rank other than
  the lowest;
- `meritBadges` is only on a rank's requirement with no children or tracker,
  its `total` is at least 1 and no more than the catalog's badges, and its
  `eagleRequired` is between 1 and its `total`, and no more than the catalog's
  Eagle-required badges, counting each `eagleGroup` once with
  `eagleGroupsCountOnce`;
- trackers have at least one column, unique column IDs, labels, row labels
  that start with a lowercase letter, a `rowCount` of at least 1 when set, and
  a `rowsNeeded` of at least 1 only on a log without a `total`, on a
  requirement without `children`;
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

It also can't check that a step with the counselor, or anyone else, made it
into the catalog, so reviewers check that too. Search the official text of
each requirement you add or change for "counselor" and for share, show,
present, review, discuss, explain, describe, tell, state, demonstrate and
report. Each step you find belongs in the summary, and so does any approval or
help from the counselor. On a requirement the app completes by itself, one
with a `rowCount` or with `children`, each of them also needs `ownWork` or a
child that covers it: Coin Collecting 7a's "identify for your counselor" was
once left out of both, so typing in 20 coins completed it.

Write each step with a verb that has the scout say or show something, such as
explain, describe, tell, discuss, share, show, present or review. A verb the
scout could satisfy on paper isn't enough: "Compare automatic and standard
transmissions." became "Explain how automatic and standard transmissions
differ." (Automotive Maintenance 9b), and "Adjust the eyepiece…" became "Show
how to adjust the eyepiece…" (Bird Study 3b). The verb is enough without "to
your counselor" (Backpacking 5b: "Explain why drinking enough water on a trek
matters."). Do name the counselor for their approval, help, guidance or
supervision, or for work done with them, even an approval that covers only one
choice (Emergency Preparedness 2c: "Using the pamphlet's checklist or one your
counselor approves…"), and name anyone else a step is for, such as a troop, a
family or the patrol leaders' council. Children that are only topics don't
repeat a verb our parent summary already gives them, such as First Aid 3's
wounds or Swimming 3's strokes ("Show five swimming strokes with good form."
over "Back crawl."); otherwise each child names its own step.

When a parent needs the counselor's approval, help, guidance or supervision, or
work done with the counselor, no child covers it, so it gets `ownWork`, even
when it applies to every child: "Get permission and your counselor's approval
before you start." (Pulp and Paper 7's "With your parent or guardian's and
counselor's approval, do ONE of the following"), "Before each hike, share a
written plan with your counselor or a designee for approval." (Hiking 4), or
"Use two kinds of floating aids your counselor provides." (Lifesaving 8). A
step that is itself each child's work is different: under Cybersecurity 3's
"Do the following and discuss each with your counselor", the children cover
the discussing. Two other cases need no `ownWork`. One is a step someone else
may do instead, which isn't a counselor step: Home Repairs 2's "Under the
supervision of your parent, guardian, or counselor", Motorboating 5's "With
your counselor or other adults on board", or Climbing 5's help from "the
counselor or another Scout". The other is work done with the counselor when
the children are that work, such as Cycling 6A(1)'s road safety test.
