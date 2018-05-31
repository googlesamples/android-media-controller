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

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.RemoteException
import android.support.design.widget.TabLayout
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.CardView
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_media_app_testing.media_controller_info_page
import kotlinx.android.synthetic.main.activity_media_app_testing.media_controller_test_page
import kotlinx.android.synthetic.main.activity_media_app_testing.page_indicator
import kotlinx.android.synthetic.main.activity_media_app_testing.toolbar
import kotlinx.android.synthetic.main.activity_media_app_testing.view_pager
import kotlinx.android.synthetic.main.media_controller_info.connection_error_text
import kotlinx.android.synthetic.main.media_controller_info.metadata_text
import kotlinx.android.synthetic.main.media_controller_info.playback_state_text
import kotlinx.android.synthetic.main.media_controller_info.queue_text
import kotlinx.android.synthetic.main.media_controller_info.queue_title_text
import kotlinx.android.synthetic.main.media_controller_info.repeat_mode_text
import kotlinx.android.synthetic.main.media_controller_info.shuffle_mode_text
import kotlinx.android.synthetic.main.media_test_option.view.card_button
import kotlinx.android.synthetic.main.media_test_option.view.card_header
import kotlinx.android.synthetic.main.media_test_option.view.card_text
import kotlinx.android.synthetic.main.media_tests.test_options_list
import kotlinx.android.synthetic.main.media_tests.test_results_container
import kotlinx.android.synthetic.main.media_tests.tests_query
import java.text.DateFormat
import java.util.Date

class MediaAppTestingActivity : AppCompatActivity() {
    private var mediaAppDetails: MediaAppDetails? = null
    private var mediaController: MediaControllerCompat? = null
    private var mediaBrowser: MediaBrowserCompat? = null

