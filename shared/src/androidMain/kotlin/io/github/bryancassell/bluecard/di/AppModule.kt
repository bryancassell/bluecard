package io.github.bryancassell.bluecard.di

import io.github.bryancassell.bluecard.MainActivityViewModel
import io.github.bryancassell.bluecard.data.profile.DataStoreProfileRepository
import io.github.bryancassell.bluecard.data.profile.ProfileRepository
import io.github.bryancassell.bluecard.data.progress.ProgressRepository
import io.github.bryancassell.bluecard.data.progress.RoomProgressRepository
import io.github.bryancassell.bluecard.ui.badge.BadgeDetailViewModel
import io.github.bryancassell.bluecard.ui.badge.EditCounselorViewModel
import io.github.bryancassell.bluecard.ui.badge.RequirementDetailViewModel
import io.github.bryancassell.bluecard.ui.badge.TrackerEntryViewModel
import io.github.bryancassell.bluecard.ui.badges.BadgesViewModel
import io.github.bryancassell.bluecard.ui.data.DataManagementViewModel
import io.github.bryancassell.bluecard.ui.home.HomeViewModel
import io.github.bryancassell.bluecard.ui.onboarding.OnboardingViewModel
import io.github.bryancassell.bluecard.ui.profile.EditProfileViewModel
import io.github.bryancassell.bluecard.ui.rank.RankDetailViewModel
import io.github.bryancassell.bluecard.ui.ranks.RanksViewModel
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.koin.core.annotation.Qualifier
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.create
import org.koin.plugin.module.dsl.single
import org.koin.plugin.module.dsl.viewModel

/** Marks the dispatcher for disk and other blocking work. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Marks the scope for work that must finish even if the screen that started it closes. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** What the app is built from, apart from what needs Android to build ([androidModule]). */
val appModule = module {
    // Classes take the dispatcher instead of using Dispatchers directly, so tests can swap in a
    // test dispatcher.
    single<CoroutineDispatcher>(named<IoDispatcher>()) { Dispatchers.IO }
    single<CoroutineScope>(named<ApplicationScope>()) { create(::applicationScope) }
    // Dates such as "today" are read from it, so tests can fix the date.
    single<ZonedClock> { DeviceClock }

    single<RoomProgressRepository>() bind ProgressRepository::class
    single<DataStoreProfileRepository>() bind ProfileRepository::class

    viewModel<MainActivityViewModel>()
    viewModel<OnboardingViewModel>()
    viewModel<HomeViewModel>()
    viewModel<BadgesViewModel>()
    viewModel<BadgeDetailViewModel>()
    viewModel<EditCounselorViewModel>()
    viewModel<RequirementDetailViewModel>()
    viewModel<TrackerEntryViewModel>()
    viewModel<RanksViewModel>()
    viewModel<RankDetailViewModel>()
    viewModel<DataManagementViewModel>()
    viewModel<EditProfileViewModel>()
}

/**
 * Lives as long as the app, as the data layer guide advises for work that must outlive the
 * screen: https://developer.android.com/topic/architecture/data-layer#make_an_operation_live_longer_than_the_screen
 * One coroutine failing doesn't cancel the others (SupervisorJob). DataStore reads and writes
 * the profile in it, so it runs on the IO dispatcher. Work run with runOutlivingCaller takes
 * its lifetime and crash handling from it, but not its dispatcher.
 */
internal fun applicationScope(@IoDispatcher ioDispatcher: CoroutineDispatcher): CoroutineScope =
    CoroutineScope(SupervisorJob() + ioDispatcher)

/** A clock with the time zone its dates, such as today's, are read in. */
interface ZonedClock : Clock {
    /**
     * The zone now. Read it each time it's needed, rather than keeping it, as the device's can
     * change while a page is open.
     */
    val timeZone: TimeZone

    fun today(): LocalDate = todayIn(timeZone)
}

/**
 * The system clock in the device's time zone at the moment it's read. A zone read once, when the
 * clock was made, would go on giving "today" in the old zone on a page left open through a time
 * zone change, as when a scout travels to camp (#79).
 */
internal object DeviceClock : ZonedClock {
    override val timeZone: TimeZone get() = TimeZone.currentSystemDefault()

    override fun now(): Instant = Clock.System.now()
}

/**
 * Where the app writes the failures it catches, such as stored data that couldn't be read. It
 * reports them nowhere else (ARCHITECTURE.md, Load and save failures), so this is the only way
 * to tell what failed and why. An interface, so each platform writes them its own way, and tests
 * can read them back: Android's `Log` throws in plain local tests. It's called from any thread,
 * such as the IO dispatcher's, so an implementation must be thread-safe. It must not throw either,
 * since callers write to it while handling a failure.
 */
interface WarningLog {
    /** Writes [message], and [error] if there is one, under [tag]. */
    fun warn(tag: String, message: String, error: Throwable? = null)
}
