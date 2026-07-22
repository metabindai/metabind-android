package ai.metabind.ui.composables

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import java.io.Serializable
import ai.metabind.ui.delegates.ViewStateProviderDelegate
import ai.metabind.ui.delegates.ViewStateProviderDelegateImpl
import timber.log.Timber

class QuizViewModel(quizModel: QuizModel, savedState: SavedStateHandle) :
    ViewModel(),
    ViewStateProviderDelegate<QuizViewModel.ViewState> by ViewStateProviderDelegateImpl(
        ViewState(), savedState) {

    init {
        updateState(
            viewState.value.copy(
                questionsState = QuestionsViewState(questions = createViewModels(quizModel)),
                resultState =
                    ResultViewState(
                        title = quizModel.resultTitle, totalAnswers = quizModel.questions.size)))
    }

    private fun createViewModels(quizModel: QuizModel): List<QuizQuestionViewModel> {
        return quizModel.questions
            .filter { question ->
                if (question.answers.isEmpty()) {
                    Timber.w("Quiz Question must have at least one answer!")
                    return@filter false
                }
                if (question.correctAnswerIndex >= question.answers.size) {
                    Timber.w("invalid correct answer index!")
                    return@filter false
                }
                return@filter true
            }
            .mapIndexed { index, question ->
                val answerViewModels =
                    question.answers.map { answer -> QuizAnswerViewModel(text = answer.text) }
                QuizQuestionViewModel(
                    text = question.text,
                    answers = answerViewModels,
                    index = index,
                    correctAnswerIndex = question.correctAnswerIndex)
            }
    }

    fun onAnswerSelected(question: QuizQuestionViewModel, userAnswer: QuizAnswerViewModel) {
        var answeredCorrectly = false
        val answers =
            question.answers.mapIndexed { index, answer ->
                val isCorrectAnswer = index == question.correctAnswerIndex
                val selectedByUser = userAnswer == answer
                answer.copy(
                    state =
                        if (isCorrectAnswer) {
                            if (selectedByUser) {
                                answeredCorrectly = true
                                QuizAnswerState.CORRECT
                            } else {
                                QuizAnswerState.CORRECT_NOT_SELECTED
                            }
                        } else if (selectedByUser) {
                            QuizAnswerState.INCORRECT
                        } else {
                            null
                        })
            }

        val updatedQuestion =
            question.copy(
                answers = answers, isAnswered = true, isAnsweredCorrectly = answeredCorrectly)

        val updatedQuestions =
            viewState.value.questionsState.questions.map {
                if (it.index == updatedQuestion.index) {
                    updatedQuestion
                } else {
                    it
                }
            }

        updateState(
            viewState.value.copy(questionsState = QuestionsViewState(questions = updatedQuestions)))

        mayBeFinishQuiz()
    }

    private fun mayBeFinishQuiz() {
        var correctAnswers = 0
        val questions = viewState.value.questionsState.questions
        questions.forEach {
            if (!it.isAnswered) {
                return@mayBeFinishQuiz
            }
            if (it.isAnsweredCorrectly) {
                correctAnswers++
            }
        }
        val percentage = ((correctAnswers.toDouble() / questions.size) * 100).toInt()

        updateState(
            viewState.value.copy(
                resultState =
                    viewState.value.resultState.copy(
                        percentage = percentage,
                        correctAnswers = correctAnswers,
                    )))
    }

    internal class QuizViewModelFactory(val quizModel: QuizModel) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(
            modelClass: Class<T>,
            extras: CreationExtras,
        ): T {
            val savedStateHandle = extras.createSavedStateHandle()
            return QuizViewModel(quizModel = quizModel, savedStateHandle) as T
        }
    }

    /** View Models */
    data class ViewState(
        val questionsState: QuestionsViewState = QuestionsViewState(emptyList()),
        val resultState: ResultViewState = ResultViewState(title = "", totalAnswers = 0),
    ) : Serializable

    data class ResultViewState(
        val title: String,
        val percentage: Int? = null,
        val correctAnswers: Int? = null,
        val totalAnswers: Int,
    ) : Serializable

    data class QuestionsViewState(
        val questions: List<QuizQuestionViewModel>,
    ) : Serializable

    data class QuizQuestionViewModel(
        val text: String,
        val answers: List<QuizAnswerViewModel>,
        val index: Int,
        val correctAnswerIndex: Int,
        val isAnswered: Boolean = false,
        val isAnsweredCorrectly: Boolean = false,
    ) : Serializable

    data class QuizAnswerViewModel(
        val text: String,
        val state: QuizAnswerState? = null,
    ) : Serializable

    enum class QuizAnswerState {
        CORRECT,
        INCORRECT,
        CORRECT_NOT_SELECTED
    }
}
