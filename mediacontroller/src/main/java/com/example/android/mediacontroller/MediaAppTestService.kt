package com.example.android.mediacontroller

import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.*
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.android.synthetic.main.media_test_suites.*
import kotlinx.android.synthetic.main.media_tests.*

// TODO: Remove all context-related code outside of this service
class MediaAppTestService : Service() {

    private val TAG = "MediaAppTestService"
    private val NOTIFICATION_CHANNEL_ID = "MediaAppTestService"
    private val NOTIFICATION_ID = 1001

    private var mediaAppDetails: MediaAppDetails? = null
    private var mediaController: MediaControllerCompat? = null
    private var mediaBrowser: MediaBrowserCompat? = null

    private var printLogsFormatted: Boolean = true
    private var callback: ICallback? = null
    private var testList: List<TestOptionDetails>? = null
    private var testSuites: List<MediaAppTestSuite>? = null
    private var context: Context? = null

    private val binder = TestServiceBinder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // To make sure the service keeps running
        startForeground(NOTIFICATION_ID, createNotification())

        Log.d(TAG, "onStartCommand")
        Log.d(TAG, "intent: $intent")
        if (intent != null) {
            Log.d(TAG, "intent string: " + intent.getStringExtra("string"))
            Log.d(TAG, "intent string: " + intent.getLongExtra("long", 0))
        }


