package ai.metabind.ui.composables

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun VerticalSpace() = Spacer(modifier = Modifier.height(16.dp))

@Composable fun MediumVerticalSpace() = Spacer(modifier = Modifier.height(12.dp))

@Composable fun LargeVerticalSpace() = Spacer(modifier = Modifier.height(20.dp))

@Composable
fun DoubleVerticalSpace() = Spacer(modifier = Modifier.height(32.dp))

@Composable
fun HalfVerticalSpace() = Spacer(modifier = Modifier.height(8.dp))

@Composable
fun OneAndHalfVerticalSpace() = Spacer(modifier = Modifier.height(24.dp))

@Composable
fun NoBackBarVerticalSpace() = Spacer(modifier = Modifier.height(82.dp))

@Composable fun HorizontalSpace() = Spacer(modifier = Modifier.width(8.dp))

@Composable
fun HalfHorizontalSpace() = Spacer(modifier = Modifier.width(4.dp))
