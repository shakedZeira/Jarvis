package com.jarvis.remote.data

import com.jarvis.remote.data.model.JarvisJson
import com.jarvis.remote.data.repo.ApiException
import com.jarvis.remote.data.repo.OpenCodeClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64

class OpenCodeClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OpenCodeClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val baseUrl = server.url("/").toString().trimEnd('/')
        client = OpenCodeClient(baseUrl, "opencode", "jarvis_test_pw_123", OkHttpClient.Builder().build())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueueJson(code: Int, body: String) {
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json; charset=utf-8").setBody(body))
    }

    @Test
    fun `health round trips`() = runBlocking {
        enqueueJson(200, SAMPLE_HEALTH)
        val health = client.health()
        assertTrue(health.healthy)
        assertEquals("1.18.32", health.version)
    }

    @Test
    fun `sessions round trip with basic auth header`() = runBlocking {
        enqueueJson(200, SAMPLE_SESSION)
        val sessions = client.sessions()
        assertEquals(1, sessions.size)
        val s = sessions.first()
        assertEquals("ses_abc", s.id)
        assertEquals("clever-falcon", s.slug)
        assertEquals("build", s.agent)
        assertEquals("big-pickle", s.model?.id)
        assertEquals("opencode", s.model?.providerID)
        assertEquals(1790240696781L, s.time?.updated)
        assertEquals(1790240544314L, s.time?.created)
        assertEquals("C:\\Proj", s.directory)

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertEquals("/session", recorded.path)
        val expected = "Basic " + Base64.getEncoder()
            .encodeToString("opencode:jarvis_test_pw_123".toByteArray(StandardCharsets.UTF_8))
        assertEquals(expected, recorded.getHeader("Authorization"))
    }

    @Test
    fun `sessions with directory filter url-encodes path and sends limit`() = runBlocking {
        enqueueJson(200, SAMPLE_SESSION)
        val sessions = client.sessions(directory = "D:\\AI Projects\\UltraDrive", limit = 50)
        assertEquals(1, sessions.size)

        val recorded = server.takeRequest()
        assertEquals(
            "/session?directory=D%3A%5CAI+Projects%5CUltraDrive&limit=50",
            recorded.path,
        )
    }

    @Test
    fun `projects round trip maps worktree and time`() = runBlocking {
        enqueueJson(200, SAMPLE_PROJECTS)
        val projects = client.projects()
        assertEquals(2, projects.size)
        val first = projects.first()
        assertEquals("proj_1", first.id)
        assertEquals("D:\\AI Projects\\Jarvis", first.worktree)
        assertEquals("git", first.vcs)
        assertEquals(1790240544314L, first.timeCreated)
        assertEquals(1790240696781L, first.timeUpdated)
        assertTrue(projects[1].worktree == null)
    }

    @Test
    fun `session status round trips as dynamic map`() = runBlocking {
        enqueueJson(200, SAMPLE_STATUS)
        val status = client.sessionStatus()
        assertEquals(2, status.size)
        assertEquals("idle", status["ses_abc"]?.raw?.get("status")?.jsonPrimitive?.content)
        assertNotNull(status["ses_xyz"])
    }

    @Test
    fun `messages round trip and map into models`() = runBlocking {
        enqueueJson(200, SAMPLE_MESSAGES)
        val messages = client.messages("ses_y", limit = 5)
        assertEquals(1, messages.size)
        val msg = messages.first()
        assertEquals("msg_x", msg.info.id)
        assertEquals("assistant", msg.info.role)
        assertEquals("ses_y", msg.info.sessionID)
        assertEquals("tool-calls", msg.info.finish)
        assertEquals(5, msg.parts.size)
        assertEquals("hello", msg.plainText())
        assertEquals("", msg.userText())
        val tool = msg.parts.first { it.isTool() }
        assertEquals("bash", tool.tool)
        assertEquals("bash: git status", tool.toolSummary())

        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.startsWith("/session/ses_y/message"))
    }

    @Test
    fun `sendPrompt posts text body`() = runBlocking {
        enqueueJson(200, "{}")
        client.sendPrompt("ses_abc", "deploy the build")
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.path!!.endsWith("/session/ses_abc/prompt_async"))
        val body = recorded.body.readUtf8()
        val parts = JarvisJson.parseToJsonElement(body).jsonObject["parts"]!!.jsonArray
        val text = parts[0].jsonObject["text"]!!.jsonPrimitive.content
        assertEquals("deploy the build", text)
    }

    @Test
    fun `abort posts to abort endpoint`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        client.abort("ses_abc")
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.path!!.endsWith("/session/ses_abc/abort"))
    }

    @Test
    fun `respondPermission posts boolean value`() = runBlocking {
        enqueueJson(200, "{}")
        client.respondPermission("ses_abc", "perm_1", allowed = true)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertTrue(recorded.path!!.contains("/session/ses_abc/permissions/perm_1"))
        val value = JarvisJson.parseToJsonElement(recorded.body.readUtf8()).jsonObject["value"]!!.jsonPrimitive.content
        assertEquals("true", value)
    }

    @Test
    fun `server error surfaces ApiException`() = runBlocking {
        enqueueJson(500, """{"error":"internal"}""")
        try {
            client.health()
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(500, e.code)
        }
    }

    @Test
    fun `client exposed fields are sane`() {
        assertEquals(server.url("/").toString().trimEnd('/'), client.baseUrl)
        assertEquals("opencode", client.username)
        assertEquals("jarvis_test_pw_123", client.password)
    }

    companion object {
        private val SAMPLE_HEALTH = """{"healthy":true,"version":"1.18.32"}"""

        private val SAMPLE_SESSION = """
            [
              {
                "id": "ses_abc",
                "slug": "clever-falcon",
                "projectID": "global",
                "directory": "C:\\Proj",
                "path": "Proj",
                "summary": {"additions": 0, "deletions": 0, "files": 0},
                "cost": 0,
                "tokens": {"input": 43598, "output": 5788, "reasoning": 124, "cache": {"read": 209291, "write": 0}},
                "title": "New session - 2026-09-24T09:02:24.314Z",
                "agent": "build",
                "model": {"id": "big-pickle", "providerID": "opencode"},
                "version": "1.18.32",
                "time": {"created": 1790240544314, "updated": 1790240696781}
              }
            ]
        """.trimIndent()

        private val SAMPLE_STATUS = """{"ses_abc":{"status":"idle","time":123},"ses_xyz":{}}"""

        private val SAMPLE_PROJECTS = """
            [
              {
                "id": "proj_1",
                "worktree": "D:\\AI Projects\\Jarvis",
                "vcs": "git",
                "time": {"created": 1790240544314, "updated": 1790240696781},
                "sandboxes": []
              },
              {
                "id": "proj_2",
                "worktree": null,
                "vcs": null
              }
            ]
        """.trimIndent()

        private val SAMPLE_MESSAGES = """
            [
              {
                "info": {
                  "parentID": "...",
                  "role": "assistant",
                  "agent": "general",
                  "cost": 0,
                  "tokens": {"total": 74002, "input": 119, "output": 667, "reasoning": 0, "cache": {"write": 0, "read": 73216}},
                  "modelID": "big-pickle",
                  "providerID": "opencode",
                  "time": {"created": 1790237239816, "completed": 1790237250602},
                  "finish": "tool-calls",
                  "id": "msg_x",
                  "sessionID": "ses_y"
                },
                "parts": [
                  {"type": "step-start", "id": "prt_1", "sessionID": "ses_y", "messageID": "msg_x"},
                  {"type": "reasoning", "text": "...", "time": {"start": 1, "end": 2}, "id": "prt_2", "sessionID": "ses_y", "messageID": "msg_x"},
                  {
                    "type": "tool",
                    "tool": "bash",
                    "callID": "call_1",
                    "state": {
                      "status": "completed",
                      "input": {"command": "git status"},
                      "output": "...",
                      "metadata": "...",
                      "exit": 0,
                      "truncated": false
                    },
                    "title": "git status",
                    "time": {"start": 1, "end": 2},
                    "id": "prt_3",
                    "sessionID": "ses_y",
                    "messageID": "msg_x"
                  },
                  {"type": "text", "text": "hello", "id": "prt_4", "sessionID": "ses_y", "messageID": "msg_x"},
                  {
                    "reason": "tool-calls",
                    "type": "step-finish",
                    "tokens": {},
                    "cost": 0,
                    "id": "prt_5",
                    "sessionID": "ses_y",
                    "messageID": "msg_x"
                  }
                ]
              }
            ]
        """.trimIndent()
    }
}