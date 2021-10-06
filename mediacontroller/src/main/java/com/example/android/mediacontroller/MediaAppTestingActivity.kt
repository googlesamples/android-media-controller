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

import android.animation.ArgbEvaluator
import android.app.Activity
import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.text.Editable
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.*

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager

import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.android.synthetic.main.activity_media_app_testing.media_controller_test_page
import kotlinx.android.synthetic.main.activity_media_app_testing.media_controller_test_suite_page
import kotlinx.android.synthetic.main.activity_media_app_testing.media_controller_info_page
import kotlinx.android.synthetic.main.activity_media_app_testing.toolbar
import kotlinx.android.synthetic.main.activity_media_app_testing.view_pager
import kotlinx.android.synthetic.main.config_item.view.test_name_config
import kotlinx.android.synthetic.main.config_item.view.test_query_config
import kotlinx.android.synthetic.main.media_controller_info.queue_item_list
import kotlinx.android.synthetic.main.media_controller_info.connection_error_text
import kotlinx.android.synthetic.main.media_controller_info.metadata_text
import kotlinx.android.synthetic.main.media_controller_info.playback_state_text
import kotlinx.android.synthetic.main.media_controller_info.queue_text
import kotlinx.android.synthetic.main.media_controller_info.repeat_mode_text
import kotlinx.android.synthetic.main.media_controller_info.queue_title_text
import kotlinx.android.synthetic.main.media_controller_info.shuffle_mode_text
import kotlinx.android.synthetic.main.media_queue_item.view.queue_id
import kotlinx.android.synthetic.main.media_queue_item.view.description_title
import kotlinx.android.synthetic.main.media_queue_item.view.description_subtitle
import kotlinx.android.synthetic.main.media_queue_item.view.description_id
import kotlinx.android.synthetic.main.media_queue_item.view.description_uri
import kotlinx.android.synthetic.main.media_test_option.view.configure_test_suite_button
import kotlinx.android.synthetic.main.media_test_option.view.card_text
import kotlinx.android.synthetic.main.media_test_option.view.card_header
import kotlinx.android.synthetic.main.media_test_option.view.card_button
import kotlinx.android.synthetic.main.media_test_suite_result.view.*
import kotlinx.android.synthetic.main.media_test_suites.*

import kotlinx.android.synthetic.main.media_tests.*
import kotlinx.android.synthetic.main.test_suite_configure_dialog.done_button
import kotlinx.android.synthetic.main.test_suite_configure_dialog.reset_results_button
import kotlinx.android.synthetic.main.test_suite_configure_dialog.title
import kotlinx.android.synthetic.main.test_suite_configure_dialog.subtitle
import kotlinx.android.synthetic.main.test_suite_configure_dialog.test_to_configure_list


class MediaAppTestingActivity : AppCompatActivity() {
    private var mediaAppTestService: MediaAppTestService? = null

    private var printLogsFormatted: Boolean = true
    private var isBinding = false
    private var testSuites: List<MediaAppTestSuite> = ArrayList()
    private var resultsAdapter: ResultsAdapter? = null

    /**
     * Map relating tests positioning in the adapter to the tests ID.
     */
    private var positionToIDMap = hashMapOf<Int, Int>()

    /**
     * Map relating the tests ID to the result struct.
     */
    private var iDToResultsMap = hashMapOf<Int, MediaAppTestSuite.TestCaseResults>()

