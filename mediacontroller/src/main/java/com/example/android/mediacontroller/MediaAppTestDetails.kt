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

import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat

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
                    RUN_STEP -> {
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
                        ++stepIndex
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
                Message.obtain(handler, RUN_STEP, state).sendToTarget()
            }

            override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
                Message.obtain(handler, RUN_STEP, metadata).sendToTarget()
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
        const val RUN_STEP = 0
        const val TIMED_OUT = 1

        const val TEST_TIMEOUT = 5000L // 5 seconds
    }
}

class TestOptionDetails(val name: String, val desc: String, val runTest: (query: String?) -> Unit)

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
        } else if (state.actions and action == 0L) {
            test.testLogger(logTag, "Warning: ${actionToString(action)} is not supported")
        }
    }
}

// "Configure" steps will:
// 1. (if applicable) parse the query text into a value usable by the transport control request
// 2. Check if the action to be tested is marked as supported
// 3. Send the appropriate transport control request
class ConfigurePlay(override val test: Test) : TestStep {
    override val logTag = "${test.name}.CPS"
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

class WaitForBufferingOrPlaying(override val test: Test) : TestStep {
    override val logTag = "${test.name}.WFBOPS"
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
                // Update the original state for the next step
                test.origState = currState
                test.testLogger(logTag, "Passed: STATE_BUFFERING")
                TestStepStatus.STEP_PASS
            }
            PlaybackStateCompat.STATE_PLAYING -> {
                // Update the original state for the next step
                test.origState = currState
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

class WaitForPlaying(override val test: Test) : TestStep {
    override val logTag = "${test.name}.WFPS"
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
                test.origState = currState
                test.testLogger(logTag, "Passed: STATE_PLAYING")
                TestStepStatus.STEP_PASS
            }
            test.origState?.state -> {
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