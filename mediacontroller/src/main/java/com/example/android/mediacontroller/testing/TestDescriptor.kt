package com.example.android.mediacontroller.testing

import android.content.Context
import android.os.Build
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.widget.Toast
import com.example.android.mediacontroller.*

class TestDescriptor {

    var testList: List<TestOptionDetails>? = null
    var testSuites: List<MediaAppTestSuite>? = null

    fun setupTests(context: Context, mediaController: MediaControllerCompat,
                   mediaAppDetails: MediaAppDetails?, mediaBrowser: MediaBrowserCompat?) {

        /**
         * Tests the play() transport control. The test can start in any state, might enter a
         * transition state, but must eventually end in STATE_PLAYING. The test will fail for
         * any terminal state other than the starting state and STATE_PLAYING. The test
         * will also fail if the metadata changes unless the test began with null metadata.
         */
        val playTest = TestOptionDetails(
            0,
            context.getString(R.string.play_test_title),
            context.getString(R.string.play_test_desc),
            TestResult.NONE,
            Test.NO_LOGS,
            false
        ) { _, callback, testId -> runPlayTest(testId, mediaController, callback) }

        /**
         * Tests the playFromSearch() transport control. The test can start in any state, might
         * enter a transition state, but must eventually end in STATE_PLAYING with playback
         * position at 0. The test will fail for any terminal state other than the starting state
         * and STATE_PLAYING. This test does not perform any metadata checks.
         */
        val playFromSearch = TestOptionDetails(
            1,
            context.getString(R.string.play_search_test_title),
            context.getString(R.string.play_search_test_desc),
            TestResult.NONE,
            Test.NO_LOGS,
            false
        ) { query, callback, testId ->
            runPlayFromSearchTest(
                testId, query, mediaController, callback
            )
        }

        /**
         * Tests the playFromMediaId() transport control. The test can start in any state, might
         * enter a transition state, but must eventually end in STATE_PLAYING with playback
         * position at 0. The test will fail for any terminal state other than the starting state
         * and STATE_PLAYING. The test will also fail if query is empty/null. This test does not
         * perform any metadata checks.
         */
        val playFromMediaId = TestOptionDetails(
            2,
            context.getString(R.string.play_media_id_test_title),
            context.getString(R.string.play_media_id_test_desc),
            TestResult.NONE,
            Test.NO_LOGS,
            true
        ) { query, callback, testId ->
            runPlayFromMediaIdTest(
                testId, query, mediaController, callback
            )
        }

        /**
         * Tests the playFromUri() transport control. The test can start in any state, might
         * enter a transition state, but must eventually end in STATE_PLAYING with playback
         * position at 0. The test will fail for any terminal state other than the starting state
         * and STATE_PLAYING. The test will also fail if query is empty/null. This test does not
         * perform any metadata checks.
         */
        val playFromUri = TestOptionDetails(
            3,
            context.getString(R.string.play_uri_test_title),
            context.getString(R.string.play_uri_test_desc),
            TestResult.NONE,
            Test.NO_LOGS,
            true
        ) { query, callback, testId ->
            runPlayFromUriTest(
                testId, query, mediaController, callback
            )
        }

        /**
         * Tests the pause() transport control. The test can start in any state, but must end in
         * STATE_PAUSED (but STATE_STOPPED is also okay if that is the state the test started with).
         * The test will fail for any terminal state other than the starting state, STATE_PAUSED,
         * and STATE_STOPPED. The test will also fail if the metadata changes unless the test began
         * with null metadata.
         */
        val pauseTest = TestOptionDetails(
            4,
            context.getString(R.string.pause_test_title),
            context.getString(R.string.pause_test_desc),
            TestResult.NONE,
            Test.NO_LOGS,
            false
        ) { _, callback, testId -> runPauseTest(testId, mediaController, callback) }

        /**
         * Tests the stop() transport control. The test can start in any state, but must end in
         * STATE_STOPPED or STATE_NONE. The test will fail for any terminal state other than the
         * starting state, STATE_STOPPED, and STATE_NONE. The test will also fail if the metadata
         * changes to a non-null media item different from the original media item.
         */
        val stopTest = TestOptionDetails(
            5,
            context.getString(R.string.stop_test_title),
            context.getString(R.string.stop_test_desc),
            TestResult.NONE,
            Test.NO_LOGS,
            false
        ) { _, callback, testId -> runStopTest(testId, mediaController, callback) }

        /**
         * Tests the skipToNext() transport control. The test can start in any state, might
         * enter a transition state, but must eventually end in STATE_PLAYING with the playback
         * position at 0 if a new media item is started or in the starting state if the media item
         * doesn't change. The test will fail for any terminal state other than the starting state
         * and STATE_PLAYING. The metadata must change, but might just "change" to be the same as
         * the original metadata (e.g. if the next media item is the same as the current one); the
         * test will not pass if the metadata doesn't get updated at some point.
         */
        val skipToNextTest = TestOptionDetails(
            6,
            context.getString(R.string.skip_next_test_title),
            context.getString(R.string.skip_next_test_desc),
            TestResult.NONE,
            Test.NO_LOGS,
            false
        ) { _, callback, testId ->
            runSkipToNextTest(
                testId, mediaController, callback
            )
        }

        /**
         * Tests the skipToPrevious() transport control. The test can start in any state, might
         * enter a transition state, but must eventually end in STATE_PLAYING with the playback
         * position at 0 if a new media item is started or in the starting state if the media item
         * doesn't change. The test will fail for any terminal state other than the starting state
         * and STATE_PLAYING. The metadata must change, but might just "change" to be the same as
         * the original metadata (e.g. if the previous media item is the same as the current one);
         * the test will not pass if the metadata doesn't get updated at some point.
         */
        val skipToPrevTest = TestOptionDetails(
            7,
            context.getString(R.string.skip_prev_test_title),
            context.getString(R.string.skip_prev_test_desc),
            TestResult.NONE,
            Test.NO_LOGS,
            false
        ) { _, callback, testId ->
            runSkipToPrevTest(
                testId, mediaController, callback
            )
        }

        /**
         * Tests the skipToQueueItem() transport control. The test can start in any state, might
         * enter a transition state, but must eventually end in STATE_PLAYING with the playback
         * position at 0 if a new media item is started or in the starting state if the media item
         * doesn't change. The test will fail for any terminal state other than the starting state
         * and STATE_PLAYING. The metadata must change, but might just "change" to be the same as
         * the original metadata (e.g. if the next media item is the same as the current one); the
         * test will not pass if the metadata doesn't get updated at some point.
         */
        val skipToItemTest = TestOptionDetails(
            8,
            context.getString(R.string.skip_item_test_title),
            context.getString(R.string.skip_item_test_desc),
            TestResult.NONE,
            Test.NO_LOGS,
            true
        ) { query, callback, testId ->
            runSkipToItemTest(
                testId, query, mediaController, callback
            )
        }

        /**
         * Tests the seekTo() transport control. The test can start in any state, might enter a
         * transition state, but must eventually end in a terminal state with playback position at
         * the requested timestamp. While not required, it is expected that the test will end in
         * the same state as it started. Metadata might change for this test if the requested
         * timestamp is outside the bounds of the current media item. The query should either be
         * a position in seconds or a change in position (number of seconds prepended by '+' to go
         * forward or '-' to go backwards). The test will fail if the query can't be parsed to a
         * Long.
         */
        val seekToTest = TestOptionDetails(
            9,
            context.getString(R.string.seek_test_title),
            context.getString(R.string.seek_test_desc),
            TestResult.NONE,
            Test.NO_LOGS,
            true
        ) { query, callback, testId ->
            runSeekToTest(
                testId, query, mediaController, callback
            )
        }

        /**
         * Automotive and Auto shared tests
         */
        val browseTreeDepthTest = TestOptionDetails(
            10,
            context.getString(R.string.browse_tree_depth_test_title),
            context.getString(R.string.browse_tree_depth_test_desc),
            TestResult.NONE,
            Test.NO_LOGS,
            false
        ) { _, callback, testId ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runBrowseTreeDepthTest(
                    testId, mediaController, mediaBrowser, callback
                )
            } else {
                Toast.makeText(
                    context,
                    "This test requires minSDK 24",
                    Toast.LENGTH_SHORT
                )
                    .show()
            }
        }

