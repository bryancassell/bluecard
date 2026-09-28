package io.github.bryancassell.bluecard.data.profile

/** Checks that the fake behaves like the DataStore implementation. */
class FakeProfileRepositoryTest : ProfileRepositoryContract() {
    override val repository = FakeProfileRepository()
}
