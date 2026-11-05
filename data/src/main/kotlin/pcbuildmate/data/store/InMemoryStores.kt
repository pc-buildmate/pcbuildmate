package pcbuildmate.data.store

import pcbuildmate.contracts.Build
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.BuildStorePort
import pcbuildmate.contracts.DraftStorePort

class InMemoryBuildStore : BuildStorePort {
    private val builds = linkedMapOf<String, Build>()
    private val revisions = mutableMapOf<String, MutableMap<Long, BuildRevision>>()

    override fun loadBuild(buildId: String): Build? = builds[buildId]

    override fun listSavedBuilds(): List<Build> = builds.values.toList()

    override fun listRevisions(buildId: String): List<BuildRevision> =
        revisions[buildId]?.values?.sortedBy { it.id }.orEmpty()

    override fun saveRevision(build: Build, revision: BuildRevision) {
        builds[build.buildId] = build
        revisions.getOrPut(build.buildId) { linkedMapOf() }[revision.id] = revision
    }

    override fun deleteBuild(buildId: String) {
        builds.remove(buildId)
        revisions.remove(buildId)
    }
}

class InMemoryDraftStore : DraftStorePort {
    private val drafts = mutableMapOf<String, BuildRevision>()

    override fun saveDraft(buildId: String, revision: BuildRevision) {
        drafts[buildId] = revision
    }

    override fun loadDraft(buildId: String): BuildRevision? = drafts[buildId]

    override fun clearDraft(buildId: String) {
        drafts.remove(buildId)
    }
}
