package ai.metabind.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.metabind.ui.composables.QuizViewModel.QuizAnswerState.CORRECT
import ai.metabind.ui.composables.QuizViewModel.QuizAnswerState.CORRECT_NOT_SELECTED
import ai.metabind.ui.composables.QuizViewModel.QuizAnswerState.INCORRECT
import ai.metabind.ui.composables.QuizViewModel.QuizAnswerViewModel
import ai.metabind.ui.composables.QuizViewModel.QuizQuestionViewModel
import ai.metabind.ui.composables.QuizViewModel.QuizViewModelFactory
import ai.metabind.ui.theme.AppTheme
import ai.metabind.ui.theme.R
import java.io.Serializable

@Composable
fun Quiz(
    modifier: Modifier = Modifier,
    quizModel: QuizModel,
    viewModel: QuizViewModel =
        viewModel(factory = QuizViewModelFactory(quizModel = quizModel)),
) {
    val viewState = viewModel.viewState.collectAsState()
    Column(modifier = modifier) {
        QuizQuestions(
            viewState = viewState.value.questionsState, viewModel::onAnswerSelected)
        viewState.value.resultState.correctAnswers?.let {
            QuizResult(viewState = viewState.value.resultState)
        }
    }
}

@Composable
fun QuizQuestions(
    viewState: QuizViewModel.QuestionsViewState,
    onAnswerSelected: (QuizQuestionViewModel, QuizAnswerViewModel) -> Unit,
) {
    Column {
        viewState.questions.forEach { question ->
            QuizQuestion(
                question = question,
                questionNumber = question.index + 1,
                onAnswerSelected = onAnswerSelected)
            OneAndHalfVerticalSpace()
        }
    }
}

@Composable
fun QuizQuestion(
    question: QuizQuestionViewModel,
    questionNumber: Int,
    onAnswerSelected: (QuizQuestionViewModel, QuizAnswerViewModel) -> Unit,
) {
    Column(
        modifier =
        Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = SolidColor(MaterialTheme.colorScheme.outlineVariant),
                shape =
                RoundedCornerShape(
                    dimensionResource(id = R.dimen.quiz_question_container_corner_radius)))
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape =
                RoundedCornerShape(
                    dimensionResource(
                        id = R.dimen.quiz_question_container_corner_radius)))) {
        Text(
            modifier =
            Modifier.padding(
                start = 20.dp, top = 20.dp),
            text = stringResource(id = R.string.quiz_question_number, questionNumber),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        HalfVerticalSpace()
        Text(
            modifier = Modifier.padding(horizontal = 20.dp),
            text = question.text,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface)
        VerticalSpace()
        question.answers.forEach { answer ->
            QuizAnswer(
                question = question, answer = answer, onAnswerSelected = onAnswerSelected)
        }
    }
}