        val mediaArtworkTest = TestOptionDetails(
            11,
            context.getString(R.string.media_artwork_test_title),
            context.getString(R.string.media_artwork_test_desc),
            TestResult.NONE,
            Test.NO_LOGS,
            false
        ) { _, callback, testId ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runMediaArtworkTest(
                    testId, mediaController, mediaBrowser, callback
                )
            } else {
                Toast.makeText(
                    context,
                    "This test requires minSDK 24",
                    Toast.LENGTH_SHORT
                )
                    .show()
            }
        }

        val contentStyleTest = TestOptionDetails(
            12,
            context.getString(R.string.content_style_test_title),
            context.getString(R.string.content_style_test_desc),
            TestResult.NONE,
            Test.NO_LOGS,
            false
        ) { _, callback, testId ->
            runContentStyleTest(
                testId, mediaController, mediaBrowser, callback
            )
        }

        val customActionIconTypeTest = TestOptionDetails(
            13,
            context.getString(R.string.custom_actions_icon_test_title),
            context.getString(R.string.custom_actions_icon_test_desc),
            TestResult.NONE,
            Test.NO_LOGS,
            false
        ) { _, callback, testId ->
            runCustomActionIconTypeTest(
                testId, context, mediaController, mediaAppDetails!!, callback
            )
        }

        val supportsSearchTest = TestOptionDetails(
            14,
            context.getString(R.string.search_supported_test_title),
            context.getString(R.string.search_supported_test_desc),
            TestResult.NONE,
            Test.NO_LOGS,
            false
        ) { _, callback, testId ->
            runSearchTest(
                testId, mediaController, mediaBrowser, callback
            )
        }

        val initialPlaybackStateTest = TestOptionDetails(
            15,
            context.getString(R.string.playback_state_test_title),
            context.getString(R.string.playback_state_test_desc),
            TestResult.NONE,
            Test.NO_LOGS,
            false
        ) { _, callback, testId ->
            runInitialPlaybackStateTest(
                testId, mediaController, callback
            )
        }

        /**
         * Automotive specific tests
         */
        val browseTreeStructureTest = TestOptionDetails(
            16,
            context.getString(R.string.browse_tree_structure_test_title),
            context.getString(R.string.browse_tree_structure_test_desc),
            TestResult.NONE,
            Test.NO_LOGS,
            false
        ) { _, callback, testId ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runBrowseTreeStructureTest(
                    testId, mediaController, mediaBrowser, callback
                )
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.test_error_minsdk),
                    Toast.LENGTH_SHORT
                )
                    .show()
            }
        }

        val preferenceTest = TestOptionDetails(
            17,
            context.getString(R.string.preference_activity_test_title),
            context.getString(R.string.preference_activity_test_desc),
            TestResult.NONE,
            Test.NO_LOGS,
            false
        ) { _, callback, testId ->
            runPreferenceTest(
                testId, mediaController, mediaAppDetails, context.packageManager, callback
            )
        }

        val errorResolutionDataTest = TestOptionDetails(
            18,
            context.getString(R.string.error_resolution_test_title),
            context.getString(R.string.error_resolution_test_desc),
            TestResult.NONE,
            Test.NO_LOGS,
            false
        ) { _, callback, testId ->
            runErrorResolutionDataTest(
                testId, mediaController, callback
            )
        }

        val launcherTest = TestOptionDetails(
            19,
            context.getString(R.string.launcher_intent_test_title),
            context.getString(R.string.launcher_intent_test_desc),
            TestResult.NONE,
            Test.NO_LOGS,
            false
        ) { _, callback, testId ->
            runLauncherTest(
                testId, mediaController, mediaAppDetails, context.packageManager, callback
            )
        }

        val basicTests = arrayOf(
            playFromSearch,
            playFromMediaId,
            playFromUri,
            playTest,
            pauseTest,
            stopTest,
            skipToNextTest,
            skipToPrevTest,
            skipToItemTest,
            seekToTest
        )

        val commonTests = arrayOf(
            browseTreeDepthTest,
            mediaArtworkTest,
            //TODO FIX contentStyleTest,
            customActionIconTypeTest,
            //TODO: FIX supportsSearchTest,
            initialPlaybackStateTest
        )

        val automotiveTests = arrayOf(
            browseTreeStructureTest,
            preferenceTest,
            errorResolutionDataTest,
            launcherTest
        )

        var testList = basicTests
        var testSuites: ArrayList<MediaAppTestSuite> = ArrayList()

        val basicTestSuite = MediaAppTestSuite("Basic Tests", "Basic media tests.", basicTests)
        testSuites.add(basicTestSuite)
        if (mediaAppDetails?.supportsAuto == true || mediaAppDetails?.supportsAutomotive == true) {
            testList += commonTests
            val autoTestSuite = MediaAppTestSuite("Auto Tests",
                "Includes support for android auto tests.", testList)
            testSuites.add(autoTestSuite)
        }
        if (mediaAppDetails?.supportsAutomotive == true) {
            testList += automotiveTests
            val automotiveTestSuite = MediaAppTestSuite("Automotive Tests",
                "Includes support for Android automotive tests.", testList)
            testSuites.add(automotiveTestSuite)
        }
        this.testList = testList.asList()
        this.testSuites = testSuites
    }
}