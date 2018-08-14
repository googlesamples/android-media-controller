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
import android.support.v4.content.ContextCompat
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
import android.view.Menu
import android.view.MenuItem
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
import kotlinx.android.synthetic.main.media_controller_info.queue_item_list
import kotlinx.android.synthetic.main.media_controller_info.queue_text
import kotlinx.android.synthetic.main.media_controller_info.queue_title_text
import kotlinx.android.synthetic.main.media_controller_info.repeat_mode_text
import kotlinx.android.synthetic.main.media_controller_info.shuffle_mode_text
import kotlinx.android.synthetic.main.media_queue_item.view.description_id
import kotlinx.android.synthetic.main.media_queue_item.view.description_subtitle
import kotlinx.android.synthetic.main.media_queue_item.view.description_title
import kotlinx.android.synthetic.main.media_queue_item.view.description_uri
import kotlinx.android.synthetic.main.media_queue_item.view.queue_id
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

    private var printLogsFormatted: Boolean = true

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

        Test.androidResources = resources

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
    }

    override fun onDestroy() {
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
                    this,
                    getString(R.string.media_app_details_update_failed),
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.testing, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item == null) {
            return true
        }

        if (item.itemId == R.id.logs_toggle) {
            if (printLogsFormatted) {
                item.icon = ContextCompat.getDrawable(this, R.drawable.ic_parsable_logs_24dp)
                Log.i(TAG, getString(R.string.logs_activate_parsable))
                showToast(getString(R.string.logs_activate_parsable))
                printLogsFormatted = false
            } else {
                item.icon = ContextCompat.getDrawable(this, R.drawable.ic_formatted_logs_24dp)
                Log.i(TAG, getString(R.string.logs_activate_formatted))
                showToast(getString(R.string.logs_activate_formatted))
                printLogsFormatted = true
            }
        } else if (item.itemId == R.id.logs_trigger) {
            logCurrentController()
            Log.i(TAG, getString(R.string.logs_triggered))
            showToast(getString(R.string.logs_triggered))
        }

        return true
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
            val token: MediaSessionCompat.Token
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

            mediaController = MediaControllerCompat(this, token).also {
                it.registerCallback(controllerCallback)

                // Force update on connect
                logCurrentController(it)
            }

            // Setup tests once media controller is connected
            setupTests()

            // Ensure views are visible
            viewPager.visibility = View.VISIBLE

            Log.d(TAG, getString(R.string.media_controller_created))
        } catch (remoteException: RemoteException) {
            Log.e(TAG, getString(R.string.media_controller_failed_msg), remoteException)
            showToast(getString(R.string.media_controller_failed_msg))
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
                getString(R.string.play_test_title),
                getString(R.string.play_test_desc)
        ) { _ -> runPlayTest(controller, ::logTestUpdate) }

        /**
         * Tests the playFromSearch() transport control. The test can start in any state, might
         * enter a transition state, but must eventually end in STATE_PLAYING with playback
         * position at 0. The test will fail for any terminal state other than the starting state
         * and STATE_PLAYING. This test does not perform any metadata checks.
         */
        val playFromSearch = TestOptionDetails(
                getString(R.string.play_search_test_title),
                getString(R.string.play_search_test_desc)
        ) { query -> runPlayFromSearchTest(query, controller, ::logTestUpdate) }

        /**
         * Tests the playFromMediaId() transport control. The test can start in any state, might
         * enter a transition state, but must eventually end in STATE_PLAYING with playback
         * position at 0. The test will fail for any terminal state other than the starting state
         * and STATE_PLAYING. The test will also fail if query is empty/null. This test does not
         * perform any metadata checks.
         */
        val playFromMediaId = TestOptionDetails(
                getString(R.string.play_media_id_test_title),
                getString(R.string.play_media_id_test_desc)
        ) { query -> runPlayFromMediaIdTest(query, controller, ::logTestUpdate) }

        /**
         * Tests the playFromUri() transport control. The test can start in any state, might
         * enter a transition state, but must eventually end in STATE_PLAYING with playback
         * position at 0. The test will fail for any terminal state other than the starting state
         * and STATE_PLAYING. The test will also fail if query is empty/null. This test does not
         * perform any metadata checks.
         */
        val playFromUri = TestOptionDetails(
                getString(R.string.play_uri_test_title),
                getString(R.string.play_uri_test_desc)
        ) { query -> runPlayFromUriTest(query, controller, ::logTestUpdate) }

        /**
         * Tests the pause() transport control. The test can start in any state, but must end in
         * STATE_PAUSED (but STATE_STOPPED is also okay if that is the state the test started with).
         * The test will fail for any terminal state other than the starting state, STATE_PAUSED,
         * and STATE_STOPPED. The test will also fail if the metadata changes unless the test began
         * with null metadata.
         */
        val pauseTest = TestOptionDetails(
                getString(R.string.pause_test_title),
                getString(R.string.pause_test_desc)
        ) { _ -> runPauseTest(controller, ::logTestUpdate) }

        /**
         * Tests the stop() transport control. The test can start in any state, but must end in
         * STATE_STOPPED or STATE_NONE. The test will fail for any terminal state other than the
         * starting state, STATE_STOPPED, and STATE_NONE. The test will also fail if the metadata
         * changes to a non-null media item different from the original media item.
         */
        val stopTest = TestOptionDetails(
                getString(R.string.stop_test_title),
                getString(R.string.stop_test_desc)
        ) { _ -> runStopTest(controller, ::logTestUpdate) }

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
                getString(R.string.skip_next_test_title),
                getString(R.string.skip_next_test_desc)
        ) { _ -> runSkipToNextTest(controller, ::logTestUpdate) }

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
                getString(R.string.skip_prev_test_title),
                getString(R.string.skip_prev_test_desc)
        ) { _ -> runSkipToPrevTest(controller, ::logTestUpdate) }

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
                getString(R.string.skip_item_test_title),
                getString(R.string.skip_item_test_desc)
        ) { query -> runSkipToItemTest(query, controller, ::logTestUpdate) }

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
                getString(R.string.seek_test_title),
                getString(R.string.seek_test_desc)
        ) { query -> runSeekToTest(query, controller, ::logTestUpdate) }

        val testOptionAdapter = TestOptionAdapter(arrayOf(
                playTest,
                playFromSearch,
                playFromMediaId,
                playFromUri,
                pauseTest,
                stopTest,
                skipToNextTest,
                skipToPrevTest,
                skipToItemTest,
                seekToTest
        ))

        val testList = test_options_list
        testList.layoutManager = LinearLayoutManager(this)
        testList.setHasFixedSize(true)
        testList.adapter = testOptionAdapter
    }

    private fun logTestUpdate(logTag: String, message: String) {
        runOnUiThread {
            val date = DateFormat
                    .getDateTimeInstance(DateFormat.SHORT, DateFormat.LONG)
                    .format(Date())
            val update = "[$date] <$logTag>:\n$message"

            if (printLogsFormatted) {
                Log.i(logTag, update)
            } else {
                Log.i(logTag, "<$logTag> [$date] $message")
            }

            val newLine = TextView(this)
            newLine.text = update
            newLine.setTextIsSelectable(true)
            resultsContainer.addView(newLine, 0)
        }
    }

    // Adapter to display test details
    inner class TestOptionAdapter(
            private val tests: Array<TestOptionDetails>
    ) : RecyclerView.Adapter<TestOptionAdapter.ViewHolder>() {
        inner class ViewHolder(val cardView: CardView) : RecyclerView.ViewHolder(cardView)

        override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int
        ): TestOptionAdapter.ViewHolder {
            val cardView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.media_test_option, parent, false) as CardView
            return ViewHolder(cardView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.cardView.card_header.text = tests[position].name
            holder.cardView.card_text.text = tests[position].desc

            holder.cardView.card_button.setOnClickListener(
                    { tests[position].runTest(testsQuery.text.toString()) }
            )
        }

        override fun getItemCount() = tests.size
    }

    // Adapter to display Queue Item information
    class QueueItemAdapter(
            private val items: MutableList<MediaSessionCompat.QueueItem>
    ) : RecyclerView.Adapter<QueueItemAdapter.ViewHolder>() {
        class ViewHolder(val linearLayout: LinearLayout) : RecyclerView.ViewHolder(linearLayout)

        override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int
        ): QueueItemAdapter.ViewHolder {
            val linearLayout = LayoutInflater.from(parent.context)
                    .inflate(R.layout.media_queue_item, parent, false) as LinearLayout
            return ViewHolder(linearLayout)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.linearLayout.queue_id.text =
                    holder.linearLayout.context.getString(
                            R.string.queue_item_id,
                            items[position].queueId
                    )

            val description = items[position].description
            holder.linearLayout.description_title.text =
                    holder.linearLayout.context.getString(
                            R.string.queue_item_title,
                            description.title
                    )
            holder.linearLayout.description_subtitle.text =
                    holder.linearLayout.context.getString(
                            R.string.queue_item_subtitle,
                            description.subtitle
                    )
            holder.linearLayout.description_id.text =
                    holder.linearLayout.context.getString(
                            R.string.queue_item_media_id,
                            description.mediaId
                    )
            holder.linearLayout.description_uri.text =
                    holder.linearLayout.context.getString(
                            R.string.queue_item_media_uri,
                            description.mediaUri.toString()
                    )
        }

        override fun getItemCount() = items.size
    }

    private fun populateQueue(_queue: MutableList<MediaSessionCompat.QueueItem>?) {
        val queue = _queue ?: emptyList<MediaSessionCompat.QueueItem>().toMutableList()
        val queueItemAdapter = QueueItemAdapter(queue)
        val queueList = queue_item_list
        queueList.layoutManager = object : LinearLayoutManager(this) {
            override fun canScrollVertically(): Boolean = false
        }
        queueList.adapter = queueItemAdapter
    }

    private fun logCurrentController(controller: MediaControllerCompat? = mediaController) {
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
                    playbackStateText.text = s
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
                    metadataText.text = s
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
                    repeatModeText.text = s
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
                    shuffleModeText.text = s
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
                    queueTitleText.text = title
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
                    queueText.text = getString(R.string.queue_size, queue?.size ?: 0)
                    populateQueue(queue)
                }
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
