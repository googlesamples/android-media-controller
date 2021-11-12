/*
 * Copyright 2020 Google Inc. All rights reserved.
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
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ProgressBar
import android.widget.Button
import android.widget.ScrollView
import androidx.annotation.NonNull
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.android.mediacontroller.databinding.ActivityMediaAppTestingBinding
import com.example.android.mediacontroller.databinding.MediaTestSuiteResultBinding
import com.example.android.mediacontroller.databinding.RunSuiteIterDialogBinding
import com.example.android.mediacontroller.databinding.TestSuiteResultsDialogBinding

import java.util.concurrent.Semaphore
import kotlin.concurrent.thread


class MediaAppTestSuite(val testSuiteName: String, val testSuiteDescription: String, private val testList:
    Array<TestOptionDetails>, private val testSuiteResultsLayout: RecyclerView, val context: Context) {

    private val TAG = "MediaAppTestSuite"

    /**
     * Sleep time after a single test was run. Assures that all steps are flushed out.
     */
    private val SLEEP_TIME = 1000L

    /**
     * The color that is associated with passing tests.
     */
    private val PASSING_COLOR = ContextCompat.getColor(context, R.color.test_result_pass)

    /**
     * The color that is associated with failing tests.
     */
    private val FAILING_COLOR = ContextCompat.getColor(context, R.color.test_result_fail)

    /**
     * Adapter for displaying individual tests results.
     */
    private var resultsAdapter = ResultsAdapter(this.testList)

    /**
     * Semaphore to prevent two tests from being run at the same time and interfering with each other.
     */
    private val testSemaphore = Semaphore(1)

    /**
     * Main thread handler.
     */
    private val mHandler = Handler(Looper.getMainLooper())

    /**
     * Map relating tests positioning in the adapter to the tests ID.
     */
    private val positionToIDMap = hashMapOf<Int, Int>()

    /**
     * Map relating the tests ID to the result struct.
     */
    private val iDToResultsMap = hashMapOf<Int, TestCaseResults>()

    /**
     * Boolean to prevent more than one suite from being running at a single time
     */
    private var suiteRunning = false

    /**
     * Object that is used to generate colors for partly passing tests.
     */
    private var argbEvaluator = ArgbEvaluator()

    /**
     * Used to retrieve test configurations.
     */
    private val sharedPreferences: SharedPreferences

    /**
     * Thread to handle all of the test suite operations.
     */
    private lateinit var suiteThread: Thread

    init {
        // Resets all of the test case results and gets config data.
        for (i in testList.indices) {
            positionToIDMap[i] = testList[i].id
            iDToResultsMap[testList[i].id] = TestCaseResults()
        }
        sharedPreferences = context.getSharedPreferences(MediaAppTestingActivity.SHARED_PREF_KEY_SUITE_CONFIG, Context.MODE_PRIVATE)
    }

    /**
     * Gets all of the tests that can be configured from a specific suite.
     */
    fun getConfigurableTests(): ArrayList<TestOptionDetails> {
        var configTests = ArrayList<TestOptionDetails>()
        for (test in testList) {
            if (test.queryRequired) {
                configTests.add(test)
            }
        }
        return configTests
    }

    /**
     * Returns true if the test suite is currently running.
     */
    fun suiteIsRunning(): Boolean {
        return suiteRunning
    }

    /**
     * Runs a single test suite a specified number of iterations.
     *
     * @param numIter - The number of iterations to run the test suite.
     */
    fun runSuite(numIter: Int) {
        resetTests()
        var progressBar :ProgressBar
        val dialog = Dialog(context)
        dialog.apply {
            setCancelable(false)
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            var binding = RunSuiteIterDialogBinding.inflate(layoutInflater)
            setContentView(binding.root)
            progressBar = findViewById<ProgressBar>(R.id.suite_iter_progress_bar).apply {
                max = numIter * testList.size
                progress = -1
            }
            findViewById<Button>(R.id.quit_suite_iter_button).setOnClickListener{
                interrupt()
                dismiss()
            }
            window!!.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }.show()
        runSuiteImpl(dialog, progressBar, numIter)
    }

    /**
     * Runs the full test suite a fixed number of times.
     *
     * @param dialog - The loading dialog to close when the suite is finished running.
     * @param progressBar - The progress bar to update with the suites status.
     * @param numIter - The number of times to run the test suite.
     */
    private fun runSuiteImpl(dialog: Dialog, progressBar: ProgressBar, numIter: Int){
        resultsAdapter = ResultsAdapter(testList)
        suiteRunning = true
        suiteThread = thread(start = true) {
            Looper.prepare()
            try {
                for(i in 0 until numIter) {
                    for (test in testList) {
                        resetSingleResults()
                        progressBar.incrementProgressBy(1)

                        // Flush out any residual media control commands from previous test
                        Thread.sleep(SLEEP_TIME)

                        // In the event that a query is not specified, don't run the test.
                        var query = sharedPreferences.getString(test.name, MediaAppTestingActivity.NO_CONFIG)!!
                        if (test.queryRequired && query == MediaAppTestingActivity.NO_CONFIG) {
                            test.testResult = TestResult.CONFIG_REQUIRED
                            val index = positionToIDMap[test.id]
                            mHandler.post { resultsAdapter.notifyItemChanged(index!!) }
                            continue
                        }

                        if (query == MediaAppTestingActivity.NO_CONFIG) {
                            query = ""
                        }
                        testSemaphore.acquire()
                        test.runTest(query, callback, test.id)
                    }
                }
            } catch(e: InterruptedException){
                Thread.currentThread().interrupt()
            }
            suiteRunning = false
            mHandler.post {
                Log.i(TAG, "Starting to display results")
                displayResults()
                Log.i(TAG, "Finished displaying results")
                dialog.dismiss()
            }
        }
    }

    /**
     * A call back function for when a test has finished. Releases the test semaphore
     *
     * @param result - A TestResult result code.
     * @param testId - The specific identifier associated with the finished tests.
     * @param testLogs - The logs associated with the finished test.
     */
    private val callback = { result: TestResult, testId: Int, testLogs: ArrayList<String> ->
        val testCaseResults = iDToResultsMap[testId]!!
        Log.d(TAG, "Finished Test: $testId with result $result")
        testCaseResults.totalRuns += 1
        when (result) {
            TestResult.PASS -> {
                testCaseResults.numPassing += 1
                testCaseResults.passingLogs.add(testLogs)
            }
            TestResult.FAIL -> {
                testCaseResults.failingLogs.add(testLogs)
            }
            TestResult.OPTIONAL_FAIL -> {
                testCaseResults.failingLogs.add(testLogs)
            }
            TestResult.CONFIG_REQUIRED -> {
                testCaseResults.totalRuns -= 1
            }
            else -> {
                Log.d(TAG, "There was an error with $testId return code")
            }
        }
        testSemaphore.release()
    }

    /**
     * Interrupts the test suite and resets its all of its results.
     */
    private fun interrupt(){
        if (!this::suiteThread.isInitialized){
            return
        }
        suiteThread.interrupt()
        suiteRunning = false
        resetTests()
    }

    /**
     * Resets a single test result.
     */
    private fun resetSingleResults(){
        for (test in testList) {
            test.testResult = TestResult.NONE
            test.testLogs = Test.NO_LOGS
        }
    }

    /**
     * Resets all of the test results associated with the suite.
     */
    private fun resetTests() {
        resetSingleResults()
        for (test in testList) {
            iDToResultsMap[test.id] = TestCaseResults()
        }
        resultsAdapter = ResultsAdapter(arrayOf())
        testSuiteResultsLayout.removeAllViews()
        displayResults()
    }


    /**
     * Displays the test suite results using a RecyclerView.
     */
    private fun displayResults() {
        testSuiteResultsLayout.layoutManager = LinearLayoutManager(context)
        testSuiteResultsLayout.setHasFixedSize(true)
        testSuiteResultsLayout.adapter = resultsAdapter
    }

    /**
     * Class to store all of a tests resulting info.
     */
    inner class TestCaseResults {
        var totalRuns = 0
        var numPassing = 0
        var passingLogs = arrayListOf<ArrayList<String>>()
        var failingLogs = arrayListOf<ArrayList<String>>()
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
            if(testCaseResults.totalRuns == 0){
                holder.cardView.cardView.setOnClickListener{}
                holder.cardView.testsPassingHeader.text = context.getString(R.string.test_suite_config_needed_header)
                holder.cardView.cardView.setCardBackgroundColor(Color.GRAY)
                return
            }
            val passPercentage = testCaseResults.numPassing.toFloat() / testCaseResults.totalRuns.toFloat()
            holder.cardView.cardView.setCardBackgroundColor((argbEvaluator.evaluate(passPercentage, FAILING_COLOR, PASSING_COLOR )) as Int)
            val onResultsClickedListener = OnResultsClickedListener(testID, tests[position].name, tests[position].desc, this@MediaAppTestSuite.context)
            holder.cardView.cardView.setOnClickListener(onResultsClickedListener)
        }

        override fun getItemCount() = tests.size
    }

    /**
     *  Listener for when a specific test result is clicked from the results adapter. Shows logs
     * for runs associated with the specific tests.
     */
    inner class OnResultsClickedListener(private val testId: Int, private val name: String, private val description: String, val context: Context) : View.OnClickListener {

        override fun onClick(p0: View?) {
            var dialog = Dialog(context).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)

                val binding = TestSuiteResultsDialogBinding.inflate(layoutInflater)
                setContentView(binding.root)
                findViewById<TextView>(R.id.results_title).text = name
                findViewById<TextView>(R.id.results_subtitle).text = description

                // Add the passing text log section
                if(iDToResultsMap[testId]!!.passingLogs.isNotEmpty()) {
                    val passing_results_log = findViewById<LinearLayout>(R.id.passing_results_log)
                    passing_results_log.removeAllViews()
                    for (logsList in iDToResultsMap[testId]!!.passingLogs) {
                        passing_results_log.addView(TextView(context).apply {
                            text = resources.getString(R.string.test_iter_divider)
                            TextViewCompat.setTextAppearance(this, R.style.SubHeader)
                            gravity = Gravity.CENTER
                            setTextColor(PASSING_COLOR)
                        })
                        for (line in logsList) {
                            val logLine = TextView(context).apply {
                                text = line

                                TextViewCompat.setTextAppearance(this, R.style.SubText)
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
                            TextViewCompat.setTextAppearance(this, R.style.SubHeader)
                            gravity = Gravity.CENTER
                            setTextColor(FAILING_COLOR)
                        })
                        for (line in logsList) {
                            val logLine = TextView(context).apply {
                                text = line
                                TextViewCompat.setTextAppearance(this, R.style.SubText)
                            }
                            failing_results_log.addView(logLine)
                        }
                    }
                }
                else {
                    findViewById<TextView>(R.id.failing_logs_header).visibility = View.GONE
                }
                findViewById<ScrollView>(R.id.results_scroll_view).layoutParams.height = (MediaAppTestingActivity.getScreenHeightPx(context) / 2).toInt()
                findViewById<Button>(R.id.close_results_button).setOnClickListener { dismiss() }
            }.show()
        }
    }
}