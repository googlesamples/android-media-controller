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
import android.util.Log
import android.view.*
import android.widget.*

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager

import com.example.android.mediacontroller.databinding.*

class MediaAppTestingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMediaAppTestingBinding
    private var mediaAppTestService: MediaAppTestService? = null
    private var isBinding = false
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMediaAppTestingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val toolbar: Toolbar = binding.toolbar
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        Test.androidResources = resources

        // Set up page navigation
        val pages = arrayOf(
            binding.mediaControllerInfoPage,
            binding.mediaControllerTestPage,
            binding.mediaControllerTestSuitePage
        )
        // Remove so that instantiateItem can add at the right time
        pages.forEach {
            (it.root.parent as ViewGroup).removeView(it.root)
        }
        binding.viewPager.offscreenPageLimit = pages.size
        binding.viewPager.adapter = object : PagerAdapter() {
            override fun getCount(): Int {
                return pages.size
            }

            override fun isViewFromObject(view: View, item: Any): Boolean {
                return view == item
            }

            override fun instantiateItem(container: ViewGroup, position: Int): Any {
                container.addView(pages[position].root)
                return pages[position].root
            }
        }
        binding.bottomNavigationView.setOnItemSelectedListener { item: MenuItem ->
            return@setOnItemSelectedListener when (item.itemId) {
                R.id.info_bottom_nav -> {
                    binding.viewPager.currentItem = 0
                    true
                }

                R.id.test_bottom_nav -> {
                    binding.viewPager.currentItem = 1
                    true
                }

                R.id.test_suite_bottom_nav -> {
                    binding.viewPager.currentItem = 2
                    true
                }
                else -> false
            }
        }
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {

            override fun onPageScrollStateChanged(state: Int) {
            }

            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int
            ) {

            }

            override fun onPageSelected(position: Int) {
                binding.bottomNavigationView.menu.getItem(position).isChecked = true
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
                binding.viewPager.visibility = View.VISIBLE
            } else {
                binding.viewPager.visibility = View.GONE
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
                binding.viewPager.visibility = View.VISIBLE
            } else {
                binding.viewPager.visibility = View.GONE
            }
        }
    }

    private val callback = object : MediaAppTestService.ICallback {
        override fun onConnected() {
            binding.mediaControllerInfoPage.connectionErrorText.visibility = View.GONE
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
            val testOptionsList = binding.mediaControllerTestPage.testOptionsList
            testOptionsList.layoutManager = LinearLayoutManager(this@MediaAppTestingActivity)
            testOptionsList.setHasFixedSize(true)
            testOptionsList.adapter = testOptionAdapter

            val testSuiteAdapter = TestSuiteAdapter(testSuites.toTypedArray())
            val testSuiteList = binding.mediaControllerTestSuitePage.testSuiteOptionsList
            testSuiteList.layoutManager = LinearLayoutManager(this@MediaAppTestingActivity)
            testSuiteList.setHasFixedSize(true)
            testSuiteList.adapter = testSuiteAdapter
        }

        override fun onControllerPlaybackStateChanged(playbackState: String) {
            binding.mediaControllerInfoPage.playbackStateText.text = playbackState
        }

        override fun onControllerMetadataChanged(metadata: String) {
            binding.mediaControllerInfoPage.metadataText.text = metadata
        }

        override fun onControllerRepeatModeChanged(repeatMode: String) {
            binding.mediaControllerInfoPage.repeatModeText.text = repeatMode
        }

        override fun onControllerShuffleModeChanged(shuffleMode: String) {
            binding.mediaControllerInfoPage.shuffleModeText.text = shuffleMode
        }

        override fun onControllerQueueTitleChanged(title: CharSequence?) {
            binding.mediaControllerInfoPage.queueTitleText.text = title
        }

        override fun onControllerQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>?) {
            binding.mediaControllerInfoPage.queueText.text = getString(R.string.queue_size, queue?.size ?: 0)
            binding.mediaControllerInfoPage.queueText.setTextAppearance(applicationContext, R.style.SubText)
            populateQueue(queue)
        }
    }

    private fun populateQueue(_queue: MutableList<MediaSessionCompat.QueueItem>?) {
        val queue = _queue ?: emptyList<MediaSessionCompat.QueueItem>().toMutableList()
        val queueItemAdapter = QueueItemAdapter(queue)
        val queueList = binding.mediaControllerInfoPage.queueItemList
        queueList.layoutManager = object : LinearLayoutManager(this) {
            override fun canScrollVertically(): Boolean = false
        }
        queueList.adapter = queueItemAdapter
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showError(message: String) {
        binding.mediaControllerInfoPage.connectionErrorText.text = message
        binding.mediaControllerInfoPage.connectionErrorText.visibility = View.VISIBLE
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.testing, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

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
        inner class ViewHolder(val cardView: MediaTestOptionBinding) : RecyclerView.ViewHolder(cardView.root)

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): TestSuiteAdapter.ViewHolder {
            val cardView = MediaTestOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(cardView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {

            val testSuite = testSuites[position]
            holder.cardView.cardHeader.text = testSuite.testSuiteName
            holder.cardView.cardText.text = testSuite.testSuiteDescription
            holder.cardView.cardButton.text = resources.getText(R.string.run_suite_button)

            val configurableTests = testSuite.getConfigurableTests()
            if (configurableTests.isNotEmpty()) {
                holder.cardView.configureTestSuiteButton.visibility = View.VISIBLE
                holder.cardView.configureTestSuiteButton.setOnClickListener {
                    val configAdapter = ConfigurationAdapter(configurableTests)
                    val sharedPreferences = getSharedPreferences(SHARED_PREF_KEY_SUITE_CONFIG, Context.MODE_PRIVATE)
                    Dialog(this@MediaAppTestingActivity).apply {
                        // Init dialog
                        requestWindowFeature(Window.FEATURE_NO_TITLE)
                        val dialog =
                            TestSuiteConfigureDialogBinding.inflate(layoutInflater)
                        setContentView(dialog.root)
                        dialog.title.text = getString(R.string.configure_dialog_title, testSuite.testSuiteName)
                        dialog.subtitle.text = testSuite.testSuiteDescription
                        dialog.testToConfigureList.layoutManager = LinearLayoutManager(this@MediaAppTestingActivity)
                        dialog.testToConfigureList.layoutParams.height = getScreenHeightPx(this@MediaAppTestingActivity) / 2
                        dialog.testToConfigureList.setHasFixedSize(true)
                        dialog.testToConfigureList.adapter = configAdapter

                        // Reset config button clicked
                        dialog.resetResultsButton.setOnClickListener {
                            sharedPreferences.edit().apply {
                                for (i in configurableTests.indices) {
                                    putString(configurableTests[i].name, NO_CONFIG)
                                    configAdapter.notifyItemChanged(i)
                                }
                            }.apply()
                            dismiss()
                        }

                        // Done button pressed
                        dialog.doneButton.setOnClickListener {
                            dismiss()
                        }
                    }.show()
                }
            }

            holder.cardView.cardButton.setOnClickListener {
                val numIter = this@MediaAppTestingActivity.binding.mediaControllerTestSuitePage.testSuiteNumIter.text.toString().toIntOrNull()
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
                                window?.setLayout(LinearLayout.LayoutParams.MATCH_PARENT,
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

                                    binding.mediaControllerTestSuitePage.testSuiteResultsContainer.layoutManager =
                                        LinearLayoutManager(this@MediaAppTestingActivity)
                                    binding.mediaControllerTestSuitePage.testSuiteResultsContainer.setHasFixedSize(true)
                                    binding.mediaControllerTestSuitePage.testSuiteResultsContainer.adapter = resultsAdapter
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
            configMap[test.name] = sharedPreferences.getString(test.name, NO_CONFIG)?: ""
        }
        return configMap
    }

    // Adapter to display test suite details
    inner class ConfigurationAdapter(
        private val tests: ArrayList<TestOptionDetails>
    ) : RecyclerView.Adapter<ConfigurationAdapter.ViewHolder>() {
        inner class ViewHolder(val cardView: ConfigItemBinding) : RecyclerView.ViewHolder(cardView.root)

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): ConfigurationAdapter.ViewHolder {
            val cardView = ConfigItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(cardView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val test = tests[position]
            holder.cardView.testNameConfig.text = test.name
            val sharedPreferences = getSharedPreferences(SHARED_PREF_KEY_SUITE_CONFIG, Context.MODE_PRIVATE)
            holder.cardView.testQueryConfig.addTextChangedListener(object : TextWatcher {

                override fun afterTextChanged(s: Editable) {
                    val newText = holder.cardView.testQueryConfig.text.toString()
                    if (newText != "") {
                        sharedPreferences.edit().apply {
                            putString(test.name, holder.cardView.testQueryConfig.text.toString())
                        }.apply()
                    }
                }

                override fun beforeTextChanged(s: CharSequence, start: Int,
                                               count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence, start: Int,
                                           before: Int, count: Int) = Unit
            })

            val previousConfig = sharedPreferences.getString(test.name, NO_CONFIG)
            holder.cardView.testQueryConfig.setText((previousConfig))
            if (previousConfig == NO_CONFIG) {
                holder.cardView.testQueryConfig.setText("")
                holder.cardView.testQueryConfig.hint = "Query"
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
        inner class ViewHolder(val cardView: MediaTestOptionBinding) : RecyclerView.ViewHolder(cardView.root)

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): TestOptionAdapter.ViewHolder {
            val cardView = MediaTestOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(cardView)
        }

        val callback = { result: TestResult, testId: Int, testLogs: ArrayList<String> ->
            tests[iDToPositionMap[testId]!!].testResult = result
            tests[iDToPositionMap[testId]!!].testLogs = testLogs
            notifyItemChanged(iDToPositionMap[testId]!!)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.cardView.cardHeader.text = tests[position].name
            holder.cardView.cardText.text = tests[position].desc
            holder.cardView.cardView.setCardBackgroundColor(
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
                binding.mediaControllerTestPage.testResultsContainer.removeAllViews()
                for (line in tests[position].testLogs) {
                    val tvNewLine = TextView(applicationContext)
                    tvNewLine.text = line
                    TextViewCompat.setTextAppearance(tvNewLine, R.style.SubText)
                    binding.mediaControllerTestPage.testResultsContainer.addView(tvNewLine)
                }
            }

            holder.cardView.cardButton.setOnClickListener {
                tests[position].runTest(binding.mediaControllerTestPage.testsQuery.text.toString(), callback, tests[position].id)
            }
        }

        override fun getItemCount() = tests.size
    }

    // Adapter to display Queue Item information
    inner class QueueItemAdapter(
        private val items: MutableList<MediaSessionCompat.QueueItem>
    ) : RecyclerView.Adapter<QueueItemAdapter.ViewHolder>() {
        inner class ViewHolder(val linearLayout: MediaQueueItemBinding) : RecyclerView.ViewHolder(linearLayout.root)

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): ViewHolder {
            val linearLayout = MediaQueueItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(linearLayout)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.linearLayout.queueId.text =
                holder.linearLayout.root.context.getString(
                    R.string.queue_item_id,
                    items[position].queueId
                )

            val description = items[position].description
            holder.linearLayout.descriptionTitle.text =
                holder.linearLayout.root.context.getString(
                    R.string.queue_item_title,
                    description.title
                )
            holder.linearLayout.descriptionSubtitle.text =
                holder.linearLayout.root.context.getString(
                    R.string.queue_item_subtitle,
                    description.subtitle
                )
            holder.linearLayout.descriptionId.text =
                holder.linearLayout.root.context.getString(
                    R.string.queue_item_media_id,
                    description.mediaId
                )
            holder.linearLayout.descriptionUri.text =
                holder.linearLayout.root.context.getString(
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
        inner class ViewHolder(val cardView: MediaTestSuiteResultBinding) : RecyclerView.ViewHolder(cardView.root)

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): ResultsAdapter.ViewHolder {
            val cardView = MediaTestSuiteResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)

            return ViewHolder(cardView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val testID = positionToIDMap[position]!!
            val testCaseResults = iDToResultsMap[testID]!!
            holder.cardView.cardHeader.text = tests[position].name
            holder.cardView.cardText.text = tests[position].desc
            holder.cardView.totalTests.text = testCaseResults.totalRuns.toString()
            holder.cardView.testsPassing.text = testCaseResults.numPassing.toString()
            if (testCaseResults.totalRuns == 0) {
                holder.cardView.cardView.setOnClickListener{}
                holder.cardView.testsPassingHeader.text = getString(R.string.test_suite_config_needed_header)
                holder.cardView.cardView.setCardBackgroundColor(Color.GRAY)
                return
            }
            val passPercentage = testCaseResults.numPassing.toFloat() / testCaseResults.totalRuns.toFloat()
            holder.cardView.cardView.setCardBackgroundColor((argbEvaluator.evaluate(
                passPercentage,
                ResourcesCompat.getColor(resources, R.color.test_result_fail, null),
                ResourcesCompat.getColor(resources, R.color.test_result_pass, null) )) as Int)
            val onResultsClickedListener = OnResultsClickedListener(testID, tests[position].name, tests[position].desc, this@MediaAppTestingActivity)
            holder.cardView.cardView.setOnClickListener(onResultsClickedListener)
        }

        override fun getItemCount() = tests.size
    }

    /**
     *  Listener for when a specific test result is clicked from the results adapter. Shows logs
     * for runs associated with the specific tests.
     */
    inner class OnResultsClickedListener(private val testId: Int, private val name: String, private val description: String, val context: Context?) : View.OnClickListener {

        override fun onClick(p0: View?) {
            var dialog = context?.let {
                Dialog(it).apply {
                    //TODO: refactor it using viewbinding
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
                    } else {
                        findViewById<TextView>(R.id.failing_logs_header).visibility = View.GONE
                    }
                    findViewById<ScrollView>(R.id.results_scroll_view).layoutParams.height = (MediaAppTestingActivity.getScreenHeightPx(context) / 2).toInt()
                    findViewById<Button>(R.id.close_results_button).setOnClickListener(View.OnClickListener { dismiss() })
                }.show()
            }
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
            return context.resources.displayMetrics.heightPixels
        }

        // Gets the current screen width in pixels
        fun getScreenWidthPx(context: Context): Int {
            return context.resources.displayMetrics.widthPixels
        }
    }
}
