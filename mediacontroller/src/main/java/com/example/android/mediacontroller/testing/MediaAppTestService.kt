package com.example.android.mediacontroller.testing

import android.app.*
import android.content.ComponentName
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
import com.example.android.mediacontroller.*

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
    private var testSuites: List<MediaAppTestSuite>? = null

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
            mediaAppDetails = extras?.getParcelable(APP_DETAILS_EXTRA)
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
        if (mediaController != null) {
            val testDescriptor = TestDescriptor()
            testDescriptor.setupTests(applicationContext, mediaController!!,
                mediaAppDetails, mediaBrowser)
            this.testSuites = testDescriptor.testSuites

            callback?.onTestsCreated(testDescriptor.testList!!, testSuites!!)
        }
        else{
            Log.e(TAG, getString(R.string.setup_tests_error_msg))
            showToast(getString(R.string.setup_tests_error_msg))
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