        showToast("done!")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    override fun onCreate() {
        super.onCreate()

        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onDestroy() {
        super.onDestroy()

        mediaController?.run {
            unregisterCallback(controllerCallback)
            currentTest?.endTest()
        }
        mediaController = null

        mediaBrowser?.let {
            if (it.isConnected) {
                it.disconnect()
            }
        }
        mediaBrowser = null

        stopForeground(true)
    }

    inner class TestServiceBinder : Binder() {
        fun getService() : MediaAppTestService {
            return this@MediaAppTestService
        }
    }

    fun getMediaAppDetails() = mediaAppDetails

    fun setupMedia(mediaAppDetails: MediaAppDetails) {
        when {
            mediaAppDetails.componentName != null -> {
                mediaBrowser = MediaBrowserCompat(this, mediaAppDetails.componentName,
                        object : MediaBrowserCompat.ConnectionCallback() {
                            override fun onConnected() {
                                callback?.let {
                                    it.onConnected()
                                }
                                setupMediaController(true)
                            }

                            override fun onConnectionSuspended() {
                                callback?.let {
                                    it.onConnectionSuspended(mediaAppDetails.appName)
                                }
                            }

                            override fun onConnectionFailed() {
                                callback?.let {
                                    it.onConnectionFailed(mediaAppDetails.appName,
                                            mediaAppDetails.componentName)
                                }
                            }
                        }, null).apply { connect() }
            }
            mediaAppDetails.sessionToken != null -> setupMediaController(false)
        }
    }

    fun setupMediaController(useTokenFromBrowser: Boolean) {
        try {
            val token: MediaSessionCompat.Token
            // setupMediaController() is only called either immediately after the mediaBrowser is
            // connected or if mediaAppDetails contains a sessionToken.
            if (useTokenFromBrowser) {
                val browser = mediaBrowser
                if (browser != null) {
                    token = browser.sessionToken
                } else {
                    if (callback != null) {
                        callback!!.onSetupMediaControllerError(getString(
                                R.string.setup_media_controller_error_hint,
                                "MediaBrowser"
                        ))
                    } else {
                        Log.e(TAG, getString(
                                R.string.setup_media_controller_error_msg,
                                "MediaBrowser"
                        ))
                    }
                    return
                }
            } else {
                val appDetails = mediaAppDetails
                if (appDetails != null) {
                    token = appDetails.sessionToken
                } else {
                    if (callback != null) {
                        callback!!.onSetupMediaControllerError(getString(
                                R.string.setup_media_controller_error_hint,
                                "MediaAppDetails"
                        ))
                    } else {
                        Log.e(TAG, getString(
                                R.string.setup_media_controller_error_msg,
                                "MediaAppDetails"
                        ))
                    }
                    return
                }
            }

            mediaController = MediaControllerCompat(this, token).also {
                it.registerCallback(controllerCallback)

                // Force update on connect
                logCurrentController(it)
            }

            // Setup tests once media controller is connected
            setupTests()
        } catch (remoteException: RemoteException) {
            showToast(getString(R.string.media_controller_failed_msg))
        }
    }

    fun setPrintLogsFormatted(printLogsFormatted: Boolean) {
        this.printLogsFormatted = printLogsFormatted
    }

    fun getPrintLogsFormatted(): Boolean = printLogsFormatted

    fun logCurrentController(controller: MediaControllerCompat? = mediaController) {
        if (controller == null) {
            showToast("Null MediaController")
            return
        }

        controllerCallback.run {
            onPlaybackStateChanged(controller.playbackState)
            onMetadataChanged(controller.metadata)
            onRepeatModeChanged(controller.repeatMode)
            onShuffleModeChanged(controller.shuffleMode)
            onQueueTitleChanged(controller.queueTitle)
            onQueueChanged(controller.queue)
        }
    }

    private fun showToast(message: String) {
        Log.d(TAG, "showToast: $applicationContext")
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }

    fun isSuiteRunning(): Boolean {
        testSuites?.let {
            for (test in it) {
                if (test.suiteIsRunning()) {
                    return true
                }
            }
        }
        return false
    }

    fun handleIntent(intent: Intent) {
        if (intent == null) {
            return
        }

        mediaBrowser?.let {
            if (it.isConnected) {
                it.disconnect()
            }
        }

        val data = intent.data
        val appPackageName: String? = when {
            data != null -> data.host
            intent.hasExtra(PACKAGE_NAME_EXTRA) -> intent.getStringExtra(PACKAGE_NAME_EXTRA)
            else -> null
        }

        // Get new MediaAppDetails object from intent extras if present (otherwise keep current
        // MediaAppDetails object)
        val extras = intent.extras
        val hasAppDetailsExtra = extras?.containsKey(APP_DETAILS_EXTRA) ?: false
        if (hasAppDetailsExtra) {
            mediaAppDetails = extras.getParcelable(APP_DETAILS_EXTRA)
        }

        // Update MediaAppDetails object if needed (the if clause after the || handles the case when
        // the object has already been set up before, but the Intent contains details for a
        // different app)
        val appDetails = mediaAppDetails
        if ((appDetails == null && appPackageName != null)
                || (appDetails != null && appPackageName != appDetails.packageName)) {
            val serviceInfo = MediaAppDetails.findServiceInfo(appPackageName, packageManager)
            if (serviceInfo != null) {
                mediaAppDetails = MediaAppDetails(serviceInfo, packageManager, resources)
            }
        } else {
            Toast.makeText(
                    applicationContext,
                    getString(R.string.media_app_details_update_failed),
                    Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupTests() {
        // setupTests() should only be called after the mediaController is connected, so this
        // should never enter the if block
        val controller = mediaController
        if (controller == null) {
            Log.e(TAG, getString(R.string.setup_tests_error_msg))
            showToast(getString(R.string.setup_tests_error_msg))
            return
        }

        /**
         * Tests the play() transport control. The test can start in any state, might enter a
         * transition state, but must eventually end in STATE_PLAYING. The test will fail for
         * any terminal state other than the starting state and STATE_PLAYING. The test
         * will also fail if the metadata changes unless the test began with null metadata.
         */
        val playTest = TestOptionDetails(
                0,
                getString(R.string.play_test_title),
                getString(R.string.play_test_desc),
                TestResult.NONE,
                Test.NO_LOGS,
                false
        ) { _, callback, testId -> runPlayTest(testId, controller, callback) }

        /**
         * Tests the playFromSearch() transport control. The test can start in any state, might
         * enter a transition state, but must eventually end in STATE_PLAYING with playback
         * position at 0. The test will fail for any terminal state other than the starting state
         * and STATE_PLAYING. This test does not perform any metadata checks.
         */
        val playFromSearch = TestOptionDetails(
                1,
                getString(R.string.play_search_test_title),
                getString(R.string.play_search_test_desc),
                TestResult.NONE,
                Test.NO_LOGS,
                false
        ) { query, callback, testId ->
            runPlayFromSearchTest(
                    testId, query, controller, callback)
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
                getString(R.string.play_media_id_test_title),
                getString(R.string.play_media_id_test_desc),
                TestResult.NONE,
                Test.NO_LOGS,
                true
        ) { query, callback, testId ->
            runPlayFromMediaIdTest(
                    testId, query, controller, callback)
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
                getString(R.string.play_uri_test_title),
                getString(R.string.play_uri_test_desc),
                TestResult.NONE,
                Test.NO_LOGS,
                true
        ) { query, callback, testId ->
            runPlayFromUriTest(
                    testId, query, controller, callback)
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
                getString(R.string.pause_test_title),
                getString(R.string.pause_test_desc),
                TestResult.NONE,
                Test.NO_LOGS,
                false
        ) { _, callback, testId -> runPauseTest(testId, controller, callback) }

        /**
         * Tests the stop() transport control. The test can start in any state, but must end in
         * STATE_STOPPED or STATE_NONE. The test will fail for any terminal state other than the
         * starting state, STATE_STOPPED, and STATE_NONE. The test will also fail if the metadata
         * changes to a non-null media item different from the original media item.
         */
        val stopTest = TestOptionDetails(
                5,
                getString(R.string.stop_test_title),
                getString(R.string.stop_test_desc),
                TestResult.NONE,
                Test.NO_LOGS,
                false
        ) { _, callback, testId -> runStopTest(testId, controller, callback) }

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
                getString(R.string.skip_next_test_title),
                getString(R.string.skip_next_test_desc),
                TestResult.NONE,
                Test.NO_LOGS,
                false
        ) { _, callback, testId ->
            runSkipToNextTest(
                    testId, controller, callback)
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
                getString(R.string.skip_prev_test_title),
                getString(R.string.skip_prev_test_desc),
                TestResult.NONE,
                Test.NO_LOGS,
                false
        ) { _, callback, testId ->
            runSkipToPrevTest(
                    testId, controller, callback)
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
                getString(R.string.skip_item_test_title),
                getString(R.string.skip_item_test_desc),
                TestResult.NONE,
                Test.NO_LOGS,
                true
        ) { query, callback, testId ->
            runSkipToItemTest(
                    testId, query, controller, callback)
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
                getString(R.string.seek_test_title),
                getString(R.string.seek_test_desc),
                TestResult.NONE,
                Test.NO_LOGS,
                true
        ) { query, callback, testId ->
            runSeekToTest(
                    testId, query, controller, callback)
        }

        /**
         * Automotive and Auto shared tests
         */
        val browseTreeDepthTest = TestOptionDetails(
                10,
                getString(R.string.browse_tree_depth_test_title),
                getString(R.string.browse_tree_depth_test_desc),
                TestResult.NONE,
                Test.NO_LOGS,
                false
        ) { _, callback, testId ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runBrowseTreeDepthTest(
                        testId, controller, mediaBrowser, callback)
            } else {
                Toast.makeText(
                        applicationContext,
                        "This test requires minSDK 24",
                        Toast.LENGTH_SHORT)
                        .show()
            }
        }

        val mediaArtworkTest = TestOptionDetails(
                11,
                getString(R.string.media_artwork_test_title),
                getString(R.string.media_artwork_test_desc),
                TestResult.NONE,
                Test.NO_LOGS,
                false
        ) { _, callback, testId ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runMediaArtworkTest(
                        testId, controller, mediaBrowser, callback)
            } else {
                Toast.makeText(
                        applicationContext,
                        "This test requires minSDK 24",
                        Toast.LENGTH_SHORT)
                        .show()
            }
        }

        val contentStyleTest = TestOptionDetails(
                12,
                getString(R.string.content_style_test_title),
                getString(R.string.content_style_test_desc),
                TestResult.NONE,
                Test.NO_LOGS,
                false
        ) { _, callback, testId ->
            runContentStyleTest(
                    testId, controller, mediaBrowser, callback)
        }

        val customActionIconTypeTest = TestOptionDetails(
                13,
                getString(R.string.custom_actions_icon_test_title),
                getString(R.string.custom_actions_icon_test_desc),
                TestResult.NONE,
                Test.NO_LOGS,
                false
        ) { _, callback, testId ->
            runCustomActionIconTypeTest(
                    testId, applicationContext, controller, mediaAppDetails, callback)
        }

        val supportsSearchTest = TestOptionDetails(
                14,
                getString(R.string.search_supported_test_title),
                getString(R.string.search_supported_test_desc),
                TestResult.NONE,
                Test.NO_LOGS,
                false
        ) { _, callback, testId ->
            runSearchTest(
                    testId, controller, mediaBrowser, callback)
        }

        val initialPlaybackStateTest = TestOptionDetails(
                15,
                getString(R.string.playback_state_test_title),
                getString(R.string.playback_state_test_desc),
                TestResult.NONE,
                Test.NO_LOGS,
                false
        ) { _, callback, testId ->
            runInitialPlaybackStateTest(
                    testId, controller, callback)
        }

        /**
         * Automotive specific tests
         */
        val browseTreeStructureTest = TestOptionDetails(
                16,
                getString(R.string.browse_tree_structure_test_title),
                getString(R.string.browse_tree_structure_test_desc),
                TestResult.NONE,
                Test.NO_LOGS,
                false
        ) { _, callback, testId ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runBrowseTreeStructureTest(
                        testId, controller, mediaBrowser, callback)
            } else {
                Toast.makeText(
                        applicationContext,
                        getString(R.string.test_error_minsdk),
                        Toast.LENGTH_SHORT)
                        .show()
            }
        }

        val preferenceTest = TestOptionDetails(
                17,
                getString(R.string.preference_activity_test_title),
                getString(R.string.preference_activity_test_desc),
                TestResult.NONE,
                Test.NO_LOGS,
                false
        ) { _, callback, testId ->
            runPreferenceTest(
                    testId, controller, mediaAppDetails, packageManager, callback)
        }

        val errorResolutionDataTest = TestOptionDetails(
                18,
                getString(R.string.error_resolution_test_title),
                getString(R.string.error_resolution_test_desc),
                TestResult.NONE,
                Test.NO_LOGS,
                false
        ) { _, callback, testId ->
            runErrorResolutionDataTest(
                    testId, controller, callback)
        }

        val launcherTest = TestOptionDetails(
                19,
                getString(R.string.launcher_intent_test_title),
                getString(R.string.launcher_intent_test_desc),
                TestResult.NONE,
                Test.NO_LOGS,
                false
        ) { _, callback, testId ->
            runLauncherTest(
                    testId, controller, mediaAppDetails, packageManager, callback)
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

        callback?.let {
            it.onTestsCreated(testList.asList(), testSuites)
        }
    }


    /**
     * This callback will log any changes in the playback state or metadata of the mediacontroller
     */
    private var controllerCallback: MediaControllerCompat.Callback =
            object : MediaControllerCompat.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                    val s = formatPlaybackState(state)
                    if (printLogsFormatted) {
                        Log.i(TAG, getString(
                                R.string.logs_controller_info_formatted,
                                getString(R.string.tests_info_state),
                                s
                        ))
                    } else {
                        Log.i(TAG, getString(
                                R.string.logs_controller_info_parsable,
                                getString(R.string.tests_info_state),
                                formatPlaybackStateParsable(state)
                        ))
                    }
                    callback?.let {
                        it.onControllerPlaybackStateChanged(s)
                    }
                }

