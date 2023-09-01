// Copyright (C) 2022 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
package com.slack.circuit.star

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsIntent.COLOR_SCHEME_DARK
import androidx.browser.customtabs.CustomTabsIntent.COLOR_SCHEME_LIGHT
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.unit.dp
import com.slack.circuit.backstack.NavDecoration
import com.slack.circuit.backstack.rememberSaveableBackStack
import com.slack.circuit.foundation.Circuit
import com.slack.circuit.foundation.CircuitCompositionLocals
import com.slack.circuit.foundation.NavigableCircuitContent
import com.slack.circuit.foundation.NavigatorDefaults
import com.slack.circuit.foundation.RecordContentProvider
import com.slack.circuit.foundation.rememberCircuitNavigator
import com.slack.circuit.overlay.ContentWithOverlays
import com.slack.circuit.star.di.ActivityKey
import com.slack.circuit.star.di.AppScope
import com.slack.circuit.star.home.HomeScreen
import com.slack.circuit.star.imageviewer.ImageViewerAwareNavDecoration
import com.slack.circuit.star.imageviewer.ImageViewerScreen
import com.slack.circuit.star.navigation.CustomTabsIntentScreen
import com.slack.circuit.star.petdetail.PetDetailScreen
import com.slack.circuit.star.ui.LocalWindowWidthSizeClass
import com.slack.circuit.star.ui.StarTheme
import com.slack.circuitx.android.AndroidScreen
import com.slack.circuitx.android.IntentScreen
import com.slack.circuitx.android.rememberAndroidScreenAwareNavigator
import com.slack.circuitx.gesturenavigation.GestureNavigationDecoration
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.collections.immutable.ImmutableList
import javax.inject.Inject
import kotlinx.collections.immutable.persistentListOf
import okhttp3.HttpUrl.Companion.toHttpUrl

@ContributesMultibinding(AppScope::class, boundType = Activity::class)
@ActivityKey(MainActivity::class)
class MainActivity @Inject constructor(private val circuit: Circuit) : AppCompatActivity() {

  @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val initialBackstack =
      if (intent.data == null) {
        persistentListOf(HomeScreen)
      } else {
        val httpUrl = intent.data.toString().toHttpUrl()
        val animalId = httpUrl.pathSegments[1].substringAfterLast("-").toLong()
        val petDetailScreen = PetDetailScreen(animalId, null)
        persistentListOf(HomeScreen, petDetailScreen)
      }

    setContent {
      StarTheme {
        // TODO why isn't the windowBackground enough so we don't need to do this?
        Surface(color = MaterialTheme.colorScheme.background) {
          val backstack = rememberSaveableBackStack {
            initialBackstack.forEach { screen -> push(screen) }
          }
          val circuitNavigator = rememberCircuitNavigator(backstack)
          val navigator = rememberAndroidScreenAwareNavigator(circuitNavigator, this::goTo)
          val windowSizeClass = calculateWindowSizeClass(this)
          CompositionLocalProvider(
            LocalWindowWidthSizeClass provides windowSizeClass.widthSizeClass
          ) {
            CircuitCompositionLocals(circuit) {
              ContentWithOverlays {
                NavigableCircuitContent(
                  navigator = navigator,
                  backstack = backstack,
                  decoration =
                  PetListDetailNavDecoration(
                    ImageViewerAwareNavDecoration(
                      GestureNavigationDecoration(
                        navigator::pop
                      )
                    )
                  )
                )
              }
            }
          }
        }
      }
    }
  }

  private fun goTo(screen: AndroidScreen) =
    when (screen) {
      is CustomTabsIntentScreen -> goTo(screen)
      is IntentScreen -> screen.startWith(this)
      else -> error("Unknown AndroidScreen: $screen")
    }

  private fun goTo(screen: CustomTabsIntentScreen) {
    val scheme = CustomTabColorSchemeParams.Builder().setToolbarColor(0x000000).build()
    CustomTabsIntent.Builder()
      .setColorSchemeParams(COLOR_SCHEME_LIGHT, scheme)
      .setColorSchemeParams(COLOR_SCHEME_DARK, scheme)
      .setShowTitle(true)
      .build()
      .launchUrl(this, Uri.parse(screen.url))
  }
}

class PetListDetailNavDecoration(
  private val defaultNavDecoration: NavDecoration = NavigatorDefaults.DefaultDecoration
) : NavDecoration {
  @Suppress("UnstableCollections")
  @Composable
  override fun <T> DecoratedContent(
    args: ImmutableList<T>,
    backStackDepth: Int,
    modifier: Modifier,
    content: @Composable (T) -> Unit
  ) {
    val firstArg = args.firstOrNull()
    val secondArg = args.getOrNull(1)

    val isDetailFocused =
      secondArg is RecordContentProvider && secondArg.record.screen is HomeScreen &&
              firstArg is RecordContentProvider && firstArg.record.screen is PetDetailScreen
    val isListFocused =
      firstArg is RecordContentProvider && firstArg.record.screen is HomeScreen
    val isListOrDetailFocused = isDetailFocused || isListFocused

    if (isListOrDetailFocused) {
      ListDetailDecoratedContent(
        args = args,
        backStackDepth = backStackDepth,
        modifier = modifier,
        isDetailFocused = isDetailFocused,
        content = content,
      )
    } else {
      defaultNavDecoration.DecoratedContent(args, backStackDepth, modifier, content)
    }
  }
}

@Composable
fun <T> ListDetailDecoratedContent(
  args: ImmutableList<T>,
  backStackDepth: Int,
  modifier: Modifier,
  isDetailFocused: Boolean,
  content: @Composable (T) -> Unit
) {
  val widthWindowSizeClass = LocalWindowWidthSizeClass.current
  val numberOfPanes =
    when (widthWindowSizeClass) {
      WindowWidthSizeClass.Compact,
        WindowWidthSizeClass.Medium -> 1
      else -> 2
    }

  if (numberOfPanes == 1) {
    NavigatorDefaults.DefaultDecoration.DecoratedContent(args, backStackDepth, modifier, content)
  } else {
    Row(modifier) {
      Box(
        Modifier.weight(1f)
      ) {
        content(if (isDetailFocused) args[1] else args[0])
      }
      if (isDetailFocused) {
        Box(Modifier.weight(1f)) {
          content(args[0])
        }
      }
    }
  }
}
