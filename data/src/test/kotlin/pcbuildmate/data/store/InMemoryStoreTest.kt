package pcbuildmate.data.store

import pcbuildmate.contracts.testing.BuildStorePortContract
import pcbuildmate.contracts.testing.DraftStorePortContract

class InMemoryBuildStoreTest : BuildStorePortContract() {
    override fun newStore() = InMemoryBuildStore()
}

class InMemoryDraftStoreTest : DraftStorePortContract() {
    override fun newStore() = InMemoryDraftStore()
}
