package io.github.bryancassell.bluecard.data.catalog

/**
 * Checks the rules the catalog's JSON structure can't express. Returns one message per
 * problem, naming where it is, or an empty list if the catalog is valid.
 */
object CatalogValidator {
    private val idPattern = Regex("[a-z0-9]+(-[a-z0-9]+)*")
    private val wholeNumber = Regex("[0-9]+")

    fun validate(catalog: Catalog): List<String> = buildList {
        if (catalog.formatVersion != CATALOG_FORMAT_VERSION) {
            add("formatVersion is ${catalog.formatVersion}; this app reads $CATALOG_FORMAT_VERSION")
        }
        if (catalog.badges.isEmpty()) add("the catalog has no badges")
        catalog.badges.duplicatesBy { it.id }.forEach {
            add("badge id \"$it\" is used more than once")
        }
        catalog.ranks.duplicatesBy { it.id }.forEach {
            add("rank id \"$it\" is used more than once")
        }
        // Badge and rank progress is stored in the same tables, keyed by ID.
        catalog.badges.map { it.id }.intersect(catalog.ranks.map { it.id }.toSet()).forEach {
            add("id \"$it\" is used by both a badge and a rank")
        }
        val badgeRules = RequirementRules(
            noMonthsInRank = "it isn't a rank's",
            noMeritBadges = "it isn't a rank's",
            badges = catalog.badges.size,
            eagleRequiredBadges = catalog.badges.count { it.eagleRequired },
            eagleSlots = catalog.badges.eagleSlots().size
        )
        catalog.badges.forEach { addAll(validateBadge(it, badgeRules)) }
        catalog.ranks.forEachIndexed { index, rank ->
            val rules = badgeRules.copy(
                // Time in rank counts from the rank below.
                noMonthsInRank = if (index == 0) "it's the lowest rank" else null,
                noMeritBadges = null
            )
            addAll(validateAdvancement("rank \"${rank.id}\"", rank, rules, OFFICIAL_URL_PREFIX))
        }
    }

    /** What a requirement can have that depends on the badge or rank it's part of. */
    private data class RequirementRules(
        /** Why it can't have monthsInRank, or null if it can. */
        val noMonthsInRank: String?,
        /** Why it can't have meritBadges, or null if it can. */
        val noMeritBadges: String?,
        /** How many badges the catalog has: the most meritBadges can ask for. */
        val badges: Int,
        /** How many Eagle-required badges the catalog has: the most meritBadges can ask for. */
        val eagleRequiredBadges: Int,
        /**
         * The same, counting each "one of" group once ([eagleSlots]): the most meritBadges can ask
         * for when its groups count once.
         */
        val eagleSlots: Int
    )

    private fun validateBadge(badge: MeritBadge, rules: RequirementRules): List<String> =
        buildList {
            val where = "badge \"${badge.id}\""
            addAll(validateAdvancement(where, badge, rules, BADGE_URL_PREFIX))
            if (badge.eagleGroup != null && !badge.eagleRequired) {
                add("$where: has an eagleGroup but is not eagleRequired")
            }
        }

    private fun validateAdvancement(
        where: String,
        advancement: Advancement,
        rules: RequirementRules,
        urlPrefix: String
    ): List<String> = buildList {
        if (!idPattern.matches(advancement.id)) {
            add("$where: id must be lowercase words joined by '-'")
        }
        if (advancement.name.isBlank()) add("$where: name is blank")
        if (advancement.summary.isBlank()) add("$where: summary is blank")
        if (!advancement.officialUrl.startsWith(urlPrefix)) {
            add("$where: officialUrl must start with $urlPrefix")
        }
        val versions = advancement.requirementVersions
        if (versions.isEmpty()) add("$where: has no requirement versions")
        versions.duplicatesBy { it.effectiveDate }
            .forEach { add("$where: more than one version effective $it") }
        versions.forEach { version ->
            addAll(validateVersion("$where, version ${version.effectiveDate}", version, rules))
        }
    }

