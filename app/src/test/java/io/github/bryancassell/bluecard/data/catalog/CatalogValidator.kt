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
        // Badge and rank progress is stored in the same tables, keyed by ID.
        (catalog.badges + catalog.ranks).groupBy { it.id }.filterValues { it.size > 1 }.keys
            .forEach { add("id \"$it\" is used more than once") }
        catalog.badges.forEach { addAll(validateBadge(it)) }
        catalog.ranks.forEach { addAll(validateAdvancement("rank \"${it.id}\"", it)) }
    }

    private fun validateBadge(badge: MeritBadge): List<String> = buildList {
        val where = "badge \"${badge.id}\""
        addAll(validateAdvancement(where, badge))
        if (badge.eagleGroup != null && !badge.eagleRequired) {
            add("$where: has an eagleGroup but is not eagleRequired")
        }
    }

    private fun validateAdvancement(where: String, advancement: Advancement): List<String> =
        buildList {
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
            versions.groupBy { it.effectiveDate }.filterValues { it.size > 1 }.keys
                .forEach { add("$where: more than one version effective $it") }
            versions.forEach { version ->
                addAll(validateVersion("$where, version ${version.effectiveDate}", version))
            }
        }

    private fun validateVersion(where: String, version: RequirementsVersion): List<String> =
        buildList {
            if (version.requirements.isEmpty()) add("$where: has no requirements")
            val all = version.requirements.flatMap { it.withDescendants() }
            all.groupBy { it.number }.filterValues { it.size > 1 }.keys.forEach {
                add("$where: requirement number \"$it\" is used more than once")
            }
            all.forEach { addAll(validateRequirement("$where, requirement \"${it.number}\"", it)) }
        }

    private fun validateRequirement(where: String, requirement: Requirement): List<String> =
        buildList {
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
        }

    private fun validateTracker(where: String, tracker: TrackerDefinition): List<String> =
        buildList {
            if (tracker.columns.isEmpty()) add("$where: has no columns")
            tracker.columns.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach {
                add("$where: column id \"$it\" is used more than once")
            }
            tracker.columns.forEach { column ->
                if (!idPattern.matches(column.id)) {
                    add("$where: column id \"${column.id}\" must be lowercase words joined by '-'")
                }
                if (column.label.isBlank()) add("$where: column \"${column.id}\" has a blank label")
            }
            tracker.rowCount?.let { if (it < 1) add("$where: rowCount must be at least 1") }
            addAll(validateRowLabel("$where: rowLabel", tracker.rowLabel))
            addAll(validateRowLabel("$where: rowLabelPlural", tracker.rowLabelPlural))
        }

    // Lowercase, because the app uses it inside sentences ("8 of 12 weeks") and capitalizes it
    // for titles ("Week 3").
    private fun validateRowLabel(where: String, label: String): List<String> = when {
        label.isBlank() -> listOf("$where is blank")

        !label.first().isLowerCase() -> listOf(
            "$where \"$label\" must start with a lowercase letter"
        )

        else -> emptyList()
    }

    private fun Requirement.withDescendants(): List<Requirement> =
        listOf(this) + children.flatMap { it.withDescendants() }

    private const val OFFICIAL_URL_PREFIX = "https://www.scouting.org/"
}
