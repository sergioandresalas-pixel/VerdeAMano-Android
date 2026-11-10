package com.verdeamano

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.verdeamano.core.design.*
import org.junit.Rule
import org.junit.Test

class UiStateRenderingTest {
    @get:Rule val compose = createComposeRule()
    @Test fun loadingHasIndeterminateProgress() {
        compose.setContent { VerdeTheme { LoadingState() } }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertIsDisplayed()
    }
    @Test fun connectionErrorOffersWorkingRetry() {
        val retry = mutableStateOf(false)
        compose.setContent { VerdeTheme {
            if(retry.value) EmptyState("Sin productos") else ErrorState("No pudimos conectar con Verde a Mano.") { retry.value = true }
        } }
        compose.onNodeWithText("No pudimos conectar con Verde a Mano.").assertIsDisplayed()
        compose.onNodeWithText("Reintentar").performClick()
        compose.onNodeWithText("Sin productos").assertIsDisplayed()
        compose.onNodeWithText("Reintentar").assertDoesNotExist()
    }
}
