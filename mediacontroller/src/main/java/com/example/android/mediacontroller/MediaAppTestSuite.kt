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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.animation.ArgbEvaluatorCompat
import kotlinx.android.synthetic.main.media_test_option.view.card_header
import kotlinx.android.synthetic.main.media_test_option.view.card_text
import kotlinx.android.synthetic.main.media_test_suite_result.view.*
import java.util.concurrent.Semaphore
import kotlin.concurrent.thread


class MediaAppTestSuite(testSuiteName: String, testSuiteDescription: String, testList:
    Array<TestOptionDetails>, private val testSuiteResultsLayout: RecyclerView, context: Context) {

    private val SLEEP_TIME = 1000L
    private val TAG = "MediaAppTestSuite"
    private val PASSING_COLOR = ContextCompat.getColor(context, R.color.test_result_pass)
    private val FAILING_COLOR = ContextCompat.getColor(context, R.color.test_result_fail)

    val name = testSuiteName
    val description = testSuiteDescription
    private val singleSuiteTestList = testList
    val context = context
    private var resultsAdapter = ResultsAdapter(singleSuiteTestList)
    private val testSemaphore = Semaphore(1)
    private val mHandler = Handler(Looper.getMainLooper())
    private val positionToIDMap = hashMapOf<Int, Int>()
    private val iDToResultsMap = hashMapOf<Int, TestCaseResults>()
    private var suiteRunning = false
    private var argbEvaluator = ArgbEvaluator()
    private val sharedPreferences: SharedPreferences
    private lateinit var suiteThread: Thread


    init {
        for (i in testList.indices) {
            positionToIDMap[i] = testList[i].id
            iDToResultsMap[testList[i].id] = TestCaseResults()
        }
        sharedPreferences = context.getSharedPreferences(MediaAppTestingActivity.SHARED_PREF_KEY_SUITE_CONFIG, Context.MODE_PRIVATE)
    }

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

    fun getConfigurableTests(): ArrayList<TestOptionDetails> {
        var configTests = ArrayList<TestOptionDetails>()
        for (test in singleSuiteTestList) {
            if (test.queryRequired) {
                configTests.add(test)
            }
        }
        return configTests
    }

    fun suiteIsRunning(): Boolean {
        return suiteRunning
    }

    fun runSuite(numIter: Int) {
        resetTests()
        var progressBar :ProgressBar
        val dialog = Dialog(context)
        dialog.apply {
            setCancelable(false)
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(R.layout.run_suite_iter_dialog)
            progressBar = findViewById<ProgressBar>(R.id.suite_iter_progress_bar).apply {
                max = numIter * singleSuiteTestList.size
                progress = -1
            }
            findViewById<Button>(R.id.quit_suite_iter_button).setOnClickListener{
                interrupt()
                dismiss()
            }
            window.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }.show()
        runSuiteImpl(dialog, progressBar, numIter)
    }

    private fun runSuiteImpl(dialog: Dialog, progressBar: ProgressBar, numIter: Int){
        resultsAdapter = ResultsAdapter(singleSuiteTestList)
        suiteRunning = true
        suiteThread = thread(start = true) {
            Looper.prepare()
            try {
                for(i in 0 until numIter) {
                    for (test in singleSuiteTestList) {
                        resetSingleResults()
                        progressBar.incrementProgressBy(1)

                        // Flush out any residual media control commands from previous test
                        Thread.sleep(SLEEP_TIME)

                        // In the event that a query is not specified, don't run the test.
                        var query = sharedPreferences.getString(test.name, MediaAppTestingActivity.NO_CONFIG)
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

    private fun interrupt(){
        if (!this::suiteThread.isInitialized){
            return
        }
        suiteThread.interrupt()
        suiteRunning = false
        resetTests()
    }

    private fun resetSingleResults(){
        for (test in singleSuiteTestList) {
            test.testResult = TestResult.NONE
            test.testLogs = Test.NO_LOGS
        }
    }

    private fun resetTests() {
        resetSingleResults()
        for (test in singleSuiteTestList) {
            iDToResultsMap[test.id] = TestCaseResults()
        }
        resultsAdapter = ResultsAdapter(arrayOf<TestOptionDetails>())
        testSuiteResultsLayout.removeAllViews()
        displayResults()
    }

    private fun displayResults() {
        testSuiteResultsLayout.layoutManager = LinearLayoutManager(context)
        testSuiteResultsLayout.setHasFixedSize(true)
        testSuiteResultsLayout.adapter = resultsAdapter
    }

    inner class TestCaseResults() {
        var totalRuns = 0
        var numPassing = 0
        var passingLogs = arrayListOf<ArrayList<String>>()
        var failingLogs = arrayListOf<ArrayList<String>>()
    }

    // Adapter to display test result details
    inner class ResultsAdapter(
            private val tests: Array<TestOptionDetails>
    ) : RecyclerView.Adapter<ResultsAdapter.ViewHolder>() {
        inner class ViewHolder(val cardView: CardView) : RecyclerView.ViewHolder(cardView)

        override fun onCreateViewHolder(
                parent: ViewGroup,
                viewType: Int
        ): ResultsAdapter.ViewHolder {
            val cardView = LayoutInflater.from(parent.context)
                    .inflate(R.layout.media_test_suite_result, parent, false) as CardView
            return ViewHolder(cardView)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val testID = positionToIDMap[position]
            val testCaseResults = iDToResultsMap[testID]!!
            holder.cardView.card_header.text = tests[position].name
            holder.cardView.card_text.text = tests[position].desc
            holder.cardView.total_tests.text = testCaseResults.totalRuns.toString()
            holder.cardView.tests_passing.text = testCaseResults.numPassing.toString()
            if(testCaseResults.totalRuns == 0){
                holder.cardView.tests_passing_header.text = context.getString(R.string.test_suite_config_needed_header)
                holder.cardView.setCardBackgroundColor(Color.GRAY)
                return
            }
            val passPercentage = testCaseResults.numPassing.toFloat() / testCaseResults.totalRuns.toFloat()
            holder.cardView.setCardBackgroundColor((argbEvaluator.evaluate(passPercentage, FAILING_COLOR, PASSING_COLOR )) as Int)
            //val onResultsClickedListener = OnResultsClickedListener(testID, this@MediaAppTestSuite.context)
            //holder.cardView.setOnClickListener(onResultsClickedListener)
        }

        override fun getItemCount() = tests.size
    }

/*
    inner class OnResultsClickedListener(private val testId: Int, val context: Context) : View.OnClickListener {

        override fun onClick(p0: View?) {
            var dialog = Dialog(context)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.test_suite_results_dialog)
            val results_title = dialog.findViewById(R.id.results_title) as TextView
            results_title.text = testDetails.name
            val results_subtitle = dialog.findViewById(R.id.results_subtitle) as TextView
            results_subtitle.text = testDetails.desc
            val results_log = dialog.findViewById(R.id.results_log) as LinearLayout
            if (testDetails.testLogs != Test.NO_LOGS) {
                results_log.removeAllViews()
                for (line in testDetails.testLogs) {
                    val tv_newLine = TextView(context)
                    tv_newLine.text = line
                    tv_newLine.setTextAppearance(context, R.style.SubText)
                    results_log.addView(tv_newLine)
                }
            }
            val close_button = dialog.findViewById(R.id.close_results_button) as Button
            val results_scroll_view = dialog.findViewById(R.id.results_scroll_view) as ScrollView
            results_scroll_view.layoutParams.height = (MediaAppTestingActivity.getScreenHeightPx(context) / 2).toInt()
            close_button.setOnClickListener(View.OnClickListener { dialog.dismiss() })
            dialog.show()
        }
    }
    */
}