                override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
                    val s = formatMetadata(metadata)
                    if (printLogsFormatted) {
                        Log.i(TAG, getString(
                                R.string.logs_controller_info_formatted,
                                getString(R.string.tests_info_metadata),
                                s
                        ))
                    } else {
                        Log.i(TAG, getString(
                                R.string.logs_controller_info_parsable,
                                getString(R.string.tests_info_metadata),
                                formatMetadataParsable(metadata)
                        ))
                    }
                    callback?.let {
                        it.onControllerMetadataChanged(s)
                    }
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    val s = repeatModeToName(repeatMode)
                    if (printLogsFormatted) {
                        Log.i(TAG, getString(
                                R.string.logs_controller_info_formatted,
                                getString(R.string.tests_info_repeat),
                                s
                        ))
                    } else {
                        Log.i(TAG, getString(
                                R.string.logs_controller_info_parsable,
                                getString(R.string.tests_info_repeat),
                                repeatMode.toString()
                        ))
                    }
                    callback?.let {
                        it.onControllerRepeatModeChanged(s)
                    }
                }

                override fun onShuffleModeChanged(shuffleMode: Int) {
                    val s = shuffleModeToName(shuffleMode)
                    if (printLogsFormatted) {
                        Log.i(TAG, getString(
                                R.string.logs_controller_info_formatted,
                                getString(R.string.tests_info_shuffle),
                                s
                        ))
                    } else {
                        Log.i(TAG, getString(
                                R.string.logs_controller_info_parsable,
                                getString(R.string.tests_info_shuffle),
                                shuffleMode.toString()
                        ))
                    }
                    callback?.let {
                        it.onControllerShuffleModeChanged(s)
                    }
                }

                override fun onQueueTitleChanged(title: CharSequence?) {
                    if (printLogsFormatted) {
                        Log.i(TAG, getString(
                                R.string.logs_controller_info_formatted,
                                getString(R.string.tests_info_queue_title),
                                title
                        ))
                    } else {
                        Log.i(TAG, getString(
                                R.string.logs_controller_info_parsable,
                                getString(R.string.tests_info_queue_title),
                                title
                        ))
                    }
                    callback?.let {
                        it.onControllerQueueTitleChanged(title)
                    }
                }

                override fun onQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>?) {
                    if (printLogsFormatted) {
                        Log.i(TAG, getString(
                                R.string.logs_controller_info_formatted,
                                getString(R.string.tests_info_queue),
                                queueToString(queue)
                        ))
                    } else {
                        Log.i(TAG, getString(
                                R.string.logs_controller_info_parsable,
                                getString(R.string.tests_info_queue),
                                queueToStringParsable(queue)
                        ))
                    }
                    callback?.let {
                        it.onControllerQueueChanged(queue)
                    }
                }
            }

    /**
     * Create the notification to make sure we run in the foreground
     * Note that this notification does NOT actually display on Android TV
     */
    private fun createNotification(): Notification {
        // From API 26, the NotificationManager requires the use of notification channels.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            // Channel must be created
            if (notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                val serviceChannel = NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        getString(R.string.app_name),
                        NotificationManager.IMPORTANCE_DEFAULT
                )
                notificationManager.createNotificationChannel(serviceChannel)
            }
        }

        val notificationIntent = Intent(applicationContext, MediaAppTestingActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, notificationIntent, 0)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(getText(R.string.app_name))
                .setContentText(getText(R.string.notification_message))
                .setSmallIcon(R.drawable.ic_android)
                .setContentIntent(pendingIntent)
                .build()
    }

    fun registerCallback(callback: ICallback) {
        this.callback = callback
    }

    // declare callback function
    interface ICallback {
        fun onConnected()
        fun onConnectionSuspended(appName: String)
        fun onConnectionFailed(appName: String, componentName: ComponentName)
        fun onSetupMediaControllerError(message: String)
        fun onTestsCreated(testList: List<TestOptionDetails>, testSuites: List<MediaAppTestSuite>)
        fun onControllerPlaybackStateChanged(playbackState: String)
        fun onControllerMetadataChanged(metadata: String)
        fun onControllerRepeatModeChanged(repeatMode: String)
        fun onControllerShuffleModeChanged(shuffleMode: String)
        fun onControllerQueueTitleChanged(title: CharSequence?)
        fun onControllerQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>?)
    }

    companion object {
        // Key names for external extras.
        private const val PACKAGE_NAME_EXTRA = "com.example.android.mediacontroller.PACKAGE_NAME"

        // Key name for Intent extras.
        private const val APP_DETAILS_EXTRA =
                "com.example.android.mediacontroller.APP_DETAILS_EXTRA"
    }
}