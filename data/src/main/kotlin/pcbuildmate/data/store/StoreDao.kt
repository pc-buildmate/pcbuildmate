package pcbuildmate.data.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BuildDao {
    @Query("SELECT * FROM builds ORDER BY updatedAt DESC, buildId ASC")
    fun listBuilds(): List<BuildEntity>

    @Query("SELECT * FROM builds WHERE buildId = :buildId")
    fun loadBuild(buildId: String): BuildEntity?

    @Query("SELECT * FROM revisions WHERE buildId = :buildId ORDER BY id ASC")
    fun listRevisions(buildId: String): List<RevisionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertBuild(build: BuildEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertRevision(revision: RevisionEntity)

    @Query("DELETE FROM builds WHERE buildId = :buildId")
    fun deleteBuild(buildId: String)
}

@Dao
interface DraftDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertDraft(draft: DraftEntity)

    @Query("SELECT * FROM drafts WHERE buildId = :buildId")
    fun loadDraft(buildId: String): DraftEntity?

    @Query("DELETE FROM drafts WHERE buildId = :buildId")
    fun clearDraft(buildId: String)
}
