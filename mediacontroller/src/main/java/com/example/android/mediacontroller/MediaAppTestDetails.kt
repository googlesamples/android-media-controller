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

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import kotlin.math.abs

/**
 * This is where verification tests are managed and configured
 */

var currentTest: Test? = null

class Test(
        testName: String,
        val mediaController: MediaControllerCompat,
        val testLogger: (tag: String, msg: String) -> Unit?
) : HandlerThread(testName) {
    private val steps = mutableListOf<TestStep>()
    private var stepIndex = 0
    var origState: PlaybackStateCompat? = null
    var origMetadata: MediaMetadataCompat? = null
    private lateinit var callback: MediaControllerCompat.Callback
    val extras = Bundle()
    lateinit var handler: Handler // TODO(nevmital): might not need to hold reference

    fun addStep(step: TestStep) {
        if (currentTest == this) {
            testLogger(name, "Can't add another step while test is running")
            return
        }
        if (step.test != this) {
            testLogger(name, "Can't add a step that doesn't belong to this test")
            return
        }
        steps.add(step)
    }

    fun runTest() {
        currentTest?.run {
            testLogger(name, "Interrupted: Ending")
            endTest()
        }
        currentTest = this
        origState = mediaController.playbackState
        origMetadata = mediaController.metadata

        // Start Looper
        start()
        testLogger(
                name,
                "Starting test with state " + playbackStateToName(
                        origState?.state ?: PlaybackStateCompat.STATE_NONE
                ) + " and metadata ${origMetadata.toBasicString()}"
        )

        handler = object : Handler(this.looper) {
            override fun handleMessage(msg: Message?) {
                if (msg == null) {
                    testLogger(name, "Received empty message")
                    return
                }
                if (stepIndex >= steps.size) {
                    testLogger(name, "Success!")
                    endTest()
                    return
                }

                val currentStep = steps[stepIndex]
                val state = mediaController.playbackState
                val metadata = mediaController.metadata
                // Process received message
                val status = when (msg.what) {
                    STATE_CHANGED, METADATA_CHANGED, RUN_STEP -> {
                        extras.putInt("STEP_TRIGGER", msg.what)
                        currentStep.execute(state, metadata)
                    }
                    TIMED_OUT -> {
                        testLogger(name, "Failed: Test timed out")
                        TestStepStatus.STEP_FAIL
                    }
                    else -> {
                        testLogger(name, "Invalid message received")
                        return
                    }
                }

                if (state.state == PlaybackStateCompat.STATE_ERROR) {
                    testLogger(
                            name,
                            "${errorCodeToName(state.errorCode)}: ${state.errorMessage}"
                    )
                }
                // Process TestStep result
                when (status) {
                    TestStepStatus.STEP_PASS -> {
                        testLogger(
                                currentStep.logTag,
                                "Passed: ${playbackStateToName(state.state)}"
                        )
                        // Move to next step
                        ++stepIndex
                        // Run step
                        Message.obtain(this, RUN_STEP).sendToTarget()
                    }
                    TestStepStatus.STEP_CONTINUE -> {
                        // No op
                        testLogger(
                                currentStep.logTag,
                                "Continuing: ${playbackStateToName(state.state)}"
                        )
                    }
                    TestStepStatus.STEP_FAIL -> {
                        if (msg.what != TIMED_OUT) {
                            testLogger(
                                    currentStep.logTag,
                                    "Failed: ${playbackStateToName(state.state)}"
                            )
                        }
                        endTest()
                    }
                }
            }
        }

        callback = object : MediaControllerCompat.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                Message.obtain(handler, STATE_CHANGED, state).sendToTarget()
            }

            override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
                Message.obtain(handler, METADATA_CHANGED, metadata).sendToTarget()
            }
        }

        // Start sending messages to looper
        mediaController.registerCallback(callback)
        Message.obtain(handler, RUN_STEP).sendToTarget()
        handler.sendMessageDelayed(Message.obtain(handler, TIMED_OUT), TEST_TIMEOUT)
    }

    fun endTest() {
        mediaController.unregisterCallback(callback)
        currentTest = null
        quit()
    }

    companion object {
        const val TIMED_OUT = 0
        const val RUN_STEP = 1
        const val STATE_CHANGED = 2
        const val METADATA_CHANGED = 3

        const val TEST_TIMEOUT = 5000L // 5 seconds
        const val POSITION_LENIENCY = 200L // 0.2 seconds
    }
}

