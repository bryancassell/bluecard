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
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

/**
 * What the app is built from, apart from what needs Android to build ([androidModule]). Each
 * repository is one instance for the whole app, so writes from every screen queue on
 * [RoomProgressRepository]'s one lock.
 */
val appModule = module {
    // Classes take the dispatcher instead of using Dispatchers directly, so tests can swap in a
    // test dispatcher.
    single<CoroutineDispatcher>(named<IoDispatcher>()) { Dispatchers.IO }
    single<CoroutineScope>(named<ApplicationScope>()) { create(::applicationScope) }
    // Dates such as "today" are read from it, so tests can fix the date.
    single<Clock> { DeviceClock }

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
 * One coroutine failing doesn't cancel the others (SupervisorJob). Its work is saving to disk,
 * so it runs on the IO dispatcher.
 */
internal fun applicationScope(@IoDispatcher ioDispatcher: CoroutineDispatcher): CoroutineScope =
    CoroutineScope(SupervisorJob() + ioDispatcher)

/**
 * The system clock in the device's time zone at the moment it's read. [Clock.systemDefaultZone]
 * keeps the zone it was made in, so a page left open through a time zone change, as when a scout
 * travels to camp, would go on using the old zone for "today" (#79).
 */
internal object DeviceClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()

    override fun withZone(zone: ZoneId): Clock = Clock.system(zone)

    override fun instant(): Instant = Instant.now()
}