    private fun validateVersion(
        where: String,
        version: RequirementsVersion,
        rules: RequirementRules
    ): List<String> = buildList {
        if (version.requirements.isEmpty()) add("$where: has no requirements")
        // Lettered requirements go under a numbered parent, even where the official text has none.
        // A blank number is reported as blank, so it's skipped here.
        version.requirements
            .filter { it.number.isNotBlank() && !wholeNumber.matches(it.number) }
            .forEach {
                add(
                    "$where, requirement \"${it.number}\": " +
                        "is at the top level, so its number must be a whole number"
                )
            }
        val all = version.requirements.flatMap { it.withDescendants() }
        // A digit would make it another number: "10" isn't under "1".
        all.filter { it.number.isNotBlank() }.forEach { parent ->
            parent.children
                .filter { it.number.isNotBlank() && !it.number.isUnder(parent.number) }
                .forEach {
                    add(
                        "$where, requirement \"${it.number}\": " +
                            "is under \"${parent.number}\", so its number must start with " +
                            "\"${parent.number}\", not followed by a digit"
                    )
                }
        }
        all.duplicatesBy { it.number }.forEach {
            add("$where: requirement number \"$it\" is used more than once")
        }
        all.forEach {
            addAll(validateRequirement("$where, requirement \"${it.number}\"", it, rules))
        }
    }

    private fun validateRequirement(
        where: String,
        requirement: Requirement,
        rules: RequirementRules
    ): List<String> = buildList {
        if (requirement.number.isBlank()) add("$where: number is blank")
        if (requirement.summary.isBlank()) add("$where: summary is blank")
        requirement.requiredCount?.let { count ->
            val children = requirement.children.size
            if (count !in 1..children) {
                add("$where: requiredCount is $count but it has $children children")
            }
        }
        requirement.ownWork?.let { ownWork ->
            if (requirement.children.isEmpty() && requirement.tracker?.rowCount == null) {
                add("$where: ownWork but it has no children or fixed-row tracker")
            }
            if (ownWork.isBlank()) add("$where: ownWork is blank")
        }
        requirement.tracker?.let { addAll(validateTracker("$where, tracker", it)) }
        // Its children decide how much of it is done, so its rows or total wouldn't add to the bar.
        if (requirement.children.isNotEmpty()) {
            requirement.tracker?.let { tracker ->
                if (tracker.rowsNeeded != null) {
                    add("$where: tracker has rowsNeeded but the requirement has children")
                }
                if (tracker.columns.any { it.total != null }) {
                    add("$where: tracker has a total but the requirement has children")
                }
            }
        }
        requirement.monthsInRank?.let {
            if (it < 1) add("$where: monthsInRank must be at least 1")
            rules.noMonthsInRank?.let { why -> add("$where: has monthsInRank but $why") }
        }
        requirement.meritBadges?.let { addAll(validateMeritBadges(where, requirement, it, rules)) }
    }

    private fun validateMeritBadges(
        where: String,
        requirement: Requirement,
        needed: MeritBadgesNeeded,
        rules: RequirementRules
    ): List<String> = buildList {
        rules.noMeritBadges?.let { why -> add("$where: has meritBadges but $why") }
        // Its completion comes from badge progress alone.
        if (requirement.children.isNotEmpty()) add("$where: has meritBadges and children")
        if (requirement.tracker != null) add("$where: has meritBadges and a tracker")
        if (needed.total < 1) add("$where: meritBadges total must be at least 1")
        if (needed.total > rules.badges) {
            add(
                "$where: meritBadges asks for ${needed.total} badges, but the catalog has ${rules.badges}"
            )
        }
        if (needed.eagleRequired !in 1..needed.total) {
            add("$where: meritBadges eagleRequired must be between 1 and its total")
        }
        val most = if (needed.eagleGroupsCountOnce) rules.eagleSlots else rules.eagleRequiredBadges
        if (needed.eagleRequired > most) {
            val counted = if (needed.eagleGroupsCountOnce) ", counting each group once" else ""
            add(
                "$where: meritBadges asks for ${needed.eagleRequired} Eagle-required badges, " +
                    "but the catalog has $most$counted"
            )
        }
    }