class TestOptionDetails(val name: String, val desc: String, val runTest: (query: String) -> Unit)

enum class TestStepStatus {
    STEP_PASS, STEP_CONTINUE, STEP_FAIL
}

/**
 * In these states, a TestStep should return STEP_PASS if the other requirements for the test are
 * met, otherwise it should return STEP_FAIL.
 */
val terminalStates = intArrayOf(
        PlaybackStateCompat.STATE_ERROR,
        PlaybackStateCompat.STATE_NONE,
        PlaybackStateCompat.STATE_PAUSED,
        PlaybackStateCompat.STATE_PLAYING,
        PlaybackStateCompat.STATE_STOPPED
)

/**
 * In these states, a TestStep should log the state along with any warnings (e.g. if the state is
 * STATE_SKIPPING_TO_NEXT but the request was skipToPrevious), and return STEP_CONTINUE.
 */
val transitionStates = intArrayOf(
        PlaybackStateCompat.STATE_BUFFERING,
        PlaybackStateCompat.STATE_CONNECTING,
        PlaybackStateCompat.STATE_FAST_FORWARDING,
        PlaybackStateCompat.STATE_REWINDING,
        PlaybackStateCompat.STATE_SKIPPING_TO_NEXT,
        PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS,
        PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM
)

interface TestStep {
    val test: Test
    val logTag: String
    fun execute(
            currState: PlaybackStateCompat?,
            currMetadata: MediaMetadataCompat?
    ): TestStepStatus

    fun checkActionSupported(state: PlaybackStateCompat?, action: Long) {
        if (state == null) {
            test.testLogger(logTag, "Warning: PlaybackState is null")
        } else {
            if (state.actions and action == 0L) {
                test.testLogger(logTag, "Warning: ${actionToString(action)} is not supported")
            }
            if (state.actions == 0L) {
                test.testLogger(logTag, "Warning: No actions supported")
            }
        }
    }
}

/*
 * "Configure" steps will:
 * 1. (if applicable) parse the query text into a value usable by the transport control request
 * 2. Check if the action to be tested is marked as supported
 * 3. Send the appropriate transport control request
 * If the query is unable to be parsed into a usable format, the step will FAIL.
 * Otherwise, the step will always PASS. The transport control request will be sent regardless of
 * whether or not the action is in the supported actions list (for the case where the supported
 * actions list is mis-configured). The assumption is that the MediaController will successfully
 * send the transport control request to the MediaSession, since this communication is handled by
 * Android libraries.
 */

/**
 * No query input. This step checks if ACTION_PLAY is supported and sends the play() request.
 * Always returns STEP_PASS.
 */
class ConfigurePlay(override val test: Test) : TestStep {
    override val logTag = "${test.name}.CP"
    override fun execute(
            currState: PlaybackStateCompat?,
            currMetadata: MediaMetadataCompat?
    ): TestStepStatus {
        checkActionSupported(currState, PlaybackStateCompat.ACTION_PLAY)
        test.testLogger(logTag, "Running: Sending play() TransportControl request")
        test.mediaController.transportControls.play()
        return TestStepStatus.STEP_PASS
    }
}

fun makePlayFromBundle(query: String): Bundle {
    val extras = Bundle()
    extras.putString("android.intent.extra.user_query_language", "en-US")
    extras.putString("query", query)
    extras.putString(
            "android.intent.extra.REFERRER_NAME",
            "android-app://com.google.android.googlequicksearchbox/https/www.google.com"
    )
    extras.putString("android.intent.extra.user_query", query)
    extras.putString("android.intent.extra.focus", "vnd.android.cursor.item/*")
    extras.putString("android.intent.extra.title", query)
    return extras
}

/**
 * The query is a search phrase to be used to find a media item to play. This step checks if
 * ACTION_PLAY_FROM_SEARCH is supported and sends the playFromSearch() request. Always returns
 * STEP_PASS. Note that for an empty/null query, this request should be treated as a request to
 * play any music.
 */
