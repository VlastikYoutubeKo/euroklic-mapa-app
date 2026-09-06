package cz.euroklicmapa.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WcLocationEntity::class,
        PickupPointEntity::class,
        SyncMetadataEntity::class,
        FavoriteEntity::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class EuroklicDatabase : RoomDatabase() {
    abstract fun dao(): EuroklicDao

    companion object {
        /**
         * `locations` / `pickup_points` are a disposable API cache, but `favorites` is user data —
         * so from v4 on there are real migrations. Older gaps still fall back to destructive.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS favorites (
                        placeId INTEGER NOT NULL,
                        isPickup INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        source TEXT,
                        lastVerified TEXT,
                        likes INTEGER NOT NULL,
                        dislikes INTEGER NOT NULL,
                        longitude REAL NOT NULL,
                        latitude REAL NOT NULL,
                        savedAt INTEGER NOT NULL,
                        PRIMARY KEY(placeId, isPickup)
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE locations ADD COLUMN webUrl TEXT")
                db.execSQL("ALTER TABLE locations ADD COLUMN openingHours TEXT")
                db.execSQL("ALTER TABLE locations ADD COLUMN access TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE locations ADD COLUMN wheelchair TEXT")
                db.execSQL("ALTER TABLE locations ADD COLUMN accessibilityNote TEXT")
                db.execSQL("ALTER TABLE locations ADD COLUMN country TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE locations ADD COLUMN floorPlanUrl TEXT")
            }
        }
    }
}