    private fun validateTracker(where: String, tracker: TrackerDefinition): List<String> =
        buildList {
            if (tracker.columns.isEmpty()) add("$where: has no columns")
            tracker.columns.duplicatesBy { it.id }.forEach {
                add("$where: column id \"$it\" is used more than once")
            }
            tracker.columns.forEach { column ->
                if (!idPattern.matches(column.id)) {
                    add("$where: column id \"${column.id}\" must be lowercase words joined by '-'")
                }
                if (column.label.isBlank()) add("$where: column \"${column.id}\" has a blank label")
                column.total?.let {
                    addAll(validateTotal("$where, column \"${column.id}\"", column.type, it))
                }
            }
            addAll(validatePartsOf(where, tracker.columns))
            tracker.rowCount?.let { if (it < 1) add("$where: rowCount must be at least 1") }
            // A fixed-row tracker's row shows how many rows are filled in, which complete it.
            if (tracker.rowCount != null && tracker.columns.any { it.total != null }) {
                add("$where: has a total but a fixed number of rows")
            }
            tracker.rowsNeeded?.let { if (it < 1) add("$where: rowsNeeded must be at least 1") }
            // A fixed-row tracker's count is already out of its rows.
            if (tracker.rowCount != null && tracker.rowsNeeded != null) {
                add("$where: has rowsNeeded but a fixed number of rows")
            }
            // A requirement's row shows its totals in place of how many rows there are.
            if (tracker.rowsNeeded != null && tracker.columns.any { it.total != null }) {
                add("$where: has rowsNeeded and a total")
            }
            addAll(validateLowercaseLabel("$where: rowLabel", tracker.rowLabel))
            addAll(validateLowercaseLabel("$where: rowLabelPlural", tracker.rowLabelPlural))
        }

    private fun validateTotal(
        where: String,
        type: TrackerColumnType,
        total: ColumnTotal
    ): List<String> = buildList {
        if (type != TrackerColumnType.NUMBER) add("$where: has a total but isn't a number")
        if (total.needed < 1) add("$where: total needed must be at least 1")
        addAll(validateLowercaseLabel("$where: total label", total.label))
        addAll(validateLowercaseLabel("$where: total labelPlural", total.labelPlural))
    }

    // The progress bar counts one total, only as far as the one total that's part of it allows.
    private fun validatePartsOf(where: String, columns: List<TrackerColumn>): List<String> =
        buildList {
            val totals = columns.mapNotNull { column -> column.total?.let { column.id to it } }
                .toMap()
            if (totals.values.count { it.partOf == null } > 1) {
                add("$where: has more than one total that isn't part of another")
            }
            for ((id, total) in totals) {
                val partOf = total.partOf ?: continue
                val whole = totals[partOf]
                when {
                    partOf == id -> add("$where, column \"$id\": partOf \"$id\" is its own column")

                    whole == null ->
                        add(
                            "$where, column \"$id\": partOf \"$partOf\" isn't a column with a total"
                        )

                    whole.partOf != null ->
                        add("$where, column \"$id\": partOf \"$partOf\" is part of another total")

                    total.needed > whole.needed ->
                        add("$where, column \"$id\": total needed is more than \"$partOf\"'s")
                }
            }
            totals.values.mapNotNull { it.partOf }.duplicatesBy { it }.forEach {
                add("$where: more than one total is part of \"$it\"")
            }
        }

    // Lowercase, because the app uses it inside sentences ("8 of 12 weeks", "4 of 6 hours") and
    // capitalizes a row label for titles ("Week 3").
    private fun validateLowercaseLabel(where: String, label: String): List<String> = when {
        label.isBlank() -> listOf("$where is blank")

        !label.first().isLowerCase() -> listOf(
            "$where \"$label\" must start with a lowercase letter"
        )

        else -> emptyList()
    }

    /** The keys that more than one of these items has. */
    private fun <T, K> List<T>.duplicatesBy(key: (T) -> K): Set<K> =
        groupBy(key).filterValues { it.size > 1 }.keys

    private fun String.isUnder(parent: String): Boolean =
        startsWith(parent) && getOrNull(parent.length)?.isDigit() != true

    private fun Requirement.withDescendants(): List<Requirement> =
        listOf(this) + children.flatMap { it.withDescendants() }

    private const val OFFICIAL_URL_PREFIX = "https://www.scouting.org/"

    /** Official merit badges' pages. Test Lab pilots' pages are elsewhere, so they fail. */
    private const val BADGE_URL_PREFIX = "${OFFICIAL_URL_PREFIX}merit-badges/"
}
