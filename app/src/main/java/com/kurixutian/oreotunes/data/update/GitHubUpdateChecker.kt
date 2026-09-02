package com.kurixutian.oreotunes.data.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val releaseUrl: String,
    val releaseNotes: String
)

object GitHubUpdateChecker {

    private const val OWNER = "Kurixutian"
    private const val REPOSITORY = "OreoTunes"

    private const val API_URL =
        "https://api.github.com/repos/$OWNER/$REPOSITORY/releases/latest"

    private const val PREFS_NAME = "oreotunes_update_preferences"
    private const val KEY_REMINDER_VERSION = "reminder_version"
    private const val KEY_REMINDER_TIME = "reminder_time"

    private const val REMINDER_DURATION_MS =
        24L * 60L * 60L * 1000L

    suspend fun checkForUpdate(
        context: Context,
        ignoreReminder: Boolean = false
    ): UpdateInfo? = withContext(Dispatchers.IO) {

        try {
            val currentVersion = getCurrentVersionName(context)

            val connection =
                (URL(API_URL).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"

                    connectTimeout = 10_000
                    readTimeout = 10_000

                    setRequestProperty(
                        "Accept",
                        "application/vnd.github+json"
                    )

                    setRequestProperty(
                        "X-GitHub-Api-Version",
                        "2022-11-28"
                    )

                    setRequestProperty(
                        "User-Agent",
                        "OreoTunes-Android"
                    )
                }

            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    return@withContext null
                }

                val response = connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

                val json = JSONObject(response)

                val tagName = json
                    .optString("tag_name")
                    .removePrefix("v")
                    .trim()

                if (tagName.isBlank()) {
                    return@withContext null
                }

                val releaseUrl = json
                    .optString("html_url")
                    .trim()

                if (releaseUrl.isBlank()) {
                    return@withContext null
                }

                val releaseNotes = json
                    .optString("body")
                    .trim()

                /*
                 * Only report a release when it is newer than
                 * the version installed on the device.
                 */
                if (compareVersions(tagName, currentVersion) <= 0) {
                    return@withContext null
                }

                val updateInfo = UpdateInfo(
                    versionName = tagName,
                    releaseUrl = releaseUrl,
                    releaseNotes = releaseNotes
                )

                /*
                 * Automatic checks respect "Remind me later".
                 *
                 * Manual checks from Settings pass ignoreReminder = true,
                 * so the user can always force another check.
                 */
                if (!ignoreReminder &&
                    !shouldShowUpdate(context, updateInfo)
                ) {
                    return@withContext null
                }

                updateInfo

            } finally {
                connection.disconnect()
            }

        } catch (_: Exception) {
            null
        }
    }

    fun markReminded(
        context: Context,
        versionName: String
    ) {
        context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY_REMINDER_VERSION,
                versionName
            )
            .putLong(
                KEY_REMINDER_TIME,
                System.currentTimeMillis()
            )
            .apply()
    }

    private fun shouldShowUpdate(
        context: Context,
        updateInfo: UpdateInfo
    ): Boolean {

        val prefs = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        val remindedVersion =
            prefs.getString(
                KEY_REMINDER_VERSION,
                null
            )

        val reminderTime =
            prefs.getLong(
                KEY_REMINDER_TIME,
                0L
            )

        /*
         * A different release version always gets shown.
         */
        if (remindedVersion != updateInfo.versionName) {
            return true
        }

        /*
         * Same release:
         * show it again after 24 hours.
         */
        return System.currentTimeMillis() - reminderTime >=
            REMINDER_DURATION_MS
    }

    private fun getCurrentVersionName(
        context: Context
    ): String {

        val packageInfo =
            context.packageManager.getPackageInfo(
                context.packageName,
                0
            )

        return packageInfo.versionName ?: "0.0.0"
    }

    private fun compareVersions(
        latest: String,
        current: String
    ): Int {

        val latestParts = normalizeVersion(latest)
        val currentParts = normalizeVersion(current)

        val maxSize = maxOf(
            latestParts.size,
            currentParts.size
        )

        for (index in 0 until maxSize) {

            val latestPart =
                latestParts.getOrElse(index) { 0 }

            val currentPart =
                currentParts.getOrElse(index) { 0 }

            if (latestPart != currentPart) {
                return latestPart.compareTo(currentPart)
            }
        }

        return 0
    }

    private fun normalizeVersion(
        version: String
    ): List<Int> {

        return version
            .removePrefix("v")
            .substringBefore("-")
            .substringBefore("+")
            .split(".")
            .map { part ->
                part
                    .filter { it.isDigit() }
                    .toIntOrNull() ?: 0
            }
    }
}