    private lateinit var viewPager: ViewPager
    private lateinit var testsQuery: EditText
    private lateinit var resultsContainer: LinearLayout
    private lateinit var connectionErrorText: TextView
    private lateinit var playbackStateText: TextView
    private lateinit var metadataText: TextView
    private lateinit var repeatModeText: TextView
    private lateinit var shuffleModeText: TextView
    private lateinit var queueTitleText: TextView
    private lateinit var queueText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_app_testing)
        val toolbar: Toolbar = toolbar
        setSupportActionBar(toolbar)

        viewPager = view_pager
        testsQuery = tests_query
        resultsContainer = test_results_container
        connectionErrorText = connection_error_text
        playbackStateText = playback_state_text
        metadataText = metadata_text
        repeatModeText = repeat_mode_text
        shuffleModeText = shuffle_mode_text
        queueTitleText = queue_title_text
        queueText = queue_text

        handleIntent(intent)

        val appDetails = mediaAppDetails
        if (appDetails != null) {
            setupMedia()
            setupToolbar(appDetails.appName, appDetails.icon)
        } else {
            viewPager.visibility = View.GONE
        }

        val pages = arrayOf(media_controller_info_page, media_controller_test_page)
        viewPager.offscreenPageLimit = pages.size
        viewPager.adapter = object : PagerAdapter() {
            override fun getCount(): Int {
                return pages.size
            }

            override fun isViewFromObject(view: View, item: Any): Boolean {
                return view == item
            }

            override fun instantiateItem(container: ViewGroup, position: Int): Any {
                return pages[position]
            }
        }

        val pageIndicator: TabLayout = page_indicator
        pageIndicator.setupWithViewPager(viewPager)

        setupTests()
    }

    override fun onDestroy() {
        mediaController?.unregisterCallback(controllerCallback)
        mediaController = null

        mediaBrowser?.let {
            if (it.isConnected) {
                it.disconnect()
            }
        }
        mediaBrowser = null

        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val originalDetails = mediaAppDetails

        handleIntent(intent)

        // Redo setup for mediaBrowser and mediaController if mediaAppDetails is updated
        val appDetails = mediaAppDetails
        if (appDetails != null && appDetails != originalDetails) {
            setupMedia()
            setupToolbar(appDetails.appName, appDetails.icon)
        } else {
            viewPager.visibility = View.GONE
        }
    }

    /**
     * This is the single point where the MediaBrowser and MediaController are setup. If there is
     * previously a controller/browser, they are disconnected/unsubscribed.
     */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) {
            return
        }

        mediaBrowser?.let {
            if (it.isConnected) {
                it.disconnect()
            }
        }

        val data = intent.data
        val appPackageName: String?
        appPackageName = when {
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
                    this,
                    "Couldn't update MediaAppDetails object",
                    Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)

        out.putParcelable(STATE_APP_DETAILS_KEY, mediaAppDetails)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)

        mediaAppDetails = savedInstanceState.getParcelable(STATE_APP_DETAILS_KEY)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showError(message: String) {
        connectionErrorText.text = message
        connectionErrorText.visibility = View.VISIBLE
    }

    private fun setupToolbar(name: String, icon: Bitmap) {
        val actionBar = supportActionBar
        if (actionBar != null) {
            val toolbarIcon = BitmapUtils.createToolbarIcon(resources, icon)
            actionBar.setIcon(BitmapDrawable(resources, toolbarIcon))
            actionBar.title = name
        }
    }

    private fun setupMedia() {
        // setupMedia() is only called when mediaAppDetails is not null, so this should always
        // skip the if function block
        val appDetails = mediaAppDetails
        if (appDetails == null) {
            Log.e(TAG, getString(R.string.setup_media_error_msg))
            showError(getString(R.string.connection_failed_msg, "!Unknown App Name!"))
            return
        }

        when {
            appDetails.componentName != null -> {
                mediaBrowser = MediaBrowserCompat(this, appDetails.componentName,
                        object : MediaBrowserCompat.ConnectionCallback() {
                            override fun onConnected() {
                                connectionErrorText.visibility = View.GONE
                                setupMediaController(true)
                            }

                            override fun onConnectionSuspended() {
                                showError(getString(
                                        R.string.connection_lost_msg,
                                        appDetails.appName
                                ))
                            }

                            override fun onConnectionFailed() {
                                showError(getString(
                                        R.string.connection_failed_hint_reject,
                                        appDetails.appName,
                                        appDetails.componentName.flattenToShortString()
                                ))
                            }
                        }, null).apply { connect() }
            }
            appDetails.sessionToken != null -> setupMediaController(false)
            else -> showError(getString(R.string.connection_failed_hint_setup, appDetails.appName))
        }
    }

    private fun setupMediaController(useTokenFromBrowser: Boolean) {
        try {
            lateinit var token: MediaSessionCompat.Token
            // setupMediaController() is only called either immediately after the mediaBrowser is
            // connected or if mediaAppDetails contains a sessionToken.
            if (useTokenFromBrowser) {
                val browser = mediaBrowser
                if (browser != null) {
                    token = browser.sessionToken
                } else {
                    Log.e(TAG, getString(
                            R.string.setup_media_controller_error_msg,
                            "MediaBrowser"
                    ))
                    showError(getString(
                            R.string.setup_media_controller_error_hint,
                            "MediaBrowser"
                    ))
                    return
                }
            } else {
                val appDetails = mediaAppDetails
                if (appDetails != null) {
                    token = appDetails.sessionToken
                } else {
                    Log.e(TAG, getString(
                            R.string.setup_media_controller_error_msg,
                            "MediaAppDetails"
                    ))
                    showError(getString(
                            R.string.setup_media_controller_error_hint,
                            "MediaAppDetails"
                    ))
                    return
                }
            }

            mediaController = MediaControllerCompat(this, token)
            mediaController?.let {
                it.registerCallback(controllerCallback)

                // Force update on connect
                controllerCallback.run {
                    onPlaybackStateChanged(it.playbackState)
                    onMetadataChanged(it.metadata)
                    onRepeatModeChanged(it.repeatMode)
                    onShuffleModeChanged(it.shuffleMode)
                    onQueueTitleChanged(it.queueTitle)
                    onQueueChanged(it.queue)
                }
            }

            // Ensure views are visible
            viewPager.visibility = View.VISIBLE

            Log.d(TAG, "MediaControllerCompat created")
        } catch (remoteException: RemoteException) {
            Log.e(TAG, getString(R.string.media_controller_failed_msg), remoteException)
            showToast(getString(R.string.media_controller_failed_msg))
        }
    }

    private fun setupTests() {
        val testOptionAdapter = TestOptionAdapter(
                arrayOf(
                        TestOptionDetails(
                                "Play",
                                "This tests the play functionality",
                                ::testPlay
                        ),
                        TestOptionDetails(
                                "Play from Search",
                                "This tests the Play From Search functionality",
                                ::testPlayFromSearch
                        ),
                        TestOptionDetails(
                                "Pause",
                                "This tests the pause functionality",
                                ::testPause
                        ),
                        TestOptionDetails(
                                "Stop",
                                "This tests the stop functionality",
                                ::testStop
                        ),
                        TestOptionDetails(
                                "Seek",
                                "This tests the seek functionality",
                                ::testSeek
                        ),
                        TestOptionDetails(
                                "Skip",
                                "This tests the skip functionality",
                                ::testSkip
                        )
                ))

        val testList = test_options_list
        testList.layoutManager = LinearLayoutManager(this)
        testList.setHasFixedSize(true)
        testList.adapter = testOptionAdapter
    }

    private fun playbackStateToName(playbackState: Int): String {
        return when (playbackState) {
            PlaybackStateCompat.STATE_NONE -> "STATE_NONE"
            PlaybackStateCompat.STATE_STOPPED -> "STATE_STOPPED"
            PlaybackStateCompat.STATE_PAUSED -> "STATE_PAUSED"
            PlaybackStateCompat.STATE_PLAYING -> "STATE_PLAYING"
            PlaybackStateCompat.STATE_FAST_FORWARDING -> "STATE_FAST_FORWARDING"
            PlaybackStateCompat.STATE_REWINDING -> "STATE_REWINDING"
            PlaybackStateCompat.STATE_BUFFERING -> "STATE_BUFFERING"
            PlaybackStateCompat.STATE_ERROR -> "STATE_ERROR"
            PlaybackStateCompat.STATE_CONNECTING -> "STATE_CONNECTING"
            PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS -> "STATE_SKIPPING_TO_PREVIOUS"
            PlaybackStateCompat.STATE_SKIPPING_TO_NEXT -> "STATE_SKIPPING_TO_NEXT"
            PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM -> "STATE_SKIPPING_TO_QUEUE_ITEM"
            else -> "!Unknown State!"
        }
    }

    private fun actionsToString(actions: Long): String {
        var s = "[\n"
        if (actions and PlaybackStateCompat.ACTION_PREPARE != 0L) {
            s += "\tACTION_PREPARE\n"
        }
        if (actions and PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID != 0L) {
            s += "\tACTION_PREPARE_FROM_MEDIA_ID\n"
        }
        if (actions and PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH != 0L) {
            s += "\tACTION_PREPARE_FROM_SEARCH\n"
        }
        if (actions and PlaybackStateCompat.ACTION_PREPARE_FROM_URI != 0L) {
            s += "\tACTION_PREPARE_FROM_URI\n"
        }
        if (actions and PlaybackStateCompat.ACTION_PLAY != 0L) {
            s += "\tACTION_PLAY\n"
        }
        if (actions and PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID != 0L) {
            s += "\tACTION_PLAY_FROM_MEDIA_ID\n"
        }
        if (actions and PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH != 0L) {
            s += "\tACTION_PLAY_FROM_SEARCH\n"
        }
        if (actions and PlaybackStateCompat.ACTION_PLAY_FROM_URI != 0L) {
            s += "\tACTION_PLAY_FROM_URI\n"
        }
        if (actions and PlaybackStateCompat.ACTION_PLAY_PAUSE != 0L) {
            s += "\tACTION_PLAY_PAUSE\n"
        }
        if (actions and PlaybackStateCompat.ACTION_PAUSE != 0L) {
            s += "\tACTION_PAUSE\n"
        }
        if (actions and PlaybackStateCompat.ACTION_STOP != 0L) {
            s += "\tACTION_STOP\n"
        }
        if (actions and PlaybackStateCompat.ACTION_SEEK_TO != 0L) {
            s += "\tACTION_SEEK_TO\n"
        }
        if (actions and PlaybackStateCompat.ACTION_SKIP_TO_NEXT != 0L) {
            s += "\tACTION_SKIP_TO_NEXT\n"
        }
        if (actions and PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS != 0L) {
            s += "\tACTION_SKIP_TO_PREVIOUS\n"
        }
        if (actions and PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM != 0L) {
            s += "\tACTION_SKIP_TO_QUEUE_ITEM\n"
        }
        if (actions and PlaybackStateCompat.ACTION_FAST_FORWARD != 0L) {
            s += "\tACTION_FAST_FORWARD\n"
        }
        if (actions and PlaybackStateCompat.ACTION_REWIND != 0L) {
            s += "\tACTION_REWIND\n"
        }
        if (actions and PlaybackStateCompat.ACTION_SET_RATING != 0L) {
            s += "\tACTION_SET_RATING\n"
        }
        if (actions and PlaybackStateCompat.ACTION_SET_REPEAT_MODE != 0L) {
            s += "\tACTION_SET_REPEAT_MODE\n"
        }
        if (actions and PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE != 0L) {
            s += "\tACTION_SET_SHUFFLE_MODE\n"
        }
        if (actions and PlaybackStateCompat.ACTION_SET_CAPTIONING_ENABLED != 0L) {
            s += "\tACTION_SET_CAPTIONING_ENABLED\n"
        }
        s += "]"
        return s
    }

    private fun errorCodeToName(code: Int): String {
        return when (code) {
            PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR -> "ERROR_CODE_UNKNOWN_ERROR"
            PlaybackStateCompat.ERROR_CODE_APP_ERROR -> "ERROR_CODE_APP_ERROR"
            PlaybackStateCompat.ERROR_CODE_NOT_SUPPORTED -> "ERROR_CODE_NOT_SUPPORTED"
            PlaybackStateCompat.ERROR_CODE_AUTHENTICATION_EXPIRED ->
                "ERROR_CODE_AUTHENTICATION_EXPIRED"
            PlaybackStateCompat.ERROR_CODE_PREMIUM_ACCOUNT_REQUIRED ->
                "ERROR_CODE_PREMIUM_ACCOUNT_REQUIRED"
            PlaybackStateCompat.ERROR_CODE_CONCURRENT_STREAM_LIMIT ->
                "ERROR_CODE_CONCURRENT_STREAM_LIMIT"
            PlaybackStateCompat.ERROR_CODE_PARENTAL_CONTROL_RESTRICTED ->
                "ERROR_CODE_PARENTAL_CONTROL_RESTRICTED"
            PlaybackStateCompat.ERROR_CODE_NOT_AVAILABLE_IN_REGION ->
                "ERROR_CODE_NOT_AVAILABLE_IN_REGION"
            PlaybackStateCompat.ERROR_CODE_CONTENT_ALREADY_PLAYING ->
                "ERROR_CODE_CONTENT_ALREADY_PLAYING"
            PlaybackStateCompat.ERROR_CODE_SKIP_LIMIT_REACHED -> "ERROR_CODE_SKIP_LIMIT_REACHED"
            PlaybackStateCompat.ERROR_CODE_ACTION_ABORTED -> "ERROR_CODE_ACTION_ABORTED"
            PlaybackStateCompat.ERROR_CODE_END_OF_QUEUE -> "ERROR_CODE_END_OF_QUEUE"
            else -> "!Unknown Error!"
        }
    }

    private fun repeatModeToName(mode: Int): String {
        return when (mode) {
            PlaybackStateCompat.REPEAT_MODE_ALL -> "ALL"
            PlaybackStateCompat.REPEAT_MODE_GROUP -> "GROUP"
            PlaybackStateCompat.REPEAT_MODE_INVALID -> "INVALID"
            PlaybackStateCompat.REPEAT_MODE_NONE -> "NONE"
            PlaybackStateCompat.REPEAT_MODE_ONE -> "ONE"
            else -> "!Unknown!"
        }
    }

    private fun shuffleModeToName(mode: Int): String {
        return when (mode) {
            PlaybackStateCompat.SHUFFLE_MODE_ALL -> "ALL"
            PlaybackStateCompat.SHUFFLE_MODE_GROUP -> "GROUP"
            PlaybackStateCompat.SHUFFLE_MODE_INVALID -> "INVALID"
            PlaybackStateCompat.SHUFFLE_MODE_NONE -> "NONE"
            else -> "!Unknown!"
        }
    }

    private fun formatPlaybackState(state: PlaybackStateCompat): String {
        var formattedString = "State:                     " + playbackStateToName(state.state)
        if (state.state == PlaybackStateCompat.STATE_ERROR) {
            formattedString += ("\nError Code:                " + errorCodeToName(state.errorCode)
                    + "\nError Message:             " + state.errorMessage)
        }
        formattedString += ("\nPosition:                  " + state.position
                + "\nBuffered Position:         " + state.bufferedPosition
                + "\nLast Position Update Time: " + state.lastPositionUpdateTime
                + "\nPlayback Speed:            " + state.playbackSpeed
                + "\nActive Queue Item ID:      " + state.activeQueueItemId
                + "\nActions: " + actionsToString(state.actions))
        return formattedString
    }

    private fun getMetadataKey(metadata: MediaMetadataCompat, key: String, type: Int = 0): String? {
        if (metadata.containsKey(key)) {
            return when (type) {
                0 -> metadata.getString(key)
                1 -> metadata.getLong(key).toString()
                2 -> "Bitmap" //metadata.getBitmap(key)
                3 -> "Rating" //metadata.getRating(key)
                else -> "!Unknown type!"
            }
        }
        return "!Not present!"
    }

    private fun formatMetadata(metadata: MediaMetadataCompat): String {
        var s = "MEDIA_ID:            " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_MEDIA_ID)
        s += "\nADVERTISEMENT:       " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_ADVERTISEMENT)
        s += "\nALBUM:               " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_ALBUM)
        s += "\nALBUM_ART:           " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_ALBUM_ART, 2)
        s += "\nALBUM_ART_URI:       " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)
        s += "\nALBUM_ARTIST:        " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST)
        s += "\nART:                 " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_ART, 2)
        s += "\nART_URI:             " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_ART_URI)
        s += "\nARTIST:              " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_ARTIST)
        s += "\nAUTHOR:              " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_AUTHOR)
        s += "\nBT_FOLDER_TYPE:      " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_BT_FOLDER_TYPE)
        s += "\nCOMPILATION:         " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_COMPILATION)
        s += "\nCOMPOSER:            " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_COMPOSER)
        s += "\nDATE:                " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_DATE)
        s += "\nDISC_NUMBER:         " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_DISC_NUMBER, 1)
        s += "\nDISPLAY_DESCRIPTION: " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION)
        s += "\nDISPLAY_ICON:        " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, 2)
        s += "\nDISPLAY_ICON_URI:    " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI)
        s += "\nDISPLAY_SUBTITLE:    " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE)
        s += "\nDISPLAY_TITLE:       " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)
        s += "\nDOWNLOAD_STATUS:     " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_DOWNLOAD_STATUS)
        s += "\nDURATION:            " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_DURATION, 1)
        s += "\nGENRE:               " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_GENRE)
        s += "\nMEDIA_URI:           " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_MEDIA_URI)
        s += "\nNUM_TRACKS:          " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, 1)
        s += "\nRATING:              " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_RATING, 3)
        s += "\nTITLE:               " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_TITLE)
        s += "\nTRACK_NUMBER:        " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, 1)
        s += "\nUSER_RATING:         " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_USER_RATING, 3)
        s += "\nWRITER:              " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_WRITER)
        s += "\nYEAR:                " +
                getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_YEAR)

        return s
    }

    /**
     * This callback will log any changes in the playback state or metadata of the mediacontroller
     */
    private var controllerCallback: MediaControllerCompat.Callback =
            object : MediaControllerCompat.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                    val s = if (state == null) "!null!" else formatPlaybackState(state)
                    Log.i(TAG, "<PlaybackState>\n$s")
                    playbackStateText.text = s
                }

                override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
                    val s = if (metadata == null) "!null!" else formatMetadata(metadata)
                    Log.i(TAG, "<Metadata>\n$s")
                    metadataText.text = s
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    val s = repeatModeToName(repeatMode)
                    Log.i(TAG, "<RepeatMode>\n$s")
                    repeatModeText.text = s
                }

                override fun onShuffleModeChanged(shuffleMode: Int) {
                    val s = shuffleModeToName(shuffleMode)
                    Log.i(TAG, "<ShuffleMode>\n$s")
                    shuffleModeText.text = s
                }

                override fun onQueueTitleChanged(title: CharSequence?) {
                    Log.i(TAG, "<QueueTitle>\n$title")
                    queueTitleText.text = title
                }

                override fun onQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>?) {
                    if (queue == null) {
                        Log.i(TAG, "<Queue>\nnull")
                        queueText.text = getString(R.string.tests_info_queue_null)
                        return
                    }
                    Log.i(TAG, "<Queue>\n${queue.size} items")
                    //TODO(nevmital): Temporary text, add more useful Queue information
                    queueText.text = "${queue.size} items"
                }
            }

    class TestOptionAdapter(
            private val options: Array<TestOptionDetails>
    ) : RecyclerView.Adapter<TestOptionAdapter.ViewHolder>() {
        class ViewHolder(val cardView: CardView) : RecyclerView.ViewHolder(cardView)

        override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int
        ): TestOptionAdapter.ViewHolder {
            val cardView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.media_test_option, parent, false) as CardView
            return ViewHolder(cardView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.cardView.card_header.text = options[position].name
            holder.cardView.card_text.text = options[position].desc

            holder.cardView.card_button.setOnClickListener({ options[position].runTest() })
        }

        override fun getItemCount() = options.size
    }

    class TestOptionDetails(testName: String, testDesc: String, private val test: () -> Unit) {
        val name: String = testName
        val desc: String = testDesc

        fun runTest() {
            test()
        }
    }

    fun MediaMetadataCompat?.isContentSameAs(other: MediaMetadataCompat?): Boolean {
        if (this == null || other == null) {
            if (this == null && other == null) {
                return true
            }
            return false
        }
        return (this.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
                == other.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
                && this.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
                == other.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
                && this.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
                == other.getLong(MediaMetadataCompat.METADATA_KEY_DURATION))
    }

    private fun logUpdate(testName: String, message: String) {
        val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.LONG).format(Date())
        val update = "[$date] <$testName Test>:\n\t$message"

        Log.i(TAG, update)
        val newLine = TextView(this)
        newLine.text = update
        resultsContainer.addView(newLine, 0)
    }

    private fun testPlay() {
        mediaController?.let {
            val originalState = it.playbackState
            val originalMetadata = it.metadata

            val testCallback: MediaControllerCompat.Callback =
                    object : MediaControllerCompat.Callback() {
                        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                            if (state == originalState || state == null) {
                                return
                            }

                            when (state.state) {
                                PlaybackStateCompat.STATE_PLAYING -> {
                                    logUpdate("Play", "Succeeded!")
                                    it.unregisterCallback(this)
                                }
                                PlaybackStateCompat.STATE_BUFFERING -> {
                                    logUpdate(
                                            "Play",
                                            "Running: Valid intermediate state "
                                                    + playbackStateToName(state.state)
                                    )
                                }
                                else -> {
                                    logUpdate(
                                            "Play",
                                            "Failed: Invalid state "
                                                    + playbackStateToName(state.state)
                                    )
                                    if (state.state == PlaybackStateCompat.STATE_ERROR) {
                                        logUpdate(
                                                "Play",
                                                "Error: ${errorCodeToName(state.errorCode)}"
                                        )
                                    }
                                    it.unregisterCallback(this)
                                }
                            }
                        }

                        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
                            if (originalMetadata != null
                                    && !metadata.isContentSameAs(originalMetadata)) {
                                logUpdate("Play", "Failed: Media item changed")
                                it.unregisterCallback(this)
                            } else {
                                logUpdate("Play", "Running: Media item updated")
                            }
                        }
                    }

            logUpdate("Play", "Started.")
            it.transportControls.play()

            if (originalState.state == PlaybackStateCompat.STATE_PLAYING) {
                logUpdate("Play", "Ending: Already playing")
                return
            }

            it.registerCallback(testCallback)
        }
    }

    private fun testPlayFromSearch() {
        mediaController?.let {
            val originalState = it.playbackState
            val originalMetadata = it.metadata

            val testCallback: MediaControllerCompat.Callback =
                    object : MediaControllerCompat.Callback() {
                        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                            if (state == originalState || state == null) {
                                return
                            }

                            when (state.state) {
                                PlaybackStateCompat.STATE_PLAYING -> {
                                    logUpdate("Play From Search", "Succeeded!")
                                    it.unregisterCallback(this)
                                }
                                PlaybackStateCompat.STATE_BUFFERING -> {
                                    logUpdate(
                                            "Play From Search",
                                            "Running: Valid intermediate state "
                                                    + playbackStateToName(state.state)
                                    )
                                }
                                else -> {
                                    logUpdate(
                                            "Play From Search",
                                            "Failed: Invalid state "
                                                    + playbackStateToName(state.state)
                                    )
                                    if (state.state == PlaybackStateCompat.STATE_ERROR) {
                                        logUpdate(
                                                "Play From Search",
                                                "Error: ${errorCodeToName(state.errorCode)}"
                                        )
                                    }
                                    it.unregisterCallback(this)
                                }
                            }
                        }

                        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
                            if (!metadata.isContentSameAs(originalMetadata)) {
                                logUpdate("Play From Search", "Running: Media item changed")
                            } else {
                                logUpdate("Play From Search", "Running: Media item updated")
                                if (originalState.state == PlaybackStateCompat.STATE_PLAYING) {
                                    logUpdate(
                                            "Play From Search",
                                            "Ending: Already playing requested media item"
                                    )
                                    it.unregisterCallback(this)
                                }
                            }
                        }
                    }

            logUpdate("Play From Search", "Started.")
            val query = testsQuery.text.toString()
            if (query == "") {
                logUpdate("Play From Search", "Ending: Empty query")
                return
            }
            it.transportControls.playFromSearch(query, null)

            it.registerCallback(testCallback)
        }
    }

    private fun testPause() {
        logUpdate("Pause", "Not yet implemented.")
    }

    private fun testStop() {
        logUpdate("Stop", "Not yet implemented.")
    }

    private fun testSeek() {
        logUpdate("Seek", "Not yet implemented.")
    }

    private fun testSkip() {
        logUpdate("Skip", "Not yet implemented.")
    }

    companion object {
        private const val TAG = "MediaAppTestingActivity"

        // Key names for external extras.
        private const val PACKAGE_NAME_EXTRA = "com.example.android.mediacontroller.PACKAGE_NAME"

        // Key name for Intent extras.
        private const val APP_DETAILS_EXTRA =
                "com.example.android.mediacontroller.APP_DETAILS_EXTRA"

        // Key name used for saving/restoring instance state.
        private const val STATE_APP_DETAILS_KEY =
                "com.example.android.mediacontroller.STATE_APP_DETAILS_KEY"

        /**
         * Builds an [Intent] to launch this Activity with a set of extras.
         *
         * @param activity   The Activity building the Intent.
         * @param appDetails The app details about the media app to connect to.
         * @return An Intent that can be used to start the Activity.
         */
        fun buildIntent(
                activity: Activity,
                appDetails: MediaAppDetails
        ): Intent {
            val intent = Intent(activity, MediaAppTestingActivity::class.java)
            intent.putExtra(APP_DETAILS_EXTRA, appDetails)
            return intent
        }
    }
}