class ConfigurePlayFromSearch(override val test: Test, private val query: String) : TestStep {
    override val logTag = "${test.name}.CPFS"
    override fun execute(
            currState: PlaybackStateCompat?,
            currMetadata: MediaMetadataCompat?
    ): TestStepStatus {
        val extras = makePlayFromBundle(query)

        checkActionSupported(currState, PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH)
        test.testLogger(
                logTag,
                "Running: Sending playFromSearch($query, $extras) TransportControl request"
        )
        test.mediaController.transportControls.playFromSearch(query, extras)
        return TestStepStatus.STEP_PASS
    }
}

/**
 * The query is expected to be the media id of a media item to play. This step checks if
 * ACTION_PLAY_FROM_MEDIA_ID is supported and sends the playFromMediaId() request. Returns STEP_FAIL
 * if the query is empty/null, otherwise returns STEP_PASS.
 */
class ConfigurePlayFromMediaId(override val test: Test, private val query: String) : TestStep {
    override val logTag = "${test.name}.CPFMI"
    override fun execute(
            currState: PlaybackStateCompat?,
            currMetadata: MediaMetadataCompat?
    ): TestStepStatus {
        if (query == "") {
            test.testLogger(logTag, "Error: empty query")
            return TestStepStatus.STEP_FAIL
        }

        val extras = makePlayFromBundle(query)

        checkActionSupported(currState, PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID)
        test.testLogger(
                logTag,
                "Running: Sending playFromMediaId($query, $extras) TransportControl request"
        )
        test.mediaController.transportControls.playFromMediaId(query, extras)
        return TestStepStatus.STEP_PASS
    }
}

/**
 * The query is expected to be the uri of a media item to play. This step checks if
 * ACTION_PLAY_FROM_URI is supported and sends the playFromUri() request. Returns STEP_FAIL if the
 * query is empty/null, otherwise returns STEP_PASS.
 */
class ConfigurePlayFromUri(override val test: Test, private val query: String) : TestStep {
    override val logTag = "${test.name}.CPFU"
    override fun execute(
            currState: PlaybackStateCompat?,
            currMetadata: MediaMetadataCompat?
    ): TestStepStatus {
        if (query == "") {
            test.testLogger(logTag, "Error: empty query")
            return TestStepStatus.STEP_FAIL
        }

        val uri = Uri.parse(query)
        val extras = makePlayFromBundle(query)

        checkActionSupported(currState, PlaybackStateCompat.ACTION_PLAY_FROM_URI)
        test.testLogger(
                logTag,
                "Running: Sending playFromUri($uri, $extras) TransportControl request"
        )
        test.mediaController.transportControls.playFromUri(uri, extras)
        return TestStepStatus.STEP_PASS
    }
}

/**
 * No query input. This step checks if ACTION_PAUSE is supported and sends the pause() request.
 * Always returns STEP_PASS.
 */
class ConfigurePause(override val test: Test) : TestStep {
    override val logTag = "${test.name}.CP"
    override fun execute(
            currState: PlaybackStateCompat?,
            currMetadata: MediaMetadataCompat?
    ): TestStepStatus {
        checkActionSupported(currState, PlaybackStateCompat.ACTION_PAUSE)
        test.testLogger(logTag, "Running: Sending pause() TransportControl request")
        test.mediaController.transportControls.pause()
        return TestStepStatus.STEP_PASS
    }
}

/**
 * No query input. This step checks if ACTION_STOP is supported and sends the stop() request.
 * Always returns STEP_PASS.
 */
class ConfigureStop(override val test: Test) : TestStep {
    override val logTag = "${test.name}.CS"
    override fun execute(
            currState: PlaybackStateCompat?,
            currMetadata: MediaMetadataCompat?
    ): TestStepStatus {
        checkActionSupported(currState, PlaybackStateCompat.ACTION_STOP)
        test.testLogger(logTag, "Running: Sending stop() TransportControl request")
        test.mediaController.transportControls.stop()
        return TestStepStatus.STEP_PASS
    }
}

/**
 * No query input. This step checks if ACTION_SKIP_TO_NEXT is supported and sends the skipToNext()
 * request. This request requires that the metadata changes, so it initializes the METADATA_CHANGED
 * key in the Test's extras Bundle to false. Always returns STEP_PASS.
 */
