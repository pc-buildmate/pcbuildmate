package pcbuildmate.data.store

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import pcbuildmate.contracts.testing.BuildStorePortContract
import pcbuildmate.contracts.testing.DraftStorePortContract

@RunWith(AndroidJUnit4::class)
class RoomBuildStoreTest : BuildStorePortContract() {
    private lateinit var db: PcbuildmateDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            PcbuildmateDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun close() {
        db.close()
    }

    override fun newStore(): RoomBuildStore = RoomBuildStore(db)
}

@RunWith(AndroidJUnit4::class)
class RoomDraftStoreTest : DraftStorePortContract() {
    private lateinit var db: PcbuildmateDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PcbuildmateDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun close() {
        db.close()
    }

    override fun newStore(): RoomDraftStore = RoomDraftStore(db)
}
