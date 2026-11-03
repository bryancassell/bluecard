package io.github.bryancassell.bluecard.data.report

/** [ReportPagingContract] with Core Text's line heights and breaks, on the iOS simulator. */
class CoreTextPagingTest : ReportPagingContract<CoreTextText>() {
    override val typesetter = CoreTextTypesetter(usEnglish, false, "en")
}
