/*
 * Copyright 2018 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.mediacontroller

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import androidx.annotation.RequiresApi

/**
 * Holds test definitions used by both the mobile and TV apps
 */
fun runPlayTest(
        testId: Int,
        controller: MediaControllerCompat,
        callback: (result: TestResult, testId: Int) -> Unit,
        logger: (tag: String, message: String) -> Unit?
) = Test(Test.androidResources.getString(R.string.play_test_logs_title), TestType.REQUIRED, controller, logger)
        .apply {
            addStep(ConfigurePlay(this))
            addStep(WaitForPlaying(this))
        }.runTest(testId, callback)

fun runPlayFromSearchTest(
        testId: Int,
        query: String,
        controller: MediaControllerCompat,
        callback: (result: TestResult, testId: Int) -> Unit,
        logger: (tag: String, message: String) -> Unit?
) = Test(Test.androidResources.getString(R.string.play_search_test_logs_title), TestType.REQUIRED, controller, logger)
        .apply {
            addStep(ConfigurePlayFromSearch(this, query))
            addStep(WaitForPlayingBeginning(this))
        }.runTest(testId, callback)

fun runPlayFromMediaIdTest(
        testId: Int,
        query: String,
        controller: MediaControllerCompat,
        callback: (result: TestResult, testId: Int) -> Unit,
        logger: (tag: String, message: String) -> Unit?
) = Test(
        Test.androidResources.getString(R.string.play_media_id_test_logs_title),
        TestType.REQUIRED,
        controller,
        logger
).apply {
    addStep(ConfigurePlayFromMediaId(this, query))
    addStep(WaitForPlayingBeginning(this))
}.runTest(testId, callback)

fun runPlayFromUriTest(
        testId: Int,
        query: String,
        controller: MediaControllerCompat,
        callback: (result: TestResult, testId: Int) -> Unit,
        logger: (tag: String, message: String) -> Unit?
) = Test(Test.androidResources.getString(R.string.play_uri_test_logs_title), TestType.REQUIRED, controller, logger)
        .apply {
            addStep(ConfigurePlayFromUri(this, query))
            addStep(WaitForPlayingBeginning(this))
        }.runTest(testId, callback)

fun runPauseTest(
        testId: Int,
        controller: MediaControllerCompat,
        callback: (result: TestResult, testId: Int) -> Unit,
        logger: (tag: String, message: String) -> Unit?
) = Test(Test.androidResources.getString(R.string.pause_test_logs_title), TestType.REQUIRED, controller, logger)
        .apply {
            addStep(ConfigurePause(this))
            addStep(WaitForPaused(this))
        }.runTest(testId, callback)

fun runStopTest(
        testId: Int,
        controller: MediaControllerCompat,
        callback: (result: TestResult, testId: Int) -> Unit,
        logger: (tag: String, message: String) -> Unit?
) = Test(Test.androidResources.getString(R.string.stop_test_logs_title), TestType.REQUIRED, controller, logger)
        .apply {
            addStep(ConfigureStop(this))
            addStep(WaitForStopped(this))
        }.runTest(testId, callback)

fun runSkipToNextTest(
        testId: Int,
        controller: MediaControllerCompat,
        callback: (result: TestResult, testId: Int) -> Unit,
        logger: (tag: String, message: String) -> Unit?
) = Test(Test.androidResources.getString(R.string.skip_next_test_logs_title), TestType.REQUIRED, controller, logger)
        .apply {
            addStep(ConfigureSkipToNext(this))
            addStep(WaitForSkip(this))
        }.runTest(testId, callback)

fun runSkipToPrevTest(
        testId: Int,
        controller: MediaControllerCompat,
        callback: (result: TestResult, testId: Int) -> Unit,
        logger: (tag: String, message: String) -> Unit?
) = Test(Test.androidResources.getString(R.string.skip_prev_test_logs_title), TestType.REQUIRED, controller, logger)
        .apply {
            addStep(ConfigureSkipToPrevious(this))
            addStep(WaitForSkip(this))
        }.runTest(testId, callback)

fun runSkipToItemTest(
        testId: Int,
        query: String,
        controller: MediaControllerCompat,
        callback: (result: TestResult, testId: Int) -> Unit,
        logger: (tag: String, message: String) -> Unit?
) = Test(Test.androidResources.getString(R.string.skip_item_test_logs_title), TestType.REQUIRED, controller, logger)
        .apply {
            addStep(ConfigureSkipToItem(this, query))
            addStep(WaitForSkip(this))
        }.runTest(testId, callback)

fun runSeekToTest(
        testId: Int,
        query: String,
        controller: MediaControllerCompat,
        callback: (result: TestResult, testId: Int) -> Unit,
        logger: (tag: String, message: String) -> Unit?
) = Test(Test.androidResources.getString(R.string.seek_test_logs_title), TestType.REQUIRED, controller, logger)
        .apply {
            addStep(ConfigureSeekTo(this, query))
            addStep(WaitForTerminalAtTarget(this))
        }.runTest(testId, callback)

fun runErrorResolutionDataTest(
        testId: Int,
        controller: MediaControllerCompat,
        callback: (result: TestResult, testId: Int) -> Unit,
        logger: (tag: String, message: String) -> Unit?
) = Test(Test.androidResources
        .getString(R.string.error_resolution_test_logs_title), TestType.REQUIRED, controller, logger)
        .apply {
            addStep(CheckErrorResolution(this))
        }.runTest(testId, callback)

fun runCustomActionIconTypeTest(
        testId: Int,
        context: Context,
        controller: MediaControllerCompat,
        appDetails: MediaAppDetails?,
        callback: (result: TestResult, testId: Int) -> Unit,
        logger: (tag: String, message: String) -> Unit?
) = Test(Test.androidResources
        .getString(R.string.custom_actions_icon_test_logs_title), TestType.REQUIRED, controller, logger)
        .apply {
            addStep(CheckCustomActions(this, context, appDetails))
        }.runTest(testId, callback)

fun runPreferenceTest(
        testId: Int,
        controller: MediaControllerCompat,
        appDetails: MediaAppDetails?,
        packageManager: PackageManager,
        callback: (result: TestResult, testId: Int) -> Unit,
        logger: (tag: String, message: String) -> Unit?
) = Test(Test.androidResources
        .getString(R.string.preference_activity_test_logs_title), TestType.REQUIRED, controller, logger)
        .apply {
            addStep(CheckForPreferences(this, appDetails, packageManager))
        }.runTest(testId, callback)

fun runLauncherTest(
        testId: Int,
        controller: MediaControllerCompat,
        appDetails: MediaAppDetails?,
        packageManager: PackageManager,
        callback: (result: TestResult, testId: Int) -> Unit,
        logger: (tag: String, message: String) -> Unit?
) = Test(Test.androidResources
        .getString(R.string.launcher_intent_test_logs_title), TestType.REQUIRED, controller, logger)
        .apply {
            addStep(CheckForLauncher(this, appDetails, packageManager))
        }.runTest(testId, callback)

fun runInitialPlaybackStateTest(
        testId: Int,
        controller: MediaControllerCompat,
        callback: (result: TestResult, testId: Int) -> Unit,
        logger: (tag: String, message: String) -> Unit?
) = Test(Test.androidResources
        .getString(R.string.launcher_intent_test_logs_title), TestType.REQUIRED, controller, logger)
        .apply {
            addStep(CheckPlaybackState(this))
        }.runTest(testId, callback)

@RequiresApi(Build.VERSION_CODES.N)
fun runBrowseTreeDepthTest(
        testId: Int,
        controller: MediaControllerCompat,
        browser: MediaBrowserCompat?,
        callback: (result: TestResult, testId: Int) -> Unit,
        logger: (tag: String, message: String) -> Unit?
) = Test(Test.androidResources
        .getString(R.string.browse_tree_depth_test_logs_title), TestType.OPTIONAL, controller, logger)
        .apply {
            addStep(CheckBrowseDepth(this, browser))
        }.runTest(testId, callback)

@RequiresApi(Build.VERSION_CODES.N)
fun runBrowseTreeStructureTest(
        testId: Int,
        controller: MediaControllerCompat,
        browser: MediaBrowserCompat?,
        callback: (result: TestResult, testId: Int) -> Unit,
        logger: (tag: String, message: String) -> Unit?
) = Test(Test.androidResources
        .getString(R.string.browse_tree_structure_test_logs_title), TestType.OPTIONAL, controller, logger)
        .apply {
            addStep(CheckBrowseStructure(this, browser))
        }.runTest(testId, callback)

fun runSearchTest(
        testId: Int,
        controller: MediaControllerCompat,
        browser: MediaBrowserCompat?,
        callback: (result: TestResult, testId: Int) -> Unit,
        logger: (tag: String, message: String) -> Unit?
) = Test(Test.androidResources
        .getString(R.string.search_test_test_logs_title), TestType.OPTIONAL, controller, logger)
        .apply {
            addStep(CheckSearchSupported(this, browser))
        }.runTest(testId, callback)

fun runContentStyleTest(
        testId: Int,
        controller: MediaControllerCompat,
        browser: MediaBrowserCompat?,
        callback: (result: TestResult, testId: Int) -> Unit,
        logger: (tag: String, message: String) -> Unit?
) = Test(Test.androidResources
        .getString(R.string.content_style_test_logs_title), TestType.OPTIONAL, controller, logger)
        .apply {
            addStep(CheckContentStyle(this, browser))
        }.runTest(testId, callback)

@RequiresApi(Build.VERSION_CODES.N)
fun runMediaArtworkTest(
        testId: Int,
        controller: MediaControllerCompat,
        browser: MediaBrowserCompat?,
        callback: (result: TestResult, testId: Int) -> Unit,
        logger: (tag: String, message: String) -> Unit?
) = Test(Test.androidResources
        .getString(R.string.media_artwork_test_logs_title), TestType.REQUIRED, controller, logger)
        .apply {
            addStep(CheckMediaArtwork(this, browser))
        }.runTest(testId, callback)