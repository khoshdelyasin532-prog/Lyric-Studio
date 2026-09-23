package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.converter.Converters
import com.example.data.local.dao.*
import com.example.data.local.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ProjectEntity::class,
        SectionEntity::class,
        IdeaEntity::class,
        PunchlineEntity::class,
        WordBankEntity::class,
        VoiceMemoEntity::class,
        VersionEntity::class,
        TemplateEntity::class,
        GoalEntity::class,
        StreakEntity::class,
        QuickNoteEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun sectionDao(): SectionDao
    abstract fun ideaDao(): IdeaDao
    abstract fun punchlineDao(): PunchlineDao
    abstract fun wordBankDao(): WordBankDao
    abstract fun voiceMemoDao(): VoiceMemoDao
    abstract fun versionDao(): VersionDao
    abstract fun templateDao(): TemplateDao
    abstract fun goalDao(): GoalDao
    abstract fun streakDao(): StreakDao
    abstract fun quickNoteDao(): QuickNoteDao
    abstract fun searchDao(): SearchDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lyric_studio.db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateInitialTemplates(database.templateDao())
                    }
                }
            }

            private suspend fun populateInitialTemplates(dao: TemplateDao) {
                if (dao.getCount() == 0) {
                    dao.insertTemplate(
                        TemplateEntity(
                            name = "الگوی رپ و هیپ‌هاپ",
                            genre = "Rap",
                            description = "مقدمه، دو بند ۱۶ بیتی، ترجیع‌بند و پایان",
                            defaultSectionsJson = """[{"type":"Intro","title":"مقدمه"},{"type":"Verse","title":"بند اول (Verse 1)"},{"type":"Chorus","title":"ترجیع‌بند (Hook)"},{"type":"Verse","title":"بند دوم (Verse 2)"},{"type":"Chorus","title":"ترجیع‌بند"},{"type":"Outro","title":"پایان"}]"""
                        )
                    )
                    dao.insertTemplate(
                        TemplateEntity(
                            name = "الگوی پاپ کلاسیک",
                            genre = "Pop",
                            description = "ساختار Verse-PreChorus-Chorus-Bridge استاندارد",
                            defaultSectionsJson = """[{"type":"Verse","title":"بند ۱"},{"type":"PreChorus","title":"پیش‌ترجیع"},{"type":"Chorus","title":"ترجیع‌بند اصلی"},{"type":"Verse","title":"بند ۲"},{"type":"PreChorus","title":"پیش‌ترجیع"},{"type":"Chorus","title":"ترجیع‌بند"},{"type":"Bridge","title":"پل ملودی (Bridge)"},{"type":"Chorus","title":"ترجیع‌بند نهایی"}]"""
                        )
                    )
                    dao.insertTemplate(
                        TemplateEntity(
                            name = "شعر و ادب کلاسیک",
                            genre = "Poetry",
                            description = "بندها و ابیات متناوب شعر سنتی و نو",
                            defaultSectionsJson = """[{"type":"Poem","title":"بند اول"},{"type":"Poem","title":"بند دوم"},{"type":"Poem","title":"بند سوم"},{"type":"Poem","title":"مقطع نهایی"}]"""
                        )
                    )
                    dao.insertTemplate(
                        TemplateEntity(
                            name = "فری‌استایل آزاد",
                            genre = "Freestyle",
                            description = "جریان کلمات پیوسته بدون محدودیت ساختار",
                            defaultSectionsJson = """[{"type":"Text","title":"جریان آزاد کلمات"}]"""
                        )
                    )
                }
            }
        }
    }
}
