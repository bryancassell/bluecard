package io.github.bryancassell.bluecard.data.progress

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

/** [MigrationTest] under Robolectric, which Android's database builder and helper need. */
@RunWith(AndroidJUnit4::class)
class AndroidMigrationTest : MigrationTest()
