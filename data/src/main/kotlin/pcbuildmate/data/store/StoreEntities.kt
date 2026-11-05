package pcbuildmate.data.store

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "builds")
data class BuildEntity(
    @PrimaryKey val buildId: String,
    val budgetCny: Long,
    val useCase: String,
    val caseSize: String?,
    val noiseLevel: String?,
    val upgradeRoom: Boolean,
    val currentRevisionId: Long,
    val userModified: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(
    tableName = "revisions",
    foreignKeys = [
        ForeignKey(
            entity = BuildEntity::class,
            parentColumns = ["buildId"],
            childColumns = ["buildId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("buildId")],
)
data class RevisionEntity(
    @PrimaryKey val id: Long,
    val buildId: String,
    val totalCny: Long,
    val catalogueVersion: String,
    val priceDate: String,
    val userModified: Boolean,
    val itemsJson: String,
)

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val buildId: String,
    val revisionJson: String,
)

@Serializable
data class StoredItem(
    val componentId: String,
    val category: String,
    val quantity: Int,
    val unitPriceCny: Long,
)

@Serializable
data class StoredRevision(
    val id: Long,
    val buildId: String,
    val totalCny: Long,
    val catalogueVersion: String,
    val priceDate: String,
    val userModified: Boolean,
    val items: List<StoredItem>,
)
