package com.example.aiwithlove.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Database(
    entities = [DocumentChunkEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class EmbeddingsDatabase : RoomDatabase() {
    abstract fun documentChunkDao(): DocumentChunkDao

    companion object {
        @Volatile
        private var INSTANCE: EmbeddingsDatabase? = null

        fun getDatabase(context: Context): EmbeddingsDatabase =
            INSTANCE ?: synchronized(this) {
                val instance =
                    Room.databaseBuilder(
                        context.applicationContext,
                        EmbeddingsDatabase::class.java,
                        "embeddings_database"
                    )
                        .fallbackToDestructiveMigration()
                        .build()
                INSTANCE = instance
                instance
            }
    }
}

/**
 * Type converters for storing embeddings as JSON
 */
class Converters {
    @TypeConverter
    fun fromEmbeddingList(value: List<Double>): String = Json.encodeToString(value)

    @TypeConverter
    fun toEmbeddingList(value: String): List<Double> = Json.decodeFromString(value)
}