    /**
     * Object that is used to generate colors for partly passing tests.
     */
    private var argbEvaluator = ArgbEvaluator()

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
    private lateinit var bottomNavigationView: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_app_testing)
        val toolbar: Toolbar = toolbar
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        Test.androidResources = resources
        bottomNavigationView = findViewById(R.id.bottom_navigation_view)
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

        // Set up page navigation
        val pages = arrayOf(media_controller_info_page, media_controller_test_page, media_controller_test_suite_page)
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
        bottomNavigationView.setOnNavigationItemSelectedListener { item: MenuItem ->
            return@setOnNavigationItemSelectedListener when (item.itemId) {
                R.id.info_bottom_nav -> {
                    viewPager.currentItem = 0
                    true
                }

                R.id.test_bottom_nav -> {
                    viewPager.currentItem = 1
                    true
                }

                R.id.test_suite_bottom_nav -> {
                    viewPager.currentItem = 2
                    true
                }
                else -> false
            }
        }
        viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {

            override fun onPageScrollStateChanged(state: Int) {
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {

            }

            override fun onPageSelected(position: Int) {
                bottomNavigationView.menu.getItem(position).isChecked = true
            }
        })

        val intent = Intent(this, MediaAppTestService::class.java)
        bindService(intent, connectionResult, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        super.onDestroy()

        if (isBinding) {
            unbindService(connectionResult)
            isBinding = false
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        mediaAppTestService?.let {
            val originalDetails = it.getMediaAppDetails()
            it.handleIntent(intent)

            // Redo setup for mediaBrowser and mediaController if mediaAppDetails is updated
            val mediaAppDetails = it.getMediaAppDetails()
            if (mediaAppDetails != null && mediaAppDetails != originalDetails) {
                if (mediaAppDetails.componentName != null || mediaAppDetails.sessionToken != null) {
                    mediaAppTestService!!.setupMedia(mediaAppDetails)
                } else {
                    showError(getString(R.string.connection_failed_hint_setup, mediaAppDetails.appName))
                }
                setupToolbar(mediaAppDetails.appName, mediaAppDetails.icon)
                viewPager.visibility = View.VISIBLE
            } else {
                viewPager.visibility = View.GONE
            }
        }
    }

    private var connectionResult = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName?) {
            isBinding = false
        }

        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            isBinding = true
            val binder = service as MediaAppTestService.TestServiceBinder
            mediaAppTestService = binder.getService()
            mediaAppTestService!!.registerCallback(callback)
            mediaAppTestService!!.handleIntent(intent)

            val mediaAppDetails = mediaAppTestService!!.getMediaAppDetails()
            if (mediaAppDetails != null) {
                when {
                    mediaAppDetails.componentName != null || mediaAppDetails.sessionToken != null -> {
                        mediaAppTestService!!.setupMedia(mediaAppDetails)
                    }
                    else -> showError(getString(R.string.connection_failed_hint_setup, mediaAppDetails.appName))
                }
                setupToolbar(mediaAppDetails.appName, mediaAppDetails.icon)
                viewPager.visibility = View.VISIBLE
            } else {
                viewPager.visibility = View.GONE
            }
        }
    }

    private val callback = object : MediaAppTestService.ICallback {
        override fun onConnected() {
            connectionErrorText.visibility = View.GONE
        }

        override fun onConnectionSuspended(appName: String) {
            showError(getString(
                    R.string.connection_lost_msg,
                    appName
            ))
        }

        override fun onConnectionFailed(appName: String, componentName: ComponentName) {
            showError(getString(
                    R.string.connection_failed_hint_reject,
                    appName,
                    componentName.flattenToShortString()
            ))
        }

        override fun onSetupMediaControllerError(message: String) {
            showError(message)
        }

        override fun onTestsCreated(testList: List<TestOptionDetails>, testSuites: List<MediaAppTestSuite>) {
            val iDToPositionMap = hashMapOf<Int, Int>()
            for (i in testList.indices) {
                iDToPositionMap[testList[i].id] = i
            }
            val testOptionAdapter = TestOptionAdapter(testList, iDToPositionMap)
            val testOptionsList = test_options_list
            testOptionsList.layoutManager = LinearLayoutManager(this@MediaAppTestingActivity)
            testOptionsList.setHasFixedSize(true)
            testOptionsList.adapter = testOptionAdapter

            val testSuiteAdapter = TestSuiteAdapter(testSuites.toTypedArray())
            val testSuiteList = test_suite_options_list
            testSuiteList.layoutManager = LinearLayoutManager(this@MediaAppTestingActivity)
            testSuiteList.setHasFixedSize(true)
            testSuiteList.adapter = testSuiteAdapter
        }

        override fun onControllerPlaybackStateChanged(playbackState: String) {
            playbackStateText.text = playbackState
        }

        override fun onControllerMetadataChanged(metadata: String) {
            metadataText.text = metadata
        }

        override fun onControllerRepeatModeChanged(repeatMode: String) {
            repeatModeText.text = repeatMode
        }

        override fun onControllerShuffleModeChanged(shuffleMode: String) {
            shuffleModeText.text = shuffleMode
        }

        override fun onControllerQueueTitleChanged(title: CharSequence?) {
            queueTitleText.text = title
        }

        override fun onControllerQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>?) {
            queueText.text = getString(R.string.queue_size, queue?.size ?: 0)
            queueText.setTextAppearance(applicationContext, R.style.SubText)
            populateQueue(queue)
        }
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

        mediaAppTestService?.let {
            if (item.itemId == R.id.logs_toggle) {
                if (mediaAppTestService!!.getPrintLogsFormatted()) {
                    item.icon = ContextCompat.getDrawable(this, R.drawable.ic_parsable_logs_24dp)
                    Log.i(TAG, getString(R.string.logs_activate_parsable))
                    showToast(getString(R.string.logs_activate_parsable))
                    mediaAppTestService!!.setPrintLogsFormatted(false)
                } else {
                    item.icon = ContextCompat.getDrawable(this, R.drawable.ic_formatted_logs_24dp)
                    Log.i(TAG, getString(R.string.logs_activate_formatted))
                    showToast(getString(R.string.logs_activate_formatted))
                    mediaAppTestService!!.setPrintLogsFormatted(true)
                }
            } else if (item.itemId == R.id.logs_trigger) {
                mediaAppTestService!!.logCurrentController()
                Log.i(TAG, getString(R.string.logs_triggered))
                showToast(getString(R.string.logs_triggered))
            }
            return true
        }
        Log.i(TAG, getString(R.string.tests_service_not_bound))
        showToast(getString(R.string.tests_service_not_bound))

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

    // Adapter to display test suite details
    inner class TestSuiteAdapter(
            private val testSuites: Array<MediaAppTestSuite>
    ) : RecyclerView.Adapter<TestSuiteAdapter.ViewHolder>() {
        inner class ViewHolder(val cardView: CardView) : RecyclerView.ViewHolder(cardView)

        override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int
        ): TestSuiteAdapter.ViewHolder {
            val cardView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.media_test_option, parent, false) as CardView
            return ViewHolder(cardView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {

            val testSuite = testSuites[position]
            holder.cardView.card_header.text = testSuite.testSuiteName
            holder.cardView.card_text.text = testSuite.testSuiteDescription
            holder.cardView.card_button.text = resources.getText(R.string.run_suite_button)

            val configurableTests = testSuite.getConfigurableTests()
            if (!configurableTests.isEmpty()) {
                holder.cardView.configure_test_suite_button.visibility = View.VISIBLE
                holder.cardView.configure_test_suite_button.setOnClickListener {
                    val configAdapter = ConfigurationAdapter(configurableTests)
                    val sharedPreferences = getSharedPreferences(SHARED_PREF_KEY_SUITE_CONFIG, Context.MODE_PRIVATE)
                    Dialog(this@MediaAppTestingActivity).apply {
                        // Init dialog
                        requestWindowFeature(Window.FEATURE_NO_TITLE)
                        setContentView(R.layout.test_suite_configure_dialog)
                        title.text = getString(R.string.configure_dialog_title, testSuite.testSuiteName)
                        subtitle.text = testSuite.testSuiteDescription
                        test_to_configure_list.layoutManager =
                                LinearLayoutManager(this@MediaAppTestingActivity)
                        test_to_configure_list.layoutParams.height =
                                getScreenHeightPx(this@MediaAppTestingActivity) / 2
                        test_to_configure_list.setHasFixedSize(true)
                        test_to_configure_list.adapter = configAdapter

                        // Reset config button clicked
                        reset_results_button.setOnClickListener {
                            sharedPreferences.edit().apply {
                                for (i in configurableTests.indices) {
                                    putString(configurableTests[i].name, NO_CONFIG)
                                    configAdapter.notifyItemChanged(i)
                                }
                            }.apply()
                            dismiss()
                        }

                        // Done button pressed
                        done_button.setOnClickListener {
                            dismiss()
                        }
                    }.show()
                }
            }

            holder.cardView.card_button.setOnClickListener {
                var numIter = test_suite_num_iter.text.toString().toIntOrNull()
                if (numIter == null) {
                    Toast.makeText(this@MediaAppTestingActivity,
                            getText(R.string.test_suite_error_invalid_iter), Toast.LENGTH_SHORT)
                            .show()
                } else if (numIter > MAX_NUM_ITER || numIter < 1) {
                    Toast.makeText(this@MediaAppTestingActivity,
                            getText(R.string.test_suite_error_invalid_iter), Toast.LENGTH_SHORT)
                            .show()
                } else {
                    if (mediaAppTestService != null) {
                        if (mediaAppTestService!!.isSuiteRunning()) {
                            Toast.makeText(this@MediaAppTestingActivity,
                                    getText(R.string.test_suite_already_running),
                                    Toast.LENGTH_SHORT).show()
                        } else {
                            val testList: Array<TestOptionDetails> = testSuite.getTestList()
                            resultsAdapter = ResultsAdapter(testList)
                            positionToIDMap = hashMapOf<Int, Int>()

                            for (i in testList.indices) {
                                positionToIDMap[i] = testList[i].id
                            }

                            var progressBar : ProgressBar
                            val dialog = Dialog(this@MediaAppTestingActivity)
                            dialog.apply {
                                setCancelable(false)
                                requestWindowFeature(Window.FEATURE_NO_TITLE)
                                setContentView(R.layout.run_suite_iter_dialog)
                                progressBar =
                                        findViewById<ProgressBar>(R.id.suite_iter_progress_bar)
                                                .apply {
                                                    max = numIter * testList.size
                                                    progress = -1
                                                }
                                findViewById<Button>(R.id.quit_suite_iter_button)
                                        .setOnClickListener{
                                            testSuite.interrupt()
                                            dismiss()
                                        }
                                window.setLayout(LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT)
                            }.show()

                            testSuite.runSuite(
                                    numIter,
                                    convertSharedPrefToConfigMap(testSuite.getTestList()),
                                    onStartTest = {
                                        progressBar.incrementProgressBy(1)
                                    },
                                    onFinishTestSuite = {
                                        iDToResultsMap = it
                                        dialog.dismiss()

                                        var testSuiteResultsLayout =
                                                test_suite_results_container as RecyclerView
                                        testSuiteResultsLayout.layoutManager =
                                                LinearLayoutManager(this@MediaAppTestingActivity)
                                        testSuiteResultsLayout.setHasFixedSize(true)
                                        testSuiteResultsLayout.adapter = resultsAdapter
                                    })
                        }
                    } else {
                        Toast.makeText(this@MediaAppTestingActivity,
                                getText(R.string.tests_service_not_bound), Toast.LENGTH_SHORT)
                                .show()
                    }
                }
            }
        }
        override fun getItemCount() = testSuites.size
    }

    private fun convertSharedPrefToConfigMap(testList: Array<TestOptionDetails>): HashMap<String, String> {
        val configMap = hashMapOf<String, String>()
        val sharedPreferences = getSharedPreferences(SHARED_PREF_KEY_SUITE_CONFIG, Context.MODE_PRIVATE)
        for (test in testList) {
            configMap[test.name] = sharedPreferences.getString(test.name, NO_CONFIG)
        }
        return configMap
    }

    // Adapter to display test suite details
    inner class ConfigurationAdapter(
            private val tests: ArrayList<TestOptionDetails>
    ) : RecyclerView.Adapter<ConfigurationAdapter.ViewHolder>() {
        inner class ViewHolder(val cardView: CardView) : RecyclerView.ViewHolder(cardView)

        override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int
        ): ConfigurationAdapter.ViewHolder {
            val cardView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.config_item, parent, false) as CardView

            return ViewHolder(cardView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val test = tests[position]
            holder.cardView.test_name_config.text = test.name
            val sharedPreferences = getSharedPreferences(SHARED_PREF_KEY_SUITE_CONFIG, Context.MODE_PRIVATE)
            holder.cardView.test_query_config.addTextChangedListener(object : TextWatcher {

                override fun afterTextChanged(s: Editable) {
                    val newText = holder.cardView.test_query_config.text.toString()
                    if (newText != "") {
                        sharedPreferences.edit().apply {
                            putString(test.name, holder.cardView.test_query_config.text.toString())
                        }.apply()
                    }
                }

                override fun beforeTextChanged(s: CharSequence, start: Int,
                                               count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence, start: Int,
                                           before: Int, count: Int) = Unit
            })

            val previousConfig = sharedPreferences.getString(test.name, NO_CONFIG)
            holder.cardView.test_query_config.setText((previousConfig))
            if (previousConfig == NO_CONFIG) {
                holder.cardView.test_query_config.setText("")
                holder.cardView.test_query_config.hint = "Query"
                return
            }
        }

        override fun getItemCount() = tests.size
    }

    // Adapter to display test details
    inner class TestOptionAdapter(
            private val tests: List<TestOptionDetails>,
            private val iDToPositionMap: HashMap<Int, Int>
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

        val callback = { result: TestResult, testId: Int, testLogs: ArrayList<String> ->
            tests[iDToPositionMap[testId]!!].testResult = result
            tests[iDToPositionMap[testId]!!].testLogs = testLogs
            notifyItemChanged(iDToPositionMap[testId]!!)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.cardView.card_header.text = tests[position].name
            holder.cardView.card_text.text = tests[position].desc
            holder.cardView.setCardBackgroundColor(
                    when (tests[position].testResult) {
                        TestResult.FAIL -> ResourcesCompat
                                .getColor(resources, R.color.test_result_fail, null)
                        TestResult.PASS -> ResourcesCompat
                                .getColor(resources, R.color.test_result_pass, null)
                        TestResult.OPTIONAL_FAIL -> ResourcesCompat
                                .getColor(resources, R.color.test_result_optional_fail, null)
                        else -> {
                            Color.WHITE
                        }
                    }
            )
            if (tests[position].testResult != TestResult.NONE) {
                resultsContainer.removeAllViews()
                for (line in tests[position].testLogs) {
                    val tv_newLine = TextView(applicationContext)
                    tv_newLine.text = line
                    tv_newLine.setTextAppearance(applicationContext, R.style.SubText)
                    resultsContainer.addView(tv_newLine)
                }
            }

            holder.cardView.card_button.setOnClickListener {
                tests[position].runTest(testsQuery.text.toString(), callback, tests[position].id)
            }
        }

        override fun getItemCount() = tests.size
    }

    // Adapter to display Queue Item information
    inner class QueueItemAdapter(
            private val items: MutableList<MediaSessionCompat.QueueItem>
    ) : RecyclerView.Adapter<QueueItemAdapter.ViewHolder>() {
        inner class ViewHolder(val linearLayout: LinearLayout) : RecyclerView.ViewHolder(linearLayout)

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

    /**
     *  Adapter to display each test and the number of associated successes.
     */
    inner class ResultsAdapter(
            private val tests: Array<TestOptionDetails>
    ) : RecyclerView.Adapter<ResultsAdapter.ViewHolder>() {
        inner class ViewHolder(val cardView: CardView) : RecyclerView.ViewHolder(cardView)

        fun update() {

        }

        override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int
        ): ResultsAdapter.ViewHolder {
            val cardView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.media_test_suite_result, parent, false) as CardView
            return ViewHolder(cardView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val testID = positionToIDMap[position]!!
            val testCaseResults = iDToResultsMap[testID]!!
            holder.cardView.card_header.text = tests[position].name
            holder.cardView.card_text.text = tests[position].desc
            holder.cardView.total_tests.text = testCaseResults.totalRuns.toString()
            holder.cardView.tests_passing.text = testCaseResults.numPassing.toString()
            if (testCaseResults.totalRuns == 0) {
                holder.cardView.setOnClickListener{}
                holder.cardView.tests_passing_header.text = getString(R.string.test_suite_config_needed_header)
                holder.cardView.setCardBackgroundColor(Color.GRAY)
                return
            }
            val passPercentage = testCaseResults.numPassing.toFloat() / testCaseResults.totalRuns.toFloat()
            holder.cardView.setCardBackgroundColor((argbEvaluator.evaluate(
                    passPercentage,
                    ResourcesCompat.getColor(resources, R.color.test_result_fail, null),
                    ResourcesCompat.getColor(resources, R.color.test_result_pass, null) )) as Int)
            val onResultsClickedListener = OnResultsClickedListener(testID, tests[position].name, tests[position].desc, this@MediaAppTestingActivity)
            holder.cardView.setOnClickListener(onResultsClickedListener)
        }

        override fun getItemCount() = tests.size
    }

    /**
     *  Listener for when a specific test result is clicked from the results adapter. Shows logs
     * for runs associated with the specific tests.
     */
    inner class OnResultsClickedListener(private val testId: Int, private val name: String, private val description: String, val context: Context?) : View.OnClickListener {

        override fun onClick(p0: View?) {
            var dialog = Dialog(context).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                setContentView(R.layout.test_suite_results_dialog)
                findViewById<TextView>(R.id.results_title).text = name
                findViewById<TextView>(R.id.results_subtitle).text = description

                // Add the passing text log section
                if(iDToResultsMap[testId]!!.passingLogs.isNotEmpty()) {
                    val passing_results_log = findViewById<LinearLayout>(R.id.passing_results_log)
                    passing_results_log.removeAllViews()
                    for (logsList in iDToResultsMap[testId]!!.passingLogs) {
                        passing_results_log.addView(TextView(context).apply {
                            text = resources.getString(R.string.test_iter_divider)
                            setTextAppearance(context, R.style.SubHeader)
                            gravity = Gravity.CENTER
                            setTextColor(ResourcesCompat.getColor(resources, R.color.test_result_pass, null))
                        })
                        for (line in logsList) {
                            var logLine = TextView(context).apply {
                                text = line

                                setTextAppearance(context, R.style.SubText)
                            }
                            passing_results_log.addView(logLine)
                        }
                    }
                } else {
                    findViewById<TextView>(R.id.passing_logs_header).visibility = View.GONE
                }

                // Add the passing text log section
                if(iDToResultsMap[testId]!!.failingLogs.isNotEmpty()) {
                    val failing_results_log = findViewById<LinearLayout>(R.id.failing_results_log)
                    failing_results_log.removeAllViews()
                    for (logsList in iDToResultsMap[testId]!!.failingLogs) {
                        failing_results_log.addView(TextView(context).apply {
                            text = resources.getString(R.string.test_iter_divider)
                            setTextAppearance(context, R.style.SubHeader)
                            gravity = Gravity.CENTER
                            setTextColor(ResourcesCompat.getColor(resources, R.color.test_result_fail, null))
                        })
                        for (line in logsList) {
                            var logLine = TextView(context).apply {
                                text = line
                                setTextAppearance(context, R.style.SubText)
                            }
                            failing_results_log.addView(logLine)
                        }
                    }
                }
                else {
                    findViewById<TextView>(R.id.failing_logs_header).visibility = View.GONE
                }
                findViewById<ScrollView>(R.id.results_scroll_view).layoutParams.height = (MediaAppTestingActivity.getScreenHeightPx(context) / 2).toInt()
                findViewById<Button>(R.id.close_results_button).setOnClickListener(View.OnClickListener { dismiss() })
            }.show()
        }
    }

    companion object {

        private const val TAG = "MediaAppTestingActivity"

        // Key names for external extras.
        private const val PACKAGE_NAME_EXTRA = "com.example.android.mediacontroller.PACKAGE_NAME"

        // Key name for Intent extras.
        private const val APP_DETAILS_EXTRA =
                "com.example.android.mediacontroller.APP_DETAILS_EXTRA"

        // The max number of test suite iterations
        private const val MAX_NUM_ITER = 10

        // Key name used for saving/restoring instance state.
        private const val STATE_APP_DETAILS_KEY =
                "com.example.android.mediacontroller.STATE_APP_DETAILS_KEY"

        // Shared pref key name for test suite config
        const val SHARED_PREF_KEY_SUITE_CONFIG = "mct-test-suite-config"

        // Shared pref suite no configuration setup
        const val NO_CONFIG = "no-conf"

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

        // Gets the current screen height in pixels
        fun getScreenHeightPx(context: Context): Int {
            val displayMetrics = DisplayMetrics()
            val windowManager = getSystemService(context, WindowManager::class.java)
                    ?: throw IllegalStateException("Could not get WindowManager")
            val display = windowManager.defaultDisplay
            display.getMetrics(displayMetrics)
            return displayMetrics.heightPixels
        }

        // Gets the current screen width in pixels
        fun getScreenWidthPx(context: Context): Int {
            val displayMetrics = DisplayMetrics()
            val windowManager = getSystemService(context, WindowManager::class.java)
                    ?: throw IllegalStateException("Could not get WindowManager")
            val display = windowManager.defaultDisplay
            display.getMetrics(displayMetrics)
            return displayMetrics.widthPixels
        }
    }
}
