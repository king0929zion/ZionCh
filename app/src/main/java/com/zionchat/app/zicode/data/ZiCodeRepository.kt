package com.zionchat.app.zicode.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zionchat.app.R
import com.zionchat.app.data.SecureValueCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class ZiCodeRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dataStore = appContext.ziCodeDataStore
    private val gson = Gson()
    private val secureValueCipher = SecureValueCipher()
    private val prefsFlow = dataStore.data.catch { emit(emptyPreferences()) }

    private val settingsKey = stringPreferencesKey("settings_json")
    private val sessionsKey = stringPreferencesKey("sessions_json")
    private val recentReposKey = stringPreferencesKey("recent_repos_json")

    private val sessionListType = object : TypeToken<List<ZiCodeSession>>() {}.type
    private val recentRepoListType = object : TypeToken<List<ZiCodeRecentRepo>>() {}.type

    val settingsFlow: Flow<ZiCodeSettings> = prefsFlow.map { prefs ->
        parseSettings(readEncryptedString(prefs, settingsKey))
    }

    val sessionsFlow: Flow<List<ZiCodeSession>> = prefsFlow.map { prefs ->
        val raw = prefs[sessionsKey].orEmpty()
        runCatching { gson.fromJson<List<ZiCodeSession>>(raw.ifBlank { "[]" }, sessionListType) }
            .getOrNull()
            .orEmpty()
            .mapNotNull(::sanitizeSession)
            .sortedByDescending { it.updatedAt }
    }

    val recentReposFlow: Flow<List<ZiCodeRecentRepo>> = prefsFlow.map { prefs ->
        val raw = prefs[recentReposKey].orEmpty()
        runCatching { gson.fromJson<List<ZiCodeRecentRepo>>(raw.ifBlank { "[]" }, recentRepoListType) }
            .getOrNull()
            .orEmpty()
            .mapNotNull(::sanitizeRecentRepo)
            .sortedWith(
                compareByDescending<ZiCodeRecentRepo> { it.lastAccessedAt }
                    .thenByDescending { it.updatedAt }
                    .thenByDescending { it.pushedAt }
            )
    }

    fun sessionsForRepoFlow(owner: String, repo: String): Flow<List<ZiCodeSession>> {
        val repoKey = buildZiCodeRepoKey(owner, repo)
        return sessionsFlow.map { sessions ->
            sessions.filter { session ->
                buildZiCodeRepoKey(session.repoOwner, session.repoName) == repoKey
            }
        }
    }

    fun sessionFlow(sessionId: String): Flow<ZiCodeSession?> {
        val key = sessionId.trim()
        return sessionsFlow.map { sessions -> sessions.firstOrNull { it.id == key } }
    }

    suspend fun setGitHubToken(token: String) {
        updateSettings { settings ->
            settings.copy(
                githubToken = token.trim(),
                viewer = null,
                lastValidatedAt = null
            )
        }
    }

    suspend fun updateViewer(viewer: ZiCodeViewer?) {
        updateSettings { settings ->
            settings.copy(
                viewer = viewer,
                lastValidatedAt = System.currentTimeMillis()
            )
        }
    }

    suspend fun clearViewer() {
        updateSettings { settings ->
            settings.copy(
                viewer = null,
                lastValidatedAt = null
            )
        }
    }

    suspend fun syncRecentRepos(remoteRepos: List<ZiCodeRemoteRepo>) {
        val existingByKey = recentReposFlow.first().associateBy { buildZiCodeRepoKey(it.owner, it.name) }
        val merged =
            remoteRepos.map { repo ->
                val existing = existingByKey[buildZiCodeRepoKey(repo.owner, repo.name)]
                repo.toRecentRepo(lastAccessedAt = existing?.lastAccessedAt ?: 0L)
            }.sortedWith(
                compareByDescending<ZiCodeRecentRepo> { it.lastAccessedAt }
                    .thenByDescending { it.updatedAt }
                    .thenByDescending { it.pushedAt }
            )

        dataStore.edit { prefs ->
            prefs[recentReposKey] = gson.toJson(merged)
        }
    }

    suspend fun touchRecentRepo(repo: ZiCodeRemoteRepo) {
        val now = System.currentTimeMillis()
        val existing = recentReposFlow.first().toMutableList()
        val key = buildZiCodeRepoKey(repo.owner, repo.name)
        val updatedRepo = repo.toRecentRepo(lastAccessedAt = now)
        val filtered = existing.filterNot { buildZiCodeRepoKey(it.owner, it.name) == key }
        dataStore.edit { prefs ->
            prefs[recentReposKey] = gson.toJson(listOf(updatedRepo) + filtered)
        }
    }

    suspend fun createSession(owner: String, repo: String, title: String = defaultSessionTitle()): ZiCodeSession {
        val fallbackTitle = defaultSessionTitle()
        val session =
            ZiCodeSession(
                repoOwner = owner.trim(),
                repoName = repo.trim(),
                title = title.trim().ifBlank { fallbackTitle }
            )
        val sessions = sessionsFlow.first().toMutableList()
        sessions.add(0, session)
        dataStore.edit { prefs ->
            prefs[sessionsKey] = gson.toJson(sessions)
        }
        return session
    }

    suspend fun deleteSession(sessionId: String) {
        val key = sessionId.trim()
        if (key.isBlank()) return
        val sessions = sessionsFlow.first().filterNot { it.id == key }
        dataStore.edit { prefs ->
            prefs[sessionsKey] = gson.toJson(sessions)
        }
    }

    suspend fun renameSession(sessionId: String, title: String) {
        val key = sessionId.trim()
        val sanitizedTitle = title.trim().ifBlank { defaultSessionTitle() }
        updateSession(key) { session ->
            session.copy(
                title = sanitizedTitle.take(48),
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    suspend fun findLatestSessionForRepo(owner: String, repo: String): ZiCodeSession? {
        val repoKey = buildZiCodeRepoKey(owner, repo)
        return sessionsFlow.first().firstOrNull { session ->
            buildZiCodeRepoKey(session.repoOwner, session.repoName) == repoKey
        }
    }

    suspend fun appendTurn(sessionId: String, prompt: String): ZiCodeTurn? {
        val key = sessionId.trim()
        val cleanPrompt = prompt.trim()
        if (key.isBlank() || cleanPrompt.isBlank()) return null
        val turn = ZiCodeTurn(prompt = cleanPrompt)
        updateSession(key) { session ->
            val defaultTitle = defaultSessionTitle()
            val nextTitle =
                if (session.title == defaultTitle || session.title == "New conversation" || session.title == "新对话") {
                    buildSessionTitle(cleanPrompt)
                } else {
                    session.title
                }
            session.copy(
                title = nextTitle,
                turns = session.turns + turn,
                updatedAt = System.currentTimeMillis()
            )
        }
        return turn
    }

    suspend fun updateTurn(
        sessionId: String,
        turnId: String,
        transform: (ZiCodeTurn) -> ZiCodeTurn
    ) {
        val sessionKey = sessionId.trim()
        val turnKey = turnId.trim()
        if (sessionKey.isBlank() || turnKey.isBlank()) return
        updateSession(sessionKey) { session ->
            var touched = false
            val updatedTurns =
                session.turns.map { turn ->
                    if (turn.id == turnKey) {
                        touched = true
                        transform(turn).copy(updatedAt = System.currentTimeMillis())
                    } else {
                        turn
                    }
                }
            if (!touched) session
            else session.copy(turns = updatedTurns, updatedAt = System.currentTimeMillis())
        }
    }

    private suspend fun updateSettings(transform: (ZiCodeSettings) -> ZiCodeSettings) {
        val current = settingsFlow.first()
        val updated = sanitizeSettings(transform(current))
        dataStore.edit { prefs ->
            prefs[settingsKey] = encryptSettings(updated)
        }
    }

    private suspend fun updateSession(
        sessionId: String,
        transform: (ZiCodeSession) -> ZiCodeSession
    ) {
        if (sessionId.isBlank()) return
        val sessions = sessionsFlow.first().toMutableList()
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index < 0) return
        sessions[index] = sanitizeSession(transform(sessions[index])) ?: sessions[index]
        val sorted = sessions.sortedByDescending { it.updatedAt }
        dataStore.edit { prefs ->
            prefs[sessionsKey] = gson.toJson(sorted)
        }
    }

    private fun readEncryptedString(
        prefs: Preferences,
        key: Preferences.Key<String>
    ): String {
        val raw = prefs[key].orEmpty()
        if (raw.isBlank()) return ""
        return secureValueCipher.decryptOrNull(raw).orEmpty()
    }

    private fun encryptSettings(settings: ZiCodeSettings): String {
        return secureValueCipher.encrypt(gson.toJson(settings))
    }

    private fun parseSettings(raw: String): ZiCodeSettings {
        return sanitizeSettings(runCatching { gson.fromJson(raw.ifBlank { "{}" }, ZiCodeSettings::class.java) }.getOrNull())
    }

    private fun sanitizeSettings(settings: ZiCodeSettings?): ZiCodeSettings {
        if (settings == null) return ZiCodeSettings()
        return ZiCodeSettings(
            githubToken = settings.githubToken.trim(),
            viewer =
                settings.viewer?.let { viewer ->
                    val login = viewer.login.trim()
                    if (login.isBlank()) null
                    else {
                        ZiCodeViewer(
                            login = login,
                            displayName = viewer.displayName?.trim()?.takeIf { it.isNotBlank() },
                            avatarUrl = viewer.avatarUrl?.trim()?.takeIf { it.isNotBlank() }
                        )
                    }
                },
            lastValidatedAt = settings.lastValidatedAt?.takeIf { it > 0 }
        )
    }

    private fun sanitizeRecentRepo(repo: ZiCodeRecentRepo?): ZiCodeRecentRepo? {
        if (repo == null) return null
        val owner = repo.owner.trim()
        val name = repo.name.trim()
        if (owner.isBlank() || name.isBlank()) return null
        return repo.copy(
            owner = owner,
            name = name,
            description = repo.description.trim(),
            defaultBranch = repo.defaultBranch.trim().ifBlank { "main" },
            htmlUrl = repo.htmlUrl.trim(),
            homepageUrl = repo.homepageUrl?.trim()?.takeIf { it.isNotBlank() },
            pushedAt = repo.pushedAt.coerceAtLeast(0L),
            updatedAt = repo.updatedAt.coerceAtLeast(0L),
            lastAccessedAt = repo.lastAccessedAt.coerceAtLeast(0L)
        )
    }

    private fun sanitizeSession(session: ZiCodeSession?): ZiCodeSession? {
        if (session == null) return null
        val owner = session.repoOwner.trim()
        val repo = session.repoName.trim()
        if (owner.isBlank() || repo.isBlank()) return null
        val turns = session.turns.mapNotNull(::sanitizeTurn)
        val createdAt = session.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()
        val updatedAt =
            maxOf(
                session.updatedAt.takeIf { it > 0 } ?: createdAt,
                turns.maxOfOrNull { it.updatedAt } ?: createdAt
            )
        return session.copy(
            repoOwner = owner,
            repoName = repo,
            title = session.title.trim().ifBlank { defaultSessionTitle() }.take(48),
            turns = turns,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun sanitizeTurn(turn: ZiCodeTurn?): ZiCodeTurn? {
        if (turn == null) return null
        val prompt = turn.prompt.trim()
        if (prompt.isBlank()) return null
        val createdAt = turn.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis()
        val toolCalls = turn.toolCalls.mapNotNull(::sanitizeToolCall)
        return turn.copy(
            prompt = prompt,
            response = turn.response.trim(),
            toolCalls = toolCalls,
            createdAt = createdAt,
            updatedAt = maxOf(turn.updatedAt.takeIf { it > 0 } ?: createdAt, createdAt),
            resultLink = turn.resultLink?.trim()?.takeIf { it.isNotBlank() },
            resultLabel = turn.resultLabel?.trim()?.takeIf { it.isNotBlank() },
            failureMessage = turn.failureMessage?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    private fun sanitizeToolCall(call: ZiCodeToolCallState?): ZiCodeToolCallState? {
        if (call == null) return null
        val label = call.label.trim()
        val toolName = call.toolName.trim()
        if (label.isBlank() || toolName.isBlank()) return null
        return call.copy(
            label = label,
            toolName = toolName,
            group = call.group.trim(),
            summary = call.summary.trim(),
            inputSummary = call.inputSummary.trim(),
            detailLog = call.detailLog.trim(),
            resultSummary = call.resultSummary.trim(),
            startedAt = call.startedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
            finishedAt = call.finishedAt?.takeIf { it > 0 }
        )
    }

    private fun buildSessionTitle(prompt: String): String {
        val compact = prompt.trim().replace(Regex("\\s+"), " ")
        return compact.take(26).ifBlank { defaultSessionTitle() }
    }

    private fun defaultSessionTitle(): String {
        return runCatching { appContext.getString(R.string.zicode_new_conversation) }
            .getOrElse { "New conversation" }
    }
}
