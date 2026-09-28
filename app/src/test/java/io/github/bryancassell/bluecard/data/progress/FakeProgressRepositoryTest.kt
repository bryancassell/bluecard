package io.github.bryancassell.bluecard.data.progress

/** Checks that the fake behaves like the Room implementation. */
class FakeProgressRepositoryTest : ProgressRepositoryContract() {
    override val repository = FakeProgressRepository()
}