@Composable
fun QuizAnswer(
    question: QuizQuestionViewModel,
    answer: QuizAnswerViewModel,
    onAnswerSelected: (QuizQuestionViewModel, QuizAnswerViewModel) -> Unit,
) {
    val icon: @Composable () -> Unit
    val backgroundColor: Color
    when (answer.state) {
        CORRECT -> {
            icon = {
                Icon(
                    modifier = Modifier.size(dimensionResource(id = R.dimen.quiz_icon_size)),
                    painter = painterResource(id = R.drawable.ic_check_circle),
                    contentDescription = null,
                    tint = colorResource(id = R.color.quiz_positive_icon),
                )
            }
            backgroundColor = colorResource(id = R.color.quiz_positive_background)
        }

        CORRECT_NOT_SELECTED -> {
            icon = {
                Icon(
                    modifier = Modifier.size(dimensionResource(id = R.dimen.quiz_icon_size)),
                    painter = painterResource(id = R.drawable.ic_check_circle),
                    contentDescription = null,
                    tint = colorResource(id = R.color.quiz_neutral_icon),
                )
            }
            backgroundColor = MaterialTheme.colorScheme.surface
        }

        INCORRECT -> {
            icon = {
                Icon(
                    modifier = Modifier.size(dimensionResource(id = R.dimen.quiz_icon_size)),
                    painter = painterResource(id = R.drawable.ic_cancel),
                    contentDescription = null,
                    tint = colorResource(id = R.color.quiz_negative_icon),
                )
            }
            backgroundColor = colorResource(id = R.color.quiz_negative_background)
        }

        null -> {
            icon = {
                Box(
                    modifier =
                    Modifier
                        .size(dimensionResource(id = R.dimen.quiz_icon_size))
                        .border(
                            width = dimensionResource(id = R.dimen.quiz_empty_icon_width),
                            brush = SolidColor(MaterialTheme.colorScheme.onSurface),
                            shape =
                            RoundedCornerShape(
                                dimensionResource(id = R.dimen.quiz_empty_icon_size))))
            }
            backgroundColor = MaterialTheme.colorScheme.surface
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier =
            Modifier
                .padding(horizontal = 8.dp)
                .clip(
                    shape =
                    RoundedCornerShape(
                        dimensionResource(id = R.dimen.quiz_answer_clip_corner_radius)))
                .background(color = backgroundColor)
                .then(
                    if (question.isAnswered) {
                        Modifier
                    } else {
                        Modifier
                            .clickable(onClick = { onAnswerSelected(question, answer) })
                            .semantics { role = Role.Button }
                    })
                .padding(
                    horizontal = 8.dp,
                    vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(
                modifier = Modifier.weight(1f),
                text = answer.text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
            icon()
        }
        HorizontalDivider(
            modifier = Modifier
                .height(1.dp)
                .padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
fun QuizResult(viewState: QuizViewModel.ResultViewState) {
    val backgroundShape =
        RoundedCornerShape(dimensionResource(id = R.dimen.quiz_question_container_corner_radius))
    Column(
        modifier =
        Modifier
            .clip(shape = backgroundShape)
            .paint(
                painterResource(id = R.drawable.pattern),
                contentScale = ContentScale.FillWidth)
            .fillMaxWidth()
            .padding(all = 20.dp)) {
        Text(
            text = viewState.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
            color = colorResource(id = R.color.quiz_result_secondary_text))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(id = R.string.quiz_result_title),
            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.W600),
            color = Color.White)
        DoubleVerticalSpace()
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                modifier = Modifier.alignByBaseline(),
                text = "${viewState.percentage}",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.W600, fontSize = 88.sp),
                color = Color.White)
            HalfHorizontalSpace()
            Text(
                modifier = Modifier.alignByBaseline(),
                text = "%",
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.W600, fontSize = 48.sp),
                color = Color.White)
        }
        Text(
            text =
            stringResource(
                id = R.string.quiz_result_description,
                viewState.correctAnswers ?: "",
                viewState.totalAnswers),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
            color = colorResource(id = R.color.quiz_result_secondary_text))
    }
}

@Preview
@Composable
fun QuizPreview() {
    AppTheme {
        val quizModel =
            QuizModel(
                resultTitle = "TAKE THE QUIZ",
                questions =
                listOf(
                    QuizQuestion(
                        text = "Which Ivy League school is known as the Crimson?",
                        answers =
                        listOf(
                            QuizAnswer("Answer 1"),
                            QuizAnswer("Answer 2"),
                            QuizAnswer("Answer 3"),
                            QuizAnswer("Answer 4"),
                            QuizAnswer("Answer 5"),
                        ),
                        correctAnswerIndex = 1),
                    QuizQuestion(
                        text = "What is Harvard's motto?",
                        answers =
                        listOf(
                            QuizAnswer("E Pluribus Unum"),
                            QuizAnswer("Veritas"),
                            QuizAnswer("Lux et Veritas"),
                            QuizAnswer("Mens et Manus"),
                        ),
                        correctAnswerIndex = 1)))
        Column {
            Box(modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp)) {
                Quiz(
                    quizModel = quizModel,
                    viewModel =
                    QuizViewModel(
                        quizModel = quizModel, savedState = SavedStateHandle()))
            }
        }
    }
}

/** Data Models */
data class QuizModel(
    val resultTitle: String,
    val questions: List<QuizQuestion>,
) : Serializable

data class QuizQuestion(
    val text: String,
    val answers: List<QuizAnswer>,
    val correctAnswerIndex: Int,
) : Serializable

data class QuizAnswer(
    val text: String,
) : Serializable
