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
        currentTest?.endTest()
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

                // Process received message
                val status = when (msg.what) {
                    STATE_CHANGED, METADATA_CHANGED, RUN_STEP -> {
                        extras.putInt("STEP_TRIGGER", msg.what)
                        steps[stepIndex].execute(
                                mediaController.playbackState,
                                mediaController.metadata
                        )
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

                // Process TestStep result
                when (status) {
                    TestStepStatus.STEP_PASS -> {
                        // Move to next step
                        ++stepIndex
                        // Run step
                        Message.obtain(this, RUN_STEP).sendToTarget()
                    }
                    TestStepStatus.STEP_CONTINUE -> {
                        // No op
                    }
                    TestStepStatus.STEP_FAIL -> {
                        testLogger(name, "Test failed")
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
        quit()
    }

    companion object {
        const val TIMED_OUT = 0
        const val RUN_STEP = 1
        const val STATE_CHANGED = 2
        const val METADATA_CHANGED = 3

        const val TEST_TIMEOUT = 5000L // 5 seconds
        const val POSITION_LENIENCY = 2000L // 2 seconds
    }
}

class TestOptionDetails(val name: String, val desc: String, val runTest: (query: String) -> Unit)

enum class TestStepStatus {
    STEP_PASS, STEP_CONTINUE, STEP_FAIL
}

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
        test.testLogger(logTag, "Running: Sending TransportControl request")
        test.mediaController.transportControls.play()
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
        test.testLogger(logTag, "Running: Sending TransportControl request")
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
        test.testLogger(logTag, "Running: Sending TransportControl request")
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
            test.testLogger(logTag, "Failed: Couldn't parse query [$query]")
            return TestStepStatus.STEP_FAIL
        }

        checkActionSupported(currState, PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM)
        test.extras.putBoolean("METADATA_CHANGED", false)
        test.testLogger(logTag, "Running: Sending TransportControl request")
        test.mediaController.transportControls.skipToQueueItem(itemId)
        return TestStepStatus.STEP_PASS
    }
}

/**
 * PASS: metadata must not change and state must be STATE_PLAYING or STATE_BUFFERING
 * CONTINUE: null or original state
 * FAIL: metadata changes or any other state
 */
class WaitForBufferingOrPlaying(override val test: Test) : TestStep {
    override val logTag = "${test.name}.WFBOP"
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
            test.testLogger(logTag, "Failed: Metadata changed")
            return TestStepStatus.STEP_FAIL
        }

        return when (currState?.state) {
            null -> {
                test.testLogger(logTag, "Warning: PlaybackState is null")
                TestStepStatus.STEP_CONTINUE
            }
            PlaybackStateCompat.STATE_BUFFERING -> {
                test.testLogger(logTag, "Passed: STATE_BUFFERING")
                TestStepStatus.STEP_PASS
            }
            PlaybackStateCompat.STATE_PLAYING -> {
                test.testLogger(logTag, "Passed: STATE_PLAYING")
                TestStepStatus.STEP_PASS
            }
            test.origState?.state -> {
                // Sometimes apps "update" the Playback State without any changes
                test.testLogger(logTag, "Continuing: ${playbackStateToName(currState.state)}")
                TestStepStatus.STEP_CONTINUE
            }
            else -> {
                test.testLogger(logTag, "Failed: ${playbackStateToName(currState.state)}")
                TestStepStatus.STEP_FAIL
            }
        }
    }
}

/**
 * PASS: metadata must not change and state must be STATE_PLAYING
 * CONTINUE: null or original state
 * FAIL: metadata changes or any other state
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
            test.testLogger(logTag, "Failed: Metadata changed")
            return TestStepStatus.STEP_FAIL
        }

        return when (currState?.state) {
            null -> {
                test.testLogger(logTag, "Warning: PlaybackState is null")
                TestStepStatus.STEP_CONTINUE
            }
            PlaybackStateCompat.STATE_PLAYING -> {
                test.testLogger(logTag, "Passed: STATE_PLAYING")
                TestStepStatus.STEP_PASS
            }
            test.origState?.state -> {
                // Sometimes apps "update" the Playback State without any changes
                test.testLogger(logTag, "Continuing: ${playbackStateToName(currState.state)}")
                TestStepStatus.STEP_CONTINUE
            }
            else -> {
                test.testLogger(logTag, "Failed: ${playbackStateToName(currState.state)}")
                TestStepStatus.STEP_FAIL
            }
        }
    }
}

/**
 * PASS: state must be STATE_PLAYING and playback position must be at the start of the media item
 * CONTINUE: null or original state, STATE_PLAYING but non-zero playback position
 * FAIL: any other state
 *
 * Note: No metadata checks
 */
