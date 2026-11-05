package pcbuildmate.data.store

import kotlinx.serialization.json.Json
import pcbuildmate.contracts.Build
import pcbuildmate.contracts.BuildItem
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.BuildStorePort
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.DraftStorePort
import pcbuildmate.contracts.Preferences
import pcbuildmate.contracts.UseCase

private val json = Json { ignoreUnknownKeys = true }

class RoomBuildStore(private val db: PcbuildmateDatabase) : BuildStorePort {
    private val dao = db.buildDao()

    override fun loadBuild(buildId: String): Build? = dao.loadBuild(buildId)?.toModel()

    override fun listSavedBuilds(): List<Build> = dao.listBuilds().map { it.toModel() }

    override fun listRevisions(buildId: String): List<BuildRevision> =
        dao.listRevisions(buildId).map { it.toModel() }

    override fun saveRevision(build: Build, revision: BuildRevision) {
        db.runInTransaction {
            dao.upsertBuild(build.toEntity())
            dao.upsertRevision(revision.toEntity())
        }
    }

    override fun deleteBuild(buildId: String) = dao.deleteBuild(buildId)
}

class RoomDraftStore(private val db: PcbuildmateDatabase) : DraftStorePort {
    private val dao = db.draftDao()

    override fun saveDraft(buildId: String, revision: BuildRevision) {
        dao.upsertDraft(DraftEntity(buildId, json.encodeToString(revision.toStored())))
    }

    override fun loadDraft(buildId: String): BuildRevision? =
        dao.loadDraft(buildId)?.let { json.decodeFromString<StoredRevision>(it.revisionJson).toModel() }

    override fun clearDraft(buildId: String) = dao.clearDraft(buildId)
}

private fun Build.toEntity() = BuildEntity(
    buildId = buildId,
    budgetCny = budgetCny,
    useCase = useCase.name,
    caseSize = preferences.caseSize,
    noiseLevel = preferences.noiseLevel,
    upgradeRoom = preferences.upgradeRoom,
    currentRevisionId = currentRevisionId,
    userModified = userModified,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun BuildEntity.toModel() = Build(
    buildId = buildId,
    budgetCny = budgetCny,
    useCase = runCatching { UseCase.valueOf(useCase) }.getOrDefault(UseCase.GAMING),
    preferences = Preferences(caseSize = caseSize, noiseLevel = noiseLevel, upgradeRoom = upgradeRoom),
    currentRevisionId = currentRevisionId,
    userModified = userModified,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun BuildRevision.toEntity() = RevisionEntity(
    id = id,
    buildId = buildId,
    totalCny = totalCny,
    catalogueVersion = catalogueVersion,
    priceDate = priceDate,
    userModified = userModified,
    itemsJson = json.encodeToString(
        items.map { StoredItem(it.componentId, it.category.name, it.quantity, it.unitPriceCny) },
    ),
)

private fun RevisionEntity.toModel() = BuildRevision(
    id = id,
    buildId = buildId,
    items = json.decodeFromString<List<StoredItem>>(itemsJson).map {
        BuildItem(it.componentId, Category.valueOf(it.category), it.quantity, it.unitPriceCny)
    },
    totalCny = totalCny,
    catalogueVersion = catalogueVersion,
    priceDate = priceDate,
    userModified = userModified,
)

private fun BuildRevision.toStored() = StoredRevision(
    id = id,
    buildId = buildId,
    totalCny = totalCny,
    catalogueVersion = catalogueVersion,
    priceDate = priceDate,
    userModified = userModified,
    items = items.map { StoredItem(it.componentId, it.category.name, it.quantity, it.unitPriceCny) },
)

private fun StoredRevision.toModel() = BuildRevision(
    id = id,
    buildId = buildId,
    items = items.map { BuildItem(it.componentId, Category.valueOf(it.category), it.quantity, it.unitPriceCny) },
    totalCny = totalCny,
    catalogueVersion = catalogueVersion,
    priceDate = priceDate,
    userModified = userModified,
)
