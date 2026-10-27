package io.github.bryancassell.bluecard.di

import io.github.bryancassell.bluecard.MainActivityViewModel
import io.github.bryancassell.bluecard.data.backup.BackupRepository
import io.github.bryancassell.bluecard.data.backup.JsonBackupRepository
import io.github.bryancassell.bluecard.data.catalog.AssetCatalogRepository
import io.github.bryancassell.bluecard.data.catalog.CatalogRepository
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.create
import org.koin.plugin.module.dsl.single
import org.koin.plugin.module.dsl.viewModel

/**
 * What the app is built from, apart from what needs Android to build ([androidModule]). It's in
 * common code once the ViewModels it lists are (#345).
 */
val appModule = module {
    // Classes take the dispatcher instead of using Dispatchers directly, so tests can swap in a
    // test dispatcher.
    single<CoroutineDispatcher>(named<IoDispatcher>()) { Dispatchers.IO }
    single<CoroutineScope>(named<ApplicationScope>()) { create(::applicationScope) }
    // Dates such as "today" are read from it, so tests can fix the date.
    single<ZonedClock> { DeviceClock }

    single<AssetCatalogRepository>() bind CatalogRepository::class
    single<RoomProgressRepository>() bind ProgressRepository::class
    single<DataStoreProfileRepository>() bind ProfileRepository::class
    single<JsonBackupRepository>() bind BackupRepository::class

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