class ConfigureSkipToNext(override val test: Test) : TestStep {
    override val logTag = "${test.name}.CSTN"
    override fun execute(
            currState: PlaybackStateCompat?,
            currMetadata: MediaMetadataCompat?
    ): TestStepStatus {
        checkActionSupported(currState, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
        test.extras.putBoolean("METADATA_CHANGED", false)
        test.testLogger(logTag, "Running: Sending skipToNext() TransportControl request")
        test.mediaController.transportControls.skipToNext()
        return TestStepStatus.STEP_PASS
    }
}

/**
 * No query input. This step checks if ACTION_SKIP_TO_PREVIOUS is supported and sends the
 * skipToPrevious() request. This request requires that the metadata changes, so it initializes the
 * METADATA_CHANGED key in the Test's extras Bundle to false. Always returns STEP_PASS.
 */
class ConfigureSkipToPrevious(override val test: Test) : TestStep {
    override val logTag = "${test.name}.CSTP"
    override fun execute(
            currState: PlaybackStateCompat?,
            currMetadata: MediaMetadataCompat?
    ): TestStepStatus {
        checkActionSupported(currState, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
        test.extras.putBoolean("METADATA_CHANGED", false)
        test.testLogger(logTag, "Running: Sending skipToPrevious() TransportControl request")
        test.mediaController.transportControls.skipToPrevious()
        return TestStepStatus.STEP_PASS
    }
}

/**
 * Expects query to be the Queue Item ID of a media item in the Queue (numerical Long). This step
 * checks if ACTION_SKIP_TO_QUEUE_ITEM is supported and sends the skipToQueueItem() request. This
 * request requires that the metadata changes, so it initializes the METADATA_CHANGED key in the
 * Test's extras Bundle to false. Returns STEP_FAIL if the query can't be parsed to a Long, returns
 * STEP_PASS otherwise.
 */
class ConfigureSkipToItem(override val test: Test, private val query: String) : TestStep {
    override val logTag = "${test.name}.CSTI"
    override fun execute(
            currState: PlaybackStateCompat?,
            currMetadata: MediaMetadataCompat?
    ): TestStepStatus {
        val itemId = query.toLongOrNull()
        if (itemId == null) {
            test.testLogger(logTag, "Error: Couldn't parse query [$query]")
            return TestStepStatus.STEP_FAIL
        }

        checkActionSupported(currState, PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM)
        test.extras.putBoolean("METADATA_CHANGED", false)
        test.testLogger(
                logTag,
                "Running: Sending skipToQueueItem($itemId) TransportControl request"
        )
        test.mediaController.transportControls.skipToQueueItem(itemId)
        return TestStepStatus.STEP_PASS
    }
}

/**
 * Expects query to be either a timestamp to seek to, or a change in position (prepended with a '+'
 * to seek forward or a '-' to seek backwards, e.g. +30 will seek 30 seconds ahead). This step
 * checks if ACTION_SEEK_TO is supported and sends the seekTo() request. Returns STEP_FAIL if the
 * query isn't a Long (potentially prepended by '+' or '-') or if the current state is null,
 * returns STEP_PASS otherwise. For later steps to verify that playback is at the desired position,
 * this step initializes the TARGET_POSITION key in the Test's extras Bundle.
 */
class ConfigureSeekTo(override val test: Test, private val query: String) : TestStep {
    override val logTag = "${test.name}.CST"
    override fun execute(
            currState: PlaybackStateCompat?,
            currMetadata: MediaMetadataCompat?
    ): TestStepStatus {
        val currentTime = currState?.position
        if (currentTime == null) {
            test.testLogger(logTag, "Failed: Can't determine current position")
            return TestStepStatus.STEP_FAIL
        }
        var newTime = query.toLongOrNull()
        if (query == "" || newTime == null) {
            test.testLogger(logTag, "Failed: Couldn't parse query [$query]")
            return TestStepStatus.STEP_FAIL
        }
        if (query[0] == '+' || query[0] == '-') {
            newTime = currentTime + newTime * 1000
        } else {
            newTime *= 1000
        }

        checkActionSupported(currState, PlaybackStateCompat.ACTION_SEEK_TO)
        test.extras.putLong("TARGET_POSITION", newTime)
        test.testLogger(logTag, "Running: Sending seekTo($newTime) TransportControl request")
        test.mediaController.transportControls.seekTo(newTime)
        return TestStepStatus.STEP_PASS
    }
}

/**
 * PASS: metadata must not change, and state must be STATE_PLAYING
 * CONTINUE: null state, original state, transition states
 * FAIL: metadata changes, any other terminal state
 */
class WaitForPlaying(override val test: Test) : TestStep {
    override val logTag = "${test.name}.WFP"
    override fun execute(
            currState: PlaybackStateCompat?,
            currMetadata: MediaMetadataCompat?
    ): TestStepStatus {
        test.testLogger(
                logTag,
                "Comparing original metadata ${test.origMetadata.toBasicString()} to current "
                        + "metadata ${currMetadata.toBasicString()}"
        )
        // Metadata should not change for this step, but some apps "update" the Metadata with the
        // same media item.
        if (test.origMetadata != null && !test.origMetadata.isContentSameAs(currMetadata)) {
            test.testLogger(logTag, "Error: Metadata changed")
            return TestStepStatus.STEP_FAIL
        }

        return when {
            currState?.state == null -> {
                test.testLogger(logTag, "Warning: PlaybackState is null")
                TestStepStatus.STEP_CONTINUE
            }
            currState.state == PlaybackStateCompat.STATE_PLAYING -> {
                TestStepStatus.STEP_PASS
            }
            currState.state == test.origState?.state
                    || transitionStates.contains(currState.state) -> {
                // Sometimes apps "update" the Playback State without any changes or may enter an
                // unexpected transition state
                TestStepStatus.STEP_CONTINUE
            }
            else -> {
                // All terminal states other than STATE_PLAYING
                TestStepStatus.STEP_FAIL
            }
        }
    }
}

/**
 * PASS: Metadata must change. If metadata changes to a new media item then state must be
 *       STATE_PLAYING, if metadata just updates to the same media item then state must be the same
 *       as it originally was. In both cases, playback position must be at 0
 * CONTINUE: null state, original state, transition states
 * FAIL: metadata doesn't change, any other state where all pass conditions aren't met
 */
class WaitForSkip(override val test: Test) : TestStep {
    override val logTag = "${test.name}.WFS"
    override fun execute(
            currState: PlaybackStateCompat?,
            currMetadata: MediaMetadataCompat?
    ): TestStepStatus {
        var itemChanged = test.extras.getBoolean("METADATA_CHANGED")
        val stepTrigger = test.extras.getInt("STEP_TRIGGER")
        val isNewItem = !test.origMetadata.isContentSameAs(currMetadata)

        test.testLogger(
                logTag,
                "Comparing original metadata ${test.origMetadata.toBasicString()} to current "
                        + "metadata ${currMetadata.toBasicString()}"
        )
        // Metadata needs to change for this step, but it might "change" to the same item. There
        // must be at least one metadata update for the step to pass
        if (isNewItem) {
            // Skipped to new media item
            itemChanged = true
            test.testLogger(logTag, "Running: Metadata changed")
        } else if (stepTrigger == Test.METADATA_CHANGED) {
            // Skipped to same media item
            itemChanged = true
            test.testLogger(logTag, "Running: Metadata updated")
        }
        test.extras.putBoolean("METADATA_CHANGED", itemChanged)

        return when {
            currState?.state == null -> {
                test.testLogger(logTag, "Warning: PlaybackState is null")
                TestStepStatus.STEP_CONTINUE
            }
            (isNewItem && currState.state == PlaybackStateCompat.STATE_PLAYING)
                    || (!isNewItem && currState.state == test.origState?.state) -> {
                if (itemChanged && (abs(currState.position) <= Test.POSITION_LENIENCY)) {
                    // All conditions satisfied
                    TestStepStatus.STEP_PASS
                } else {
                    test.testLogger(
                            logTag,
                            "Running: Either metadata is unchanged or position is non-zero"
                    )
                    TestStepStatus.STEP_CONTINUE
                }
            }
            currState.state == test.origState?.state
                    || transitionStates.contains(currState.state) -> {
                // Sometimes apps "update" the Playback State without any changes or may enter an
                // unexpected transition state
                TestStepStatus.STEP_CONTINUE
            }
            else -> {
                // All terminal states other than STATE_PLAYING and STATE_PAUSED
                TestStepStatus.STEP_FAIL
            }
        }
    }
}

/**
 * PASS: metadata may change to null and state must be STATE_STOPPED or STATE_NONE
 * CONTINUE: null or original state
 * FAIL: any other state, or metadata changes to non-original, non-null metadata
 */
class WaitForStopped(override val test: Test) : TestStep {
    override val logTag = "${test.name}.WFS"
    override fun execute(
            currState: PlaybackStateCompat?,
            currMetadata: MediaMetadataCompat?
    ): TestStepStatus {
        test.testLogger(
                logTag,
                "Comparing original metadata ${test.origMetadata.toBasicString()} to current "
                        + "metadata ${currMetadata.toBasicString()}"
        )
        // Metadata may change to null, and some apps "update" the Metadata with the same media
        // item, but Metadata should not change to a different media item.
        if (currMetadata != null && test.origMetadata != null
                && !test.origMetadata.isContentSameAs(currMetadata)) {
            test.testLogger(logTag, "Error: Metadata changed")
            return TestStepStatus.STEP_FAIL
        }

        return when {
            currState?.state == null -> {
                test.testLogger(logTag, "Warning: PlaybackState is null")
                TestStepStatus.STEP_CONTINUE
            }
            currState.state == PlaybackStateCompat.STATE_NONE
                    || currState.state == PlaybackStateCompat.STATE_STOPPED -> {
                TestStepStatus.STEP_PASS
            }
            currState.state == test.origState?.state
                    || transitionStates.contains(currState.state) -> {
                // Sometimes apps "update" the Playback State without any changes or may enter an
                // unexpected transition state
                TestStepStatus.STEP_CONTINUE
            }
            else -> {
                // All terminal states other than STATE_NONE and STATE_STOPPED
                TestStepStatus.STEP_FAIL
            }
        }
    }
}

/**
 * PASS: metadata must not change and state must be STATE_PAUSED (if the test began with
 *       STATE_STOPPED, it is also acceptable to end with STATE_STOPPED)
 * CONTINUE: null state, original state, or transition state
 * FAIL: metadata changes or any other terminal state
 */
class WaitForPaused(override val test: Test) : TestStep {
    override val logTag = "${test.name}.WFP"
    override fun execute(
            currState: PlaybackStateCompat?,
            currMetadata: MediaMetadataCompat?
    ): TestStepStatus {
        test.testLogger(
                logTag,
                "Comparing original metadata ${test.origMetadata.toBasicString()} to current "
                        + "metadata ${currMetadata.toBasicString()}"
        )
        // Metadata should not change for this step, but some apps "update" the Metadata with the
        // same media item.
        if (test.origMetadata != null && !test.origMetadata.isContentSameAs(currMetadata)) {
            test.testLogger(logTag, "Error: Metadata changed")
            return TestStepStatus.STEP_FAIL
        }

        return when {
            currState?.state == null -> {
                test.testLogger(logTag, "Warning: PlaybackState is null")
                TestStepStatus.STEP_CONTINUE
            }
            currState.state == PlaybackStateCompat.STATE_PAUSED -> {
                TestStepStatus.STEP_PASS
            }
            currState.state == PlaybackStateCompat.STATE_STOPPED -> {
                val origState = test.origState
                if (origState == null || origState.state != PlaybackStateCompat.STATE_STOPPED) {
                    TestStepStatus.STEP_FAIL
                } else {
                    TestStepStatus.STEP_PASS
                }
            }
            currState.state == test.origState?.state
                    || transitionStates.contains(currState.state) -> {
                // Sometimes apps "update" the Playback State without any changes or may enter an
                // unexpected transition state
                TestStepStatus.STEP_CONTINUE
            }
            else -> {
                // All terminal states other than STATE_PAUSED and STATE_STOPPED
                TestStepStatus.STEP_FAIL
            }
        }
    }
}

/**
 * PASS: state must be STATE_PLAYING and playback position must be at the start of the media item
 * CONTINUE: null state, original state, STATE_PLAYING but non-zero playback position, transition
 *           states
 * FAIL: any other terminal state
 *
 * Note: No metadata checks
 */
class WaitForPlayingBeginning(override val test: Test) : TestStep {
    override val logTag = "${test.name}.WFPB"
    override fun execute(
            currState: PlaybackStateCompat?,
            currMetadata: MediaMetadataCompat?
    ): TestStepStatus {
        return when {
            currState?.state == null -> {
                test.testLogger(logTag, "Warning: PlaybackState is null")
                TestStepStatus.STEP_CONTINUE
            }
            currState.state == PlaybackStateCompat.STATE_PLAYING -> {
                if (abs(currState.position) < Test.POSITION_LENIENCY) {
                    TestStepStatus.STEP_PASS
                } else {
                    test.testLogger(logTag, "Running: Not at beginning")
                    TestStepStatus.STEP_CONTINUE
                }
            }
            currState.state == test.origState?.state
                    || transitionStates.contains(currState.state) -> {
                // Sometimes apps "update" the Playback State without any changes or may enter an
                // unexpected transition state
                TestStepStatus.STEP_CONTINUE
            }
            else -> {
                // All terminal states other than STATE_PLAYING
                TestStepStatus.STEP_FAIL
            }
        }
    }
}

/**
 * PASS: terminal state and playback position at target, metadata might change if target is outside
 *       the bounds of the media item duration (in which case state must be STATE_PLAYING)
 * CONTINUE: null state, transition state, terminal state but either incorrect playback position
 *           or not STATE_PLAYING for new media item
 * FAIL: metadata changes when target is within media item duration, any other state
 *
 * Note: This test issues a warning if the ending state is not the same as the original state (e.g.
 *       if the test starts with STATE_PLAYING, it is expected though not required that the test
 *       also ends in STATE_PLAYING).
 */
class WaitForTerminalAtTarget(override val test: Test) : TestStep {
    override val logTag = "${test.name}.WFTAT"
    override fun execute(
            currState: PlaybackStateCompat?,
            currMetadata: MediaMetadataCompat?
    ): TestStepStatus {
        val target = test.extras.getLong("TARGET_POSITION")
        val dur = test.origMetadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
        if (dur == null) {
            test.testLogger(logTag, "Failed: null original metadata")
            return TestStepStatus.STEP_FAIL
        }
        // Metadata might change for this step (if the seek position is outside the bounds of the
        // media item).
        val isNewItem = !test.origMetadata.isContentSameAs(currMetadata)
        if (test.origMetadata != null && isNewItem) {
            if (target < 0 || target > (dur - Test.POSITION_LENIENCY)) {
                test.testLogger(logTag, "Running: Media item ended, metadata changed")
            } else {
                test.testLogger(logTag, "Failed: Metadata changed")
                return TestStepStatus.STEP_FAIL
            }
        }

        return when {
            currState?.state == null -> {
                test.testLogger(logTag, "Continuing: null")
                TestStepStatus.STEP_CONTINUE
            }
            terminalStates.contains(currState.state) -> {
                val boundedTarget = when {
                    target <= 0L || target >= dur -> 0L
                    else -> target
                }
                return if (abs(currState.position - boundedTarget) < Test.POSITION_LENIENCY) {
                    if(!isNewItem) {
                        // valid end state and correct position
                        val origState = test.origState
                        if (origState != null && currState.state != origState.state) {
                            test.testLogger(
                                    logTag,
                                    "Warning: Step did not end with the same state,"
                                            + " ${playbackStateToName(currState.state)}, in which"
                                            + " it began, ${playbackStateToName(origState.state)}"
                            )
                        }
                        TestStepStatus.STEP_PASS
                    } else {
                        // if metadata changed, state must be playing
                        if(currState.state == PlaybackStateCompat.STATE_PLAYING) {
                            TestStepStatus.STEP_PASS
                        } else {
                            TestStepStatus.STEP_CONTINUE
                        }
                    }
                } else {
                    // valid end state, but incorrect position
                    test.testLogger(
                            logTag,
                            "Running: Incorrect position ${currState.position}"
                    )
                    TestStepStatus.STEP_CONTINUE
                }
            }
            transitionStates.contains(currState.state) -> {
                TestStepStatus.STEP_CONTINUE
            }
            else -> {
                TestStepStatus.STEP_FAIL
            }
        }
    }
}