class WaitForPlayingBeginning(override val test: Test) : TestStep {
    override val logTag = "${test.name}.WFPB"
    override fun execute(
            currState: PlaybackStateCompat?,
            currMetadata: MediaMetadataCompat?
    ): TestStepStatus {
        return when (currState?.state) {
            null -> {
                test.testLogger(logTag, "Continuing: null")
                TestStepStatus.STEP_CONTINUE
            }
            PlaybackStateCompat.STATE_PLAYING -> {
                if (abs(currState.position) < Test.POSITION_LENIENCY) {
                    test.testLogger(logTag, "Passed: STATE_PLAYING")
                    TestStepStatus.STEP_PASS
                } else {
                    test.testLogger(logTag, "Continuing: Playing, but not at beginning")
                    TestStepStatus.STEP_CONTINUE
                }
            }
            test.origState?.state -> {
                // Sometimes apps "update" the Playback State without any changes
                test.testLogger(logTag, "Continuing: ${playbackStateToName(currState.state)}")
                TestStepStatus.STEP_CONTINUE
            }
            else -> {
                test.testLogger(logTag, "Failed: ${playbackStateToName(currState.state)}")
                TestStepStatus.STEP_FAIL
            }
        }
    }
}

/**
 * PASS: state must be the same as when the test began and playback position must be at the start
 *       of the media item
 * CONTINUE: null state, original state but non-zero playback position
 * FAIL: any other state
 *
 * Note: No metadata checks
 */
class WaitForOriginalBeginning(override val test: Test) : TestStep {
    override val logTag = "${test.name}.WFOB"
    override fun execute(
            currState: PlaybackStateCompat?,
            currMetadata: MediaMetadataCompat?
    ): TestStepStatus {
        return when (currState?.state) {
            null -> {
                test.testLogger(logTag, "Continuing: null")
                TestStepStatus.STEP_CONTINUE
            }
            test.origState?.state -> {
                if (abs(currState.position) < Test.POSITION_LENIENCY) {
                    test.testLogger(logTag, "Passed: ${playbackStateToName(currState.state)}")
                    TestStepStatus.STEP_PASS
                } else {
                    test.testLogger(logTag, "Continuing: ${playbackStateToName(currState.state)}"
                            + ", but not at beginning")
                    TestStepStatus.STEP_CONTINUE
                }
            }
            else -> {
                test.testLogger(logTag, "Failed: ${playbackStateToName(currState.state)}")
                TestStepStatus.STEP_FAIL
            }
        }
    }
}

/**
 * PASS: metadata must change and playback position must be at the start of the media item
 * CONTINUE: playback position is 0 but metadata hasn't changed, null or original state,
 *           STATE_SKIPPING_TO_*, STATE_BUFFERING, STATE_PLAYING
 * FAIL: any other state
 */
class WaitForSkipPositionReset(override val test: Test) : TestStep {
    override val logTag = "${test.name}.WFSPR"
    override fun execute(
            currState: PlaybackStateCompat?,
            currMetadata: MediaMetadataCompat?
    ): TestStepStatus {
        var metadataChanged = test.extras.getBoolean("METADATA_CHANGED")
        val stepTrigger = test.extras.getInt("STEP_TRIGGER")

        test.testLogger(
                logTag,
                "Comparing original metadata ${test.origMetadata.toBasicString()} to current "
                        + "metadata ${currMetadata.toBasicString()}"
        )
        // Metadata needs to change for this step, but it might "change" to the same item. There
        // must be at least one metadata update for the step to pass
        if (test.origMetadata != null && !test.origMetadata.isContentSameAs(currMetadata)) {
            // Skipped to new media item
            metadataChanged = true
            test.testLogger(logTag, "Running: Metadata changed")
        } else if (stepTrigger == Test.METADATA_CHANGED) {
            // Skipped to same media item
            metadataChanged = true
            test.testLogger(logTag, "Running: Metadata updated")
        }
        test.extras.putBoolean("METADATA_CHANGED", metadataChanged)

        val validStates = intArrayOf(
                PlaybackStateCompat.STATE_SKIPPING_TO_NEXT,
                PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS,
                PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM,
                PlaybackStateCompat.STATE_BUFFERING,
                PlaybackStateCompat.STATE_PLAYING
        )

        return when {
            currState?.state == null -> {
                test.testLogger(logTag, "Continuing: null")
                TestStepStatus.STEP_CONTINUE
            }
            abs(currState.position) < Test.POSITION_LENIENCY -> {
                if (metadataChanged) {
                    test.testLogger(logTag, "Passed: Position reset")
                    TestStepStatus.STEP_PASS
                } else {
                    test.testLogger(logTag, "Continuing: Position reset but old metadata")
                    TestStepStatus.STEP_CONTINUE
                }
            }
            validStates.contains(currState.state) -> {
                test.testLogger(logTag, "Continuing: ${playbackStateToName(currState.state)}")
                TestStepStatus.STEP_CONTINUE
            }
            currState.state == test.origState?.state -> {
                // Sometimes apps "update" the Playback State without any changes
                test.testLogger(logTag, "Continuing: ${playbackStateToName(currState.state)}")
                TestStepStatus.STEP_CONTINUE
            }
            else -> {
                test.testLogger(logTag, "Failed: ${playbackStateToName(currState.state)}")
                TestStepStatus.STEP_FAIL
            }
        }
    }
}