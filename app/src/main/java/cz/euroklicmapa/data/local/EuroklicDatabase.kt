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
    version = 8,
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

        /** `favorites` becomes a full detail snapshot — 18 nullable columns so a favourited
         *  place opens with the complete detail body offline / when it's not in the feed cache. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE favorites ADD COLUMN description TEXT")
                db.execSQL("ALTER TABLE favorites ADD COLUMN note TEXT")
                db.execSQL("ALTER TABLE favorites ADD COLUMN photoUrl TEXT")
                db.execSQL("ALTER TABLE favorites ADD COLUMN webUrl TEXT")
                db.execSQL("ALTER TABLE favorites ADD COLUMN openingHours TEXT")
                db.execSQL("ALTER TABLE favorites ADD COLUMN access TEXT")
                db.execSQL("ALTER TABLE favorites ADD COLUMN wheelchair TEXT")
                db.execSQL("ALTER TABLE favorites ADD COLUMN accessibilityNote TEXT")
                db.execSQL("ALTER TABLE favorites ADD COLUMN country TEXT")
                db.execSQL("ALTER TABLE favorites ADD COLUMN floorPlanUrl TEXT")
                db.execSQL("ALTER TABLE favorites ADD COLUMN address TEXT")
                db.execSQL("ALTER TABLE favorites ADD COLUMN phone TEXT")
                db.execSQL("ALTER TABLE favorites ADD COLUMN email TEXT")
                db.execSQL("ALTER TABLE favorites ADD COLUMN hours TEXT")
                db.execSQL("ALTER TABLE favorites ADD COLUMN district TEXT")
                db.execSQL("ALTER TABLE favorites ADD COLUMN kraj TEXT")
                db.execSQL("ALTER TABLE favorites ADD COLUMN precision TEXT")
                db.execSQL("ALTER TABLE favorites ADD COLUMN sourceUrl TEXT")
            }
        }
    }
}
