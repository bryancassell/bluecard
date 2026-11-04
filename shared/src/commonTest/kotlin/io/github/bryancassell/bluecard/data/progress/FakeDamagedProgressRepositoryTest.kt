package io.github.bryancassell.bluecard.data.progress

/** Checks that the fake behaves like the file implementation. */
class FakeDamagedProgressRepositoryTest : DamagedProgressRepositoryContract() {
    override val repository = FakeDamagedProgressRepository()

    override fun setAsideDamagedProgress() = repository.setAside()
}
