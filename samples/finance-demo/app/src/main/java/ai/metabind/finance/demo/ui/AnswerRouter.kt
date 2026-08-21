/*
 * AnswerRouter.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.finance.demo.ui

import ai.metabind.ai.ChatMessage
import ai.metabind.ai.MessageRole
import ai.metabind.ai.MetabindAssistant
import ai.metabind.ai.ToolUIContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

/**
 * Turns a linear assistant conversation into a two-surface app.
 *
 * The app never shows a transcript. Every turn lands in one of two places: the
 * *home* answer, which is the screen you launch into, and a *thread* — a chain of
 * answers presented in a sheet over the top.
 *
 * The thread is the app's answer to context. Chat shows you everything the model
 * knows by piling it up forever; here the pile is scoped to one sheet and thrown
 * away when you close it, so what's in context is exactly what you can see.
 * Closing the sheet calls [endThread], which clears the model's memory
 * server-side too — the chain really is gone, not just hidden.
 *
 * State is Compose snapshot state and every mutation runs on [scope]'s dispatcher
 * (the ViewModel's main scope), so the screens can read it directly.
 */
class AnswerRouter(
    val assistant: MetabindAssistant,
    private val scope: CoroutineScope,
) {

    /**
     * One rendered card: a tool call that declared UI, paired with the resolved
     * content for it.
     *
     * Snapshotted rather than looked up live. The assistant keys tool UI by
     * tool-call id and drops the whole map on [MetabindAssistant.reset] — which is
     * exactly what ending a thread does — so an answer that read its cards out of
     * the map on every recomposition would be hollowed out the moment the sheet
     * closed, home card included. See [refreshLive] for how a still-streaming
     * answer stays current without that.
     */
    data class Card(
        /** Tool-call id; also the id of the `TOOL` [ChatMessage] the call produced. */
        val id: String,
        val toolName: String,
        val content: ToolUIContent,
    )

    /** One turn: the question asked, and what came back. */
    data class Answer(
        val id: String = UUID.randomUUID().toString(),
        val question: String,
        /**
         * Where this turn begins in [MetabindAssistant.messages]. Only valid until
         * the next [endThread], which is why [prose] and [cards] are snapshotted
         * rather than read from the conversation on demand.
         */
        val startIndex: Int,
        /**
         * Every card this turn rendered, in the order the model asked for them.
         * Usually one. A model may emit several tool calls in a single message,
         * which costs one inference pass rather than one per card — so a turn that
         * wants to show two things is far cheaper than two turns, and the app tabs
         * them rather than dropping all but the first.
         */
        val cards: List<Card> = emptyList(),
        /**
         * The assistant's prose for this turn. Updated while the turn streams, then
         * left alone — so a thread's text survives the reset that ends it.
         */
        val prose: String = "",
        /**
         * Follow-ups the model attached to this turn's card, in the order it
         * suggested them.
         *
         * Held per answer rather than on the assistant: the chips let you jump back
         * to an earlier answer, and the rail should offer that answer's suggestions,
         * not the newest turn's. It also means the home answer keeps its follow-ups
         * across the reset that ends a thread.
         */
        val nextSteps: List<String> = emptyList(),
    ) {
        /** The first card, which is the one presentation waits on. */
        val card: Card? get() = cards.firstOrNull()
    }

    /** The answer pinned to the home screen. */
    var home by mutableStateOf<Answer?>(null)
        private set

    /**
     * The chain currently in the sheet, oldest first. Surfaced as a row of chips
     * once there's more than one, so the whole thread stays reachable from a
     * single sheet.
     */
    var thread by mutableStateOf<List<Answer>>(emptyList())
        private set

    /** Which answer in the thread the sheet is showing. */
    var selected by mutableIntStateOf(0)
        private set

    /** The question in flight, if any — what the processing bubble shows. */
    var pending by mutableStateOf<Answer?>(null)
        private set

    var isAnswerPresented by mutableStateOf(false)
        private set

    /**
     * True while the in-flight question is a home (re)load, which lands on the home
     * screen instead of the thread.
     */
    private var pendingIsHome = false

    /**
     * Set when a question was asked while the assistant was still busy, so it can
     * go out as soon as the current turn finishes.
     */
    private var queued: String? = null

    /**
     * Index of the last user message already accounted for, so a turn is never
     * adopted twice. See [adoptUntrackedTurn].
     */
    private var handledUpTo = -1

    /** The delivered answer whose prose and cards may still be streaming in. */
    private var liveAnswerId: String? = null

    /**
     * Held across the call into [MetabindAssistant.send].
     *
     * The assistant appends the user message and *then* raises its loading flag, and
     * this router collects on `Dispatchers.Main.immediate` — so appending the message
     * re-enters [sync] synchronously, in the one window where the conversation has a
     * question in it and the assistant still reads as idle. Without this the very
     * first sync would conclude the turn produced nothing and deliver an empty
     * answer, and the real one would have to overwrite it a second later.
     */
    private var isSending = false

    private val isProcessing: Boolean get() = isSending || assistant.isLoading.value

    val isBusy: Boolean get() = pending != null || isProcessing

    /** The suggestions the rail should offer beside whichever surface is on top. */
    val homeNextSteps: List<String> get() = home?.nextSteps ?: emptyList()

    /**
     * One collector for all three flows: routing decisions read messages, loading
     * state and the tool-UI map together, and any of the three changing can be the
     * thing that makes a turn deliverable.
     */
    private val syncJob: Job = scope.launch {
        combine(
            assistant.messages,
            assistant.isLoading,
            assistant.toolUIContent,
        ) { _, _, _ -> Unit }.collect { sync() }
    }

    /** Stop watching the conversation. Call when discarding the router. */
    fun close() {
        syncJob.cancel()
    }

    // MARK: - Asking

    /** Load the home answer. Safe to call repeatedly; only the first lands. */
    fun start() {
        if (home != null || pending != null) return
        reloadHome()
    }

    fun reloadHome() {
        pendingIsHome = true
        send(HOME_QUESTION)
    }

    /**
     * Ask a question. From the home rail this starts a thread; from inside the
     * sheet it extends the one that's open.
     */
    fun ask(question: String) {
        // A question queued behind the home load must not change where the in-flight
        // home answer lands. `flushQueue` calls back here once the current turn
        // finishes, at which point it becomes a thread answer.
        if (!isBusy) pendingIsHome = false
        // The chips let you jump back to an earlier answer, but the model's history
        // is linear — left alone it would read "that" as the most recent turn, not
        // the one on screen. Naming the focused answer keeps the tabs meaning what
        // they look like they mean.
        val current = thread.getOrNull(selected)
        if (current != null && selected != thread.size - 1) {
            assistant.mergePendingContext(mapOf("focusedAnswer" to current.question))
        }
        send(question)
    }

    fun select(index: Int) {
        if (index in thread.indices) selected = index
    }

    /** Re-run the question behind whichever surface the user is looking at. */
    fun retry(answer: Answer) {
        if (answer.id == home?.id) reloadHome() else ask(answer.question)
    }

    fun cancelPending() {
        assistant.cancel()
        assistant.clearPendingContext()
        // Mark it handled on the way out, or the next `sync` would see an
        // unaccounted-for user message and adopt the turn straight back.
        pending?.let { handledUpTo = it.startIndex }
        pending = null
        pendingIsHome = false
        queued = null
    }

    private fun send(question: String) {
        val text = question.trim()
        if (text.isEmpty()) return
        if (isBusy) {
            // Hold it rather than dropping it — `sync` flushes the queue when the
            // current turn ends.
            queued = text
            return
        }
        // Captured before `send` so the index points at this turn's user message,
        // which is what `prose` slices from.
        pending = Answer(question = text, startIndex = assistant.messages.value.size)
        isSending = true
        try {
            assistant.send(text)
        } finally {
            isSending = false
        }
    }

    // MARK: - Ending a thread

    /**
     * Called once the sheet has finished dismissing. Ending the chain means ending
     * it for the model too — otherwise the next question would silently inherit
     * context the user can no longer see, which is the exact failure a visible
     * thread is meant to avoid.
     */
    fun endThread() {
        if (thread.isEmpty()) return
        // A question still in flight has nowhere to land now, and letting it finish
        // would re-present the sheet over a thread that's gone.
        cancelPending()
        thread = emptyList()
        selected = 0
        liveAnswerId = null
        handledUpTo = -1
        assistant.reset()
        // Unlike iOS there is no host bridge to re-attach: each card owns its own JS
        // runtime and registers its host on mount, and that host closes over the
        // assistant, which survives the reset.
    }

    fun dismissAnswer() {
        isAnswerPresented = false
    }

    // MARK: - Routing

    /**
     * Called whenever the conversation changes. Attaches the in-flight answer to
     * its rendered cards, keeps streaming prose up to date, and delivers turns
     * that produce no card at all.
     */
    private fun sync() {
        adoptUntrackedTurn()
        refreshLive()

        val current = pending
        if (current == null) {
            flushQueue()
            return
        }

        // Text first. The model speaks once it has read the figures, which is a whole
        // tool call before it renders the card — so the sentence is sitting in the
        // conversation while the app is still waiting on the card to be worth
        // showing. Deliver the moment there is something to read and let the card
        // land underneath it: `refreshLive` keeps both the prose and the card list
        // current for the live answer afterwards.
        if (current.prose.isNotEmpty()) {
            deliver(current.id)
            return
        }

        // A card enters `toolUIContent` only once its `ui://` resource has been read
        // and parsed, and the arguments arrive whole with the tool call rather than
        // streaming in behind it. So the first entry for this turn is already worth
        // looking at — no equivalent of iOS's wait-for-first-real-prop is needed.
        if (renderedCards(current.startIndex).isNotEmpty()) {
            deliver(current.id)
            return
        }

        // No card this turn: the prose is the whole answer, so it's ready as soon as
        // the assistant stops. An error turn lands here too, with nothing in it, and
        // gets the retry card.
        if (!isProcessing) deliver(current.id)
    }

    /**
     * Picks up a turn that started without going through [ask].
     *
     * A rendered component can talk back to the model — tapping a row in a
     * transaction list calls `host.sendMessage`, which lands straight on the
     * assistant. Without this, that turn would run with no bubble and its answer
     * would never surface. Adopting it means a component-driven question extends
     * the thread exactly like a tapped pill.
     */
    private fun adoptUntrackedTurn() {
        if (pending != null) return
        val messages = assistant.messages.value
        val index = messages.indexOfLast { it.role == MessageRole.USER }
        if (index <= handledUpTo) return

        pendingIsHome = false
        pending = Answer(question = messages[index].content, startIndex = index)
    }

    private fun deliver(id: String) {
        val current = pending ?: return
        if (current.id != id) return
        pending = null
        handledUpTo = current.startIndex

        val cards = renderedCards(current.startIndex)
        val answer = current.copy(
            prose = prose(current.startIndex),
            cards = cards,
            nextSteps = nextSteps(cards),
        )
        liveAnswerId = answer.id

        if (pendingIsHome) {
            pendingIsHome = false
            home = answer
        } else {
            thread = thread + answer
            selected = thread.size - 1
            isAnswerPresented = true
        }
        flushQueue()
    }

    private fun flushQueue() {
        val next = queued ?: return
        if (isBusy) return
        queued = null
        ask(next)
    }

    // MARK: - Reading the conversation

    /**
     * Keeps the in-flight answer and the one that just landed in step with the
     * stream. Everything older is already snapshotted and never read from the
     * conversation again, so an [endThread] can't hollow it out.
     */
    private fun refreshLive() {
        pending?.let { pending = it.copy(prose = prose(it.startIndex)) }

        val live = liveAnswerId ?: return
        home?.let { if (it.id == live) home = it.refreshed() }
        val index = thread.indexOfFirst { it.id == live }
        if (index >= 0) {
            thread = thread.toMutableList().also { it[index] = it[index].refreshed() }
        }
        if (!isProcessing) liveAnswerId = null
    }

    /**
     * Re-reads a delivered answer out of the conversation. Falls back to what it
     * already holds rather than to nothing: a card can land after the prose was
     * delivered (same message, arguments streaming in behind), but a conversation
     * that has just been reset must not blank an answer that is still on screen.
     */
    private fun Answer.refreshed(): Answer {
        val cards = renderedCards(startIndex)
        return copy(
            prose = prose(startIndex).ifEmpty { prose },
            cards = cards.ifEmpty { this.cards },
            nextSteps = nextSteps(cards).ifEmpty { nextSteps },
        )
    }

    /**
     * The first thing the assistant said between this turn's question and the next
     * one — not everything it said.
     *
     * A turn that reads the figures before rendering gives the model two openings
     * to talk: once when the numbers come back, once after the card is drawn. It
     * reliably takes both, and the second is the first restated with the figures
     * put back in — so joining them showed the same observation twice, run
     * together. Three attempts at instructing it otherwise didn't hold; this is
     * deterministic.
     *
     * First rather than last on the evidence: the remark it makes on seeing the
     * numbers is the analysis, and the one after the card is the summary of what is
     * already on screen.
     */
    private fun prose(startIndex: Int): String {
        val messages = assistant.messages.value
        if (startIndex >= messages.size) return ""
        var end = messages.size
        for (i in startIndex + 1 until messages.size) {
            if (messages[i].role == MessageRole.USER) {
                end = i
                break
            }
        }
        return messages.subList(startIndex, end)
            .firstOrNull { it.role == MessageRole.ASSISTANT && it.content.isNotBlank() }
            ?.content
            ?.trim()
            .orEmpty()
    }

    /**
     * The tool calls in this turn that render UI. Data-only tools feed the model
     * but have nothing to show, and never get an entry in `toolUIContent`.
     */
    private fun renderedCards(startIndex: Int): List<Card> {
        val messages = assistant.messages.value
        if (startIndex >= messages.size) return emptyList()
        val ui = assistant.toolUIContent.value

        val found = mutableListOf<Card>()
        for (i in startIndex until messages.size) {
            val message = messages[i]
            // `startIndex` is this turn's own user message; a later one is the next
            // turn, and its cards are not ours.
            if (i > startIndex && message.role == MessageRole.USER) break
            if (message.role != MessageRole.TOOL) continue
            val content = ui[message.id] ?: continue
            found += Card(id = message.id, toolName = message.toolName.orEmpty(), content = content)
        }

        // One card per tool. A model that renders the same card twice in a turn is
        // correcting itself, not offering two views: it built a `trend_card` from a
        // `limit: 0` fetch, got no rows to plot, refetched and rendered again. Both
        // calls are real, so both became tabs — two named "Trend", the first empty.
        // Genuine multi-card turns use *different* tools, so keeping the last of each
        // name drops the abandoned attempt and keeps the pairing.
        val lastByTool = found.associateBy { it.toolName }
        return found.filter { lastByTool[it.toolName]?.id == it.id }
    }

    /**
     * Reads [NEXT_STEPS_ARGUMENT] out of the turn's cards.
     *
     * The suggestions ride along as an argument on a tool call the model is already
     * making, so they cost no extra round trip. The tool itself ignores the
     * argument; it exists so the model has somewhere to put them. It must still be
     * declared in the tool's published input schema — a server validating
     * `additionalProperties: false` rejects a call carrying an argument it never
     * advertised.
     *
     * The last card to supply them wins, so a turn that renders two things offers
     * the follow-ups belonging to the newer one.
     */
    private fun nextSteps(cards: List<Card>): List<String> =
        cards.asReversed().firstNotNullOfOrNull { card ->
            val args = card.content.toolArguments as? JsonObject ?: return@firstNotNullOfOrNull null
            val raw = args[NEXT_STEPS_ARGUMENT] as? JsonArray ?: return@firstNotNullOfOrNull null
            raw.mapNotNull { element ->
                (element as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
            }.filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() }
        }.orEmpty()

    companion object {
        /**
         * The question the home screen answers. Sent once, on launch — the home
         * screen *is* its result, so changing this changes the whole app.
         */
        const val HOME_QUESTION = "Where did my money go this month?"

        /** Tool argument the model puts its follow-up suggestions in. */
        private const val NEXT_STEPS_ARGUMENT = "nextSteps"
    }
}
