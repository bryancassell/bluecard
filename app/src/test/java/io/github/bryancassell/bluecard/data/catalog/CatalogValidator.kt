package io.github.bryancassell.bluecard.data.catalog

/**
 * Checks the rules the catalog's JSON structure can't express. Returns one message per
 * problem, naming where it is, or an empty list if the catalog is valid.
 */
object CatalogValidator {
    private val idPattern = Regex("[a-z0-9]+(-[a-z0-9]+)*")

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
        catalog.badges.forEach { addAll(validateBadge(it)) }
        catalog.ranks.forEachIndexed { index, rank ->
            // Time in rank counts from the rank below.
            val noMonthsInRank = if (index == 0) "it's the lowest rank" else null
            addAll(validateAdvancement("rank \"${rank.id}\"", rank, noMonthsInRank))
        }
    }

    private fun validateBadge(badge: MeritBadge): List<String> = buildList {
        val where = "badge \"${badge.id}\""
        addAll(validateAdvancement(where, badge, noMonthsInRank = "it isn't a rank's"))
        if (badge.eagleGroup != null && !badge.eagleRequired) {
            add("$where: has an eagleGroup but is not eagleRequired")
        }
    }

    /** [noMonthsInRank] is why its requirements can't have monthsInRank, or null if they can. */
    private fun validateAdvancement(
        where: String,
        advancement: Advancement,
        noMonthsInRank: String?
    ): List<String> = buildList {
        if (!idPattern.matches(advancement.id)) {
            add("$where: id must be lowercase words joined by '-'")
        }
        if (advancement.name.isBlank()) add("$where: name is blank")
        if (advancement.summary.isBlank()) add("$where: summary is blank")
        if (!advancement.officialUrl.startsWith(OFFICIAL_URL_PREFIX)) {
            add("$where: officialUrl must start with $OFFICIAL_URL_PREFIX")
        }
        val versions = advancement.requirementVersions
        if (versions.isEmpty()) add("$where: has no requirement versions")
        versions.duplicatesBy { it.effectiveDate }
            .forEach { add("$where: more than one version effective $it") }
        versions.forEach { version ->
            addAll(
                validateVersion(
                    "$where, version ${version.effectiveDate}",
                    version,
                    noMonthsInRank
                )
            )
        }
    }

    private fun validateVersion(
        where: String,
        version: RequirementsVersion,
        noMonthsInRank: String?
    ): List<String> = buildList {
        if (version.requirements.isEmpty()) add("$where: has no requirements")
        val all = version.requirements.flatMap { it.withDescendants() }
        all.duplicatesBy { it.number }.forEach {
            add("$where: requirement number \"$it\" is used more than once")
        }
        all.forEach {
            addAll(
                validateRequirement("$where, requirement \"${it.number}\"", it, noMonthsInRank)
            )
        }
    }

    private fun validateRequirement(
        where: String,
        requirement: Requirement,
        noMonthsInRank: String?
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
            if (requirement.children.isEmpty()) add("$where: ownWork but it has no children")
            if (ownWork.isBlank()) add("$where: ownWork is blank")
        }
        requirement.tracker?.let { addAll(validateTracker("$where, tracker", it)) }
        requirement.monthsInRank?.let {
            if (it < 1) add("$where: monthsInRank must be at least 1")
            noMonthsInRank?.let { why -> add("$where: has monthsInRank but $why") }
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
            tracker.rowCount?.let { if (it < 1) add("$where: rowCount must be at least 1") }
            // A fixed-row tracker's row shows how many rows are filled in, which complete it.
            if (tracker.rowCount != null && tracker.columns.any { it.total != null }) {
                add("$where: has a total but a fixed number of rows")
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

    private fun Requirement.withDescendants(): List<Requirement> =
        listOf(this) + children.flatMap { it.withDescendants() }

    private const val OFFICIAL_URL_PREFIX = "https://www.scouting.org/"
}
