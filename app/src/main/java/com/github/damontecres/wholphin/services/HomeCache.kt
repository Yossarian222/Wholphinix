package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.ui.seasonal.Holiday
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.ui.util.StringProvider
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.requestSerializersModule
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One loaded home row. [config] is the row's [HomeRowConfig], or null for the seasonal row which is not a saved row.
 */
@Serializable
data class CachedHomeRow(
    val config: HomeRowConfig?,
    val title: StringProvider,
    val items: List<BaseItem?>,
    val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    val rowType: HomeRowConfig? = null,
    val showViewMore: Boolean = true,
) {
    fun toState() = HomeRowLoadingState.Success(title, items, viewOptions, rowType, showViewMore)

    companion object {
        fun from(
            config: HomeRowConfig?,
            row: HomeRowLoadingState.Success,
        ) = CachedHomeRow(config, row.title, row.items, row.viewOptions, row.rowType, row.showViewMore)
    }
}

/**
 * The last loaded rows of a user's home page
 *
 * @param seasonal the [Holiday.key] of the seasonal row, if [rows] contains it
 */
@Serializable
data class CachedHome(
    val rows: List<CachedHomeRow>,
    val seasonal: String? = null,
)

@Serializable
private data class CachedHomeFile(
    /** Identifies the installed app, see [HomeCache.appStamp] */
    val stamp: Long,
    val home: CachedHome,
)

/**
 * Remembers the last loaded home rows of each user, in memory and on disk, so the home page shows them right away (also
 * after the app starts) while it refreshes them in the background.
 *
 * The disk copy is dropped when the app is updated, since it contains string resource IDs.
 */
@Singleton
class HomeCache
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val cacheDir: File
            get() = File(context.cacheDir, "home")
        private val memory = ConcurrentHashMap<UUID, CachedHome>()
        private val mutex = Mutex()

        /**
         * Changes whenever the app is installed/updated, so resource IDs in the disk cache stay valid
         */
        private val appStamp: Long by lazy {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
            }.getOrDefault(0L)
        }

        private fun file(userId: UUID) = File(cacheDir, "${userId.toServerString()}.json")

        /**
         * The cached home of the user, from memory or else from disk
         */
        suspend fun get(userId: UUID): CachedHome? {
            memory[userId]?.let { return it }
            return withContext(WholphinDispatchers.IO) {
                mutex.withLock {
                    try {
                        val file = file(userId)
                        if (!file.exists()) {
                            null
                        } else {
                            json
                                .decodeFromString<CachedHomeFile>(file.readText())
                                .takeIf { it.stamp == appStamp }
                                ?.home
                                ?.also { memory.putIfAbsent(userId, it) }
                        }
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not read the home cache")
                        null
                    }
                }
            }
        }

        /**
         * Remembers the home in memory only, it is cheap so it can be called on every row update
         */
        fun putInMemory(
            userId: UUID,
            home: CachedHome,
        ) {
            memory[userId] = home
        }

        /**
         * Remembers the home in memory and writes it to disk
         */
        suspend fun put(
            userId: UUID,
            home: CachedHome,
        ) {
            memory[userId] = home
            withContext(WholphinDispatchers.IO) {
                mutex.withLock {
                    try {
                        cacheDir.mkdirs()
                        val file = file(userId)
                        val tmp = File(cacheDir, "${file.name}.tmp")
                        tmp.writeText(json.encodeToString(CachedHomeFile(appStamp, home)))
                        if (!tmp.renameTo(file)) {
                            file.delete()
                            tmp.renameTo(file)
                        }
                    } catch (ex: Exception) {
                        Timber.w(ex, "Could not write the home cache")
                    }
                }
            }
        }

        companion object {
            private val json =
                Json {
                    // Same as the back stack, which also stores items and destinations
                    classDiscriminator = "_type"
                    serializersModule = requestSerializersModule
                    ignoreUnknownKeys = true
                }

            /**
             * Builds the [CachedHome] to store from the home [rows] for [settingsRows] (preceded by the [seasonal] row if
             * any). Only loaded rows are kept.
             */
            fun toCache(
                rows: List<HomeRowLoadingState>,
                settingsRows: List<HomeRowConfig>,
                seasonal: Holiday?,
            ): CachedHome {
                val offset = if (seasonal != null) 1 else 0
                val cached =
                    rows.mapIndexedNotNull { index, row ->
                        if (row !is HomeRowLoadingState.Success) return@mapIndexedNotNull null
                        val config =
                            if (index < offset) {
                                null
                            } else {
                                settingsRows.getOrNull(index - offset) ?: return@mapIndexedNotNull null
                            }
                        CachedHomeRow.from(config, row)
                    }
                return CachedHome(cached, seasonal?.key)
            }

            /**
             * The rows to show right away for [settingsRows] (preceded by the [seasonal] row if any) from the [cached] home:
             * a cached row where the config matches, otherwise [pending]. Null if no row is cached.
             */
            fun restoreRows(
                cached: CachedHome?,
                settingsRows: List<HomeRowConfig>,
                seasonal: Holiday?,
                pending: (index: Int) -> HomeRowLoadingState,
            ): List<HomeRowLoadingState>? {
                if (cached == null) return null
                val byConfig = cached.rows.filter { it.config != null }.associateBy { it.config }
                val offset = if (seasonal != null) 1 else 0
                var found = false
                val rows =
                    List(settingsRows.size + offset) { index ->
                        val row =
                            if (index < offset) {
                                cached.rows.firstOrNull { it.config == null }?.takeIf { cached.seasonal == seasonal?.key }
                            } else {
                                byConfig[settingsRows[index - offset]]
                            }
                        if (row != null) found = true
                        row?.toState() ?: pending(index)
                    }
                return rows.takeIf { found }
            }

            /**
             * The row to show when a refresh of a row finished: a failed refresh keeps the [previous] loaded row instead
             * of replacing it with an error, and a ČSFD row that is not ready yet ([CsfdRowUnavailableException]) is
             * hidden when there is nothing to keep.
             */
            fun mergeRow(
                previous: HomeRowLoadingState?,
                new: HomeRowLoadingState,
            ): HomeRowLoadingState =
                when {
                    new !is HomeRowLoadingState.Error -> new
                    previous is HomeRowLoadingState.Success -> previous
                    new.exception is CsfdRowUnavailableException -> HomeRowLoadingState.Success(new.title, listOf())
                    else -> new
                }
        }
    }
