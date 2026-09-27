package com.jarvis.remote.ui.session

import com.jarvis.remote.data.model.PermissionRequest
import com.jarvis.remote.data.model.QuestionInfo
import com.jarvis.remote.data.model.QuestionOption
import com.jarvis.remote.data.model.QuestionRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingPromptsTest {

    private fun permission(id: String, sessionID: String, name: String = "bash") =
        PermissionRequest(id = id, sessionID = sessionID, permission = name)

    private fun question(
        id: String,
        sessionID: String,
        questions: List<QuestionInfo> = emptyList(),
    ) = QuestionRequest(id = id, sessionID = sessionID, questions = questions)

    private fun info(
        question: String,
        header: String = "",
        options: List<QuestionOption> = emptyList(),
        multiple: Boolean = false,
        custom: Boolean = false,
    ) = QuestionInfo(
        question = question,
        header = header,
        options = options,
        multiple = multiple,
        custom = custom,
    )

    @Test
    fun `permissions keep only the requested session`() {
        val all = listOf(
            permission("p1", "ses_a"),
            permission("p2", "ses_b"),
            permission("p3", "ses_a"),
        )

        val mine = PendingPrompts.permissions(all, "ses_a")

        assertEquals(listOf("p1", "p3"), mine.map { it.id })
    }

    @Test
    fun `permissions of other sessions produce an empty list`() {
        assertTrue(PendingPrompts.permissions(listOf(permission("p1", "ses_b")), "ses_a").isEmpty())
    }

    @Test
    fun `questions keep only the requested session`() {
        val all = listOf(
            question("q1", "ses_b"),
            question("q2", "ses_a"),
        )

        assertEquals(listOf("q2"), PendingPrompts.questions(all, "ses_a").map { it.id })
        assertTrue(PendingPrompts.questions(all, "ses_c").isEmpty())
    }

    @Test
    fun `heading falls back to the question when the header is blank`() {
        assertEquals("Pick a port", PendingPrompts.heading(info("Pick a port", header = "  ")))
        assertEquals("Deploy", PendingPrompts.heading(info("Pick a port", header = "Deploy")))
    }

    @Test
    fun `draft starts empty with one entry per question`() {
        val request = question(
            "q1",
            "ses_a",
            listOf(info("first"), info("second", custom = true)),
        )

        val draft = PendingPrompts.draftFor(request)

        assertEquals(2, draft.size)
        assertTrue(draft.all { it.selected.isEmpty() && it.custom.isEmpty() })
    }

    @Test
    fun `single select replaces the previous answer`() {
        val request = question(
            "q1",
            "ses_a",
            listOf(
                info(
                    "Pick one",
                    options = listOf(QuestionOption("Yes"), QuestionOption("No")),
                )
            ),
        )

        val first = PendingPrompts.toggle(request, PendingPrompts.draftFor(request), 0, "Yes")
        val second = PendingPrompts.toggle(request, first, 0, "No")

        assertEquals(listOf(listOf("No")), PendingPrompts.answers(request, second))
    }

    @Test
    fun `multi select keeps option order and toggles off`() {
        val request = question(
            "q1",
            "ses_a",
            listOf(
                info(
                    "Pick many",
                    options = listOf(QuestionOption("A"), QuestionOption("B"), QuestionOption("C")),
                    multiple = true,
                )
            ),
        )

        val draft = PendingPrompts.toggle(request, PendingPrompts.draftFor(request), 0, "C")
        val both = PendingPrompts.toggle(request, draft, 0, "A")
        val offA = PendingPrompts.toggle(request, both, 0, "A")

        assertEquals(listOf(listOf("A", "C")), PendingPrompts.answers(request, both))
        assertEquals(listOf(listOf("C")), PendingPrompts.answers(request, offA))
    }

    @Test
    fun `answers payload combines selected labels and custom text for every question`() {
        val request = question(
            "q1",
            "ses_a",
            listOf(
                info(
                    "Which targets?",
                    options = listOf(QuestionOption("api"), QuestionOption("web")),
                    multiple = true,
                    custom = true,
                ),
                info("Anything else?", custom = true),
            ),
        )
        val draft = PendingPrompts.withCustom(
            PendingPrompts.toggle(request, PendingPrompts.draftFor(request), 0, "web"),
            index = 0,
            text = " and docs ",
        ).let { PendingPrompts.withCustom(it, index = 1, text = "ship it") }

        assertEquals(listOf(listOf("web", "and docs"), listOf("ship it")), PendingPrompts.answers(request, draft))
        assertEquals(" and docs ", draft[0].custom)
    }

    @Test
    fun `unanswered questions contribute an empty list`() {
        val request = question(
            "q1",
            "ses_a",
            listOf(
                info("one", options = listOf(QuestionOption("a"))),
                info("two", options = listOf(QuestionOption("b"))),
            ),
        )
        val draft = PendingPrompts.toggle(request, PendingPrompts.draftFor(request), 0, "a")

        val answers = PendingPrompts.answers(request, draft)

        assertEquals(listOf(listOf("a"), emptyList<String>()), answers)
        assertTrue(PendingPrompts.canSubmit(request, draft))
    }

    @Test
    fun `blank custom text is dropped and whitespace only answers count as empty`() {
        val request = question(
            "q1",
            "ses_a",
            listOf(info("free form", custom = true), info("blank", custom = true)),
        )
        val draft = PendingPrompts.withCustom(
            PendingPrompts.draftFor(request),
            index = 0,
            text = "  spaced  ",
        ).let { PendingPrompts.withCustom(it, index = 1, text = "   ") }

        val answers = PendingPrompts.answers(request, draft)

        assertEquals(listOf(listOf("spaced"), emptyList<String>()), answers)
        assertTrue(PendingPrompts.canSubmit(request, draft))
    }

    @Test
    fun `nothing answered means the reply is not answerable yet`() {
        val request = question(
            "q1",
            "ses_a",
            listOf(info("pick", options = listOf(QuestionOption("a")))),
        )

        assertFalse(PendingPrompts.canSubmit(request, PendingPrompts.draftFor(request)))
    }

    @Test
    fun `a question with nothing to pick stays submittable`() {
        val request = question("q1", "ses_a", listOf(info("continue?")))

        assertTrue(PendingPrompts.canSubmit(request, PendingPrompts.draftFor(request)))
        assertEquals(listOf(emptyList<String>()), PendingPrompts.answers(request, PendingPrompts.draftFor(request)))
    }

    @Test
    fun `answers always has one entry per question even with a short draft`() {
        val request = question(
            "q1",
            "ses_a",
            listOf(info("one"), info("two"), info("three")),
        )

        assertEquals(listOf(emptyList<String>(), emptyList<String>(), emptyList<String>()), PendingPrompts.answers(request, emptyList()))
    }

    @Test
    fun `toggling or editing an unknown question index leaves the draft untouched`() {
        val request = question("q1", "ses_a", listOf(info("one")))
        val draft = PendingPrompts.draftFor(request)

        assertEquals(draft, PendingPrompts.toggle(request, draft, 5, "a"))
        assertEquals(draft, PendingPrompts.withCustom(draft, 5, "text"))
    }
}
