package com.jarvis.remote.ui.home

import com.jarvis.remote.data.model.Session
import com.jarvis.remote.data.model.SessionStatus
import com.jarvis.remote.data.model.SessionTime
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRowMapperTest {

    private val now = 1_000_000L

    private fun session(
        id: String,
        title: String? = null,
        directory: String? = null,
        agent: String? = null,
        created: Long? = null,
        updated: Long? = null,
    ) = Session(
        id = id,
        title = title,
        directory = directory,
        agent = agent,
        time = SessionTime(created = created, updated = updated)
    )

    private fun statuses(ids: Set<String>): Map<String, SessionStatus> =
        ids.associateWith { SessionStatus(buildJsonObject {}) }

    private fun rows(
        sessions: List<Session>,
        status: Set<String> = emptySet(),
        busy: Set<String> = emptySet(),
        errors: Map<String, String> = emptyMap(),
    ): List<SessionRow> =
        HomeRowMapper.toRows(sessions, statuses(status), busy, now, errors)

    @Test
    fun `sorts by last activity descending falling back to updated then created then 0`() {
        val sessions = listOf(
            session("old", updated = now - 600_000L),
            session("mid", updated = now - 60_000L),
            session("fresh", updated = now - 30_000L),
            session("created", created = now - 120_000L),
            session("none"),
        )

        val ids = rows(sessions).map { it.id }

        assertEquals(listOf("fresh", "mid", "created", "old", "none"), ids)
    }

    @Test
    fun `classifies running when status map contains the session`() {
        val result = rows(listOf(session("a")), status = setOf("a"))

        assertEquals(RowStatus.RUNNING, result.single().status)
        assertTrue(result.single().busy)
    }

    @Test
    fun `classifies running when an sse busy event is pending`() {
        val result = rows(listOf(session("a")), busy = setOf("a"))

        assertEquals(RowStatus.RUNNING, result.single().status)
        assertTrue(result.single().busy)
    }

    @Test
    fun `classifies idle by default`() {
        val result = rows(listOf(session("a")))

        assertEquals(RowStatus.IDLE, result.single().status)
        assertFalse(result.single().busy)
    }

    @Test
    fun `classifies error from error map`() {
        val result = rows(listOf(session("a")), errors = mapOf("a" to "boom"))

        assertEquals(RowStatus.ERROR, result.single().status)
    }

    @Test
    fun `decays stale running session with no updated time to idle`() {
        val result = rows(
            listOf(session("a", created = now - 10 * 60 * 1000L)),
            status = setOf("a"),
        )

        assertEquals(RowStatus.IDLE, result.single().status)
        assertFalse(result.single().busy)
    }

    @Test
    fun `keeps running when stale session has an updated time`() {
        val result = rows(
            listOf(session("a", updated = now - 10 * 60 * 1000L)),
            status = setOf("a"),
        )

        assertEquals(RowStatus.RUNNING, result.single().status)
    }

    @Test
    fun `keeps running when stale session has a pending busy event`() {
        val result = rows(
            listOf(session("a", created = now - 10 * 60 * 1000L)),
            busy = setOf("a"),
        )

        assertEquals(RowStatus.RUNNING, result.single().status)
    }

    @Test
    fun `falls back to id for blank title`() {
        val result = rows(listOf(session("ses_1", title = "  ")))

        assertEquals("ses_1", result.single().title)
    }

    @Test
    fun `carries directory and agent`() {
        val result = rows(listOf(session("a", directory = "C:\\work", agent = "build")))

        assertEquals("C:\\work", result.single().directory)
        assertEquals("build", result.single().agent)
    }

    @Test
    fun `formatRelative yields human labels`() {
        assertEquals("just now", HomeRowMapper.formatRelative(now, now))
        assertEquals("just now", HomeRowMapper.formatRelative(now - 59_000L, now))
        assertEquals("5m ago", HomeRowMapper.formatRelative(now - 5 * 60_000L, now))
        assertEquals("2h ago", HomeRowMapper.formatRelative(now - 2 * 3_600_000L, now))
        assertEquals("—", HomeRowMapper.formatRelative(null, now))
    }
}