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

import android.support.v4.media.session.MediaControllerCompat

/**
 * Holds test definitions used by both the mobile and TV apps
 */
fun runPlayTest(
        controller: MediaControllerCompat,
        logger: (tag: String, message: String) -> Unit?
) = Test(Test.androidResources.getString(R.string.play_test_logs_title), controller, logger)
        .apply {
            addStep(ConfigurePlay(this))
            addStep(WaitForPlaying(this))
        }.runTest()

fun runPlayFromSearchTest(
        query: String,
        controller: MediaControllerCompat,
        logger: (tag: String, message: String) -> Unit?
) = Test(Test.androidResources.getString(R.string.play_search_test_logs_title), controller, logger)
        .apply {
            addStep(ConfigurePlayFromSearch(this, query))
            addStep(WaitForPlayingBeginning(this))
        }.runTest()

fun runPlayFromMediaIdTest(
        query: String,
        controller: MediaControllerCompat,
        logger: (tag: String, message: String) -> Unit?
) = Test(
        Test.androidResources.getString(R.string.play_media_id_test_logs_title),
        controller,
        logger
).apply {
    addStep(ConfigurePlayFromMediaId(this, query))
    addStep(WaitForPlayingBeginning(this))
}.runTest()

fun runPlayFromUriTest(
        query: String,
        controller: MediaControllerCompat,
        logger: (tag: String, message: String) -> Unit?
) = Test(Test.androidResources.getString(R.string.play_uri_test_logs_title), controller, logger)
        .apply {
            addStep(ConfigurePlayFromUri(this, query))
            addStep(WaitForPlayingBeginning(this))
        }.runTest()

fun runPauseTest(
        controller: MediaControllerCompat,
        logger: (tag: String, message: String) -> Unit?
) = Test(Test.androidResources.getString(R.string.pause_test_logs_title), controller, logger)
        .apply {
            addStep(ConfigurePause(this))
            addStep(WaitForPaused(this))
        }.runTest()

fun runStopTest(
        controller: MediaControllerCompat,
        logger: (tag: String, message: String) -> Unit?
) = Test(Test.androidResources.getString(R.string.stop_test_logs_title), controller, logger)
        .apply {
            addStep(ConfigureStop(this))
            addStep(WaitForStopped(this))
        }.runTest()

fun runSkipToNextTest(
        controller: MediaControllerCompat,
        logger: (tag: String, message: String) -> Unit?
) = Test(Test.androidResources.getString(R.string.skip_next_test_logs_title), controller, logger)
        .apply {
            addStep(ConfigureSkipToNext(this))
            addStep(WaitForSkip(this))
        }.runTest()

fun runSkipToPrevTest(
        controller: MediaControllerCompat,
        logger: (tag: String, message: String) -> Unit?
) = Test(Test.androidResources.getString(R.string.skip_prev_test_logs_title), controller, logger)
        .apply {
            addStep(ConfigureSkipToPrevious(this))
            addStep(WaitForSkip(this))
        }.runTest()

fun runSkipToItemTest(
        query: String,
        controller: MediaControllerCompat,
        logger: (tag: String, message: String) -> Unit?
) = Test(Test.androidResources.getString(R.string.skip_item_test_logs_title), controller, logger)
        .apply {
            addStep(ConfigureSkipToItem(this, query))
            addStep(WaitForSkip(this))
        }.runTest()

fun runSeekToTest(
        query: String,
        controller: MediaControllerCompat,
        logger: (tag: String, message: String) -> Unit?
) = Test(Test.androidResources.getString(R.string.seek_test_logs_title), controller, logger)
        .apply {
            addStep(ConfigureSeekTo(this, query))
            addStep(WaitForTerminalAtTarget(this))
        }.runTest()