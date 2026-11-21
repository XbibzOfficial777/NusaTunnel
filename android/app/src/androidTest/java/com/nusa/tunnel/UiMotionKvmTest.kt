package com.nusa.tunnel

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** KVM smoke coverage for the system splash handoff and animated top-level navigation. */
@RunWith(AndroidJUnit4::class)
class UiMotionKvmTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun dashboardAndAnimatedNavigationRemainUsableAfterLaunch() {
        compose.onNodeWithText("NUSATUNNEL").assertIsDisplayed()
        compose.onNodeWithText("HUBUNGKAN").assertIsDisplayed()

        compose.onNodeWithText("Server").performClick()
        compose.onNodeWithText("Cari nama / host…").assertIsDisplayed()

        compose.onNodeWithText("Atur").performClick()
        compose.onNodeWithText("Settings").assertIsDisplayed()

        compose.onNodeWithText("Beranda").performClick()
        compose.onNodeWithText("NUSATUNNEL").assertIsDisplayed()
    }
}
