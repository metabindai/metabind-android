/*
 * Suggestions.kt.
 *
 * © 2026 Yap Studios LLC
 */
package ai.metabind.finance.demo.ui

import ai.metabind.finance.demo.R
import androidx.annotation.DrawableRes

/** One pill in the ask rail. */
data class Suggestion(
    @param:DrawableRes val icon: Int,
    /** What fits on a pill. */
    val short: String,
    /** What the assistant is actually asked. */
    val text: String,
) {
    val id: String get() = text

    companion object {
        /** Identity of the "Ask anything" pill, which the composer grows out of. */
        const val ASK_ID = "ask-anything"

        /**
         * Icons the model may put on a follow-up, keyed by the name it writes.
         *
         * A closed vocabulary rather than "any icon": an unmapped name would leave a
         * pill with a hole in it. The server instructions list SF Symbol names —
         * the same ten the iOS client carries — so the names stay identical across
         * platforms and only this table changes. Anything off the list falls back
         * rather than failing.
         */
        private val modelIcons: Map<String, Int> = mapOf(
            "calendar" to R.drawable.fd_ic_calendar,                          // a different time period
            "chart.line.uptrend.xyaxis" to R.drawable.fd_ic_trending_up,      // a trend
            "chart.pie.fill" to R.drawable.fd_ic_pie_chart,                   // a breakdown
            "arrow.up.arrow.down" to R.drawable.fd_ic_swap_vert,              // a comparison
            "magnifyingglass" to R.drawable.fd_ic_search,                     // more detail
            "creditcard.fill" to R.drawable.fd_ic_credit_card,                // transactions
            "banknote.fill" to R.drawable.fd_ic_banknote,                     // income
            "repeat" to R.drawable.fd_ic_repeat,                              // recurring charges
            "exclamationmark.triangle.fill" to R.drawable.fd_ic_warning,      // something unusual
            "lightbulb.fill" to R.drawable.fd_ic_lightbulb,                   // advice
        )

        /**
         * Stands in when the model omits the prefix or names an icon we don't carry.
         * Reads as "next" rather than as a wrong topic.
         */
        @DrawableRes
        val fallbackIcon: Int = R.drawable.fd_ic_arrow_forward

        /**
         * A follow-up the model wrote for the card on screen, in the form
         * `icon|question` — e.g. `calendar|Show me last month`.
         *
         * Forgiving on purpose. A missing prefix, an unknown icon or a stray pipe
         * costs the pill its icon, never the pill: the question is the part the user
         * needs, and it's already phrased the way they'd say it.
         */
        fun fromModel(raw: String): Suggestion {
            val pipe = raw.indexOf('|')
            if (pipe < 0) return Suggestion(fallbackIcon, raw, raw)

            val name = raw.substring(0, pipe).trim()
            val question = raw.substring(pipe + 1).trim()

            // Does the left side look like a symbol name at all? Icon names are one
            // unspaced token; a pipe inside a real question ("Is it A|B?") has words
            // either side. Without this test an off-list name like `chart.bar.fill`
            // would be shown to the user as pill text.
            val looksLikeIconName = name.isNotEmpty() && !name.contains(' ') && name.length < 40
            if (question.isEmpty() || !looksLikeIconName) {
                return Suggestion(fallbackIcon, raw, raw)
            }
            // Recognised name draws its icon; anything else still loses the prefix, so
            // a hallucinated symbol costs the icon rather than the wording.
            return Suggestion(modelIcons[name] ?: fallbackIcon, question, question)
        }

        /**
         * Openers, shown on the home screen. One per tool pairing, so every pill
         * exercises a different renderer.
         */
        val starters: List<Suggestion> = listOf(
            Suggestion(R.drawable.fd_ic_trending_up, "How am I doing?", "How am I doing?"),
//            Suggestion(R.drawable.fd_ic_credit_card, "Groceries", "What did I spend on groceries last month?"),
//            Suggestion(R.drawable.fd_ic_repeat, "Subscriptions", "What subscriptions am I paying for?"),
//            Suggestion(R.drawable.fd_ic_banknote, "Dining", "What did I spend on dining?"),
//            Suggestion(R.drawable.fd_ic_warning, "Anything unusual?", "Were there any unusual charges this month?"),
        )

        /**
         * Continuations, shown in the answer sheet. Every one leans on "that" — they
         * only make sense against the answer above them, which is the whole point:
         * the chips are what makes the thread's context usable without showing the
         * user a transcript to reason about.
         *
         * These are fixed. Model-written follow-ups arrive separately, as the
         * `nextSteps` argument riding along on a card's tool call, and lead the rail
         * here — see [QuestionBar].
         */
        val followUps: List<Suggestion> = listOf(
            Suggestion(R.drawable.fd_ic_calendar, "Compare months", "How does that compare over the last 6 months ?"),
//            Suggestion(R.drawable.fd_ic_search, "Show transactions", "Show me the individual transactions behind that."),
//            Suggestion(R.drawable.fd_ic_help, "Why the change?", "Why did that change?"),
//            Suggestion(R.drawable.fd_ic_trending_up, "Biggest movers", "What were the biggest movers in that?"),
//            Suggestion(R.drawable.fd_ic_lightbulb, "Set a budget", "Should I set a budget for that?"),
        )
    }
}

/** Shortens a question to something that fits on a chip. */
object PromptLabel {
    /**
     * The suggestion's own short form when the question came from one, otherwise a
     * trimmed version of whatever was typed.
     */
    fun short(question: String): String {
        val all = Suggestion.starters + Suggestion.followUps
        all.firstOrNull { it.text == question }?.let { return it.short }

        val trimmed = question.trim().trim('?', '.')
        if (trimmed.length <= 22) return trimmed

        // Cut on a word boundary so the ellipsis doesn't land mid-word.
        val clipped = trimmed.take(22)
        val cut = clipped.lastIndexOf(' ').let { if (it <= 0) clipped.length else it }
        return clipped.take(cut).trim() + "…"
    }
}
