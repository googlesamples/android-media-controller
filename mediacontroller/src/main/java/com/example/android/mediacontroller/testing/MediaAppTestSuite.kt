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
package com.example.android.mediacontroller.testing

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.android.mediacontroller.Test
import com.example.android.mediacontroller.TestOptionDetails
import com.example.android.mediacontroller.TestResult

import java.util.concurrent.Semaphore
import kotlin.concurrent.thread

class MediaAppTestSuite(val testSuiteName: String, val testSuiteDescription: String, private val testList:
Array<TestOptionDetails>) {

    private val TAG = "MediaAppTestSuite"

    /**
     * Sleep time after a single test was run. Assures that all steps are flushed out.
     */
    private val SLEEP_TIME = 1000L

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
     * Thread to handle all of the test suite operations.
     */
    private lateinit var suiteThread: Thread

    init {
        // Resets all of the test case results and gets config data.
        for (i in testList.indices) {
            positionToIDMap[i] = testList[i].id
            iDToResultsMap[testList[i].id] = TestCaseResults()
        }
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
     * Gets all of the tests in the test suite.
     */
    fun getTestList(): Array<TestOptionDetails> {
        return testList
    }

    /**
     * Runs the full test suite a fixed number of times.
     *
     * @param numIter - The number of times to run the test suite.
     */
    fun runSuite(numIter: Int, queries: HashMap<String, String>, onStartTest: () -> Unit,
                 onFinishTestSuite: (idToResultMap: HashMap<Int, TestCaseResults>) -> Unit) {
        resetTests()
        suiteRunning = true
        suiteThread = thread(start = true) {
            Looper.prepare()
            try {
                for(i in 0 until numIter) {
                    for (test in testList.sortedBy { it.id }) {
                        resetSingleResults()
                        onStartTest()

                        // Flush out any residual media control commands from previous test
                        Thread.sleep(SLEEP_TIME)

                        // In the event that a query is not specified, don't run the test.
                        var query = MediaAppTestingActivity.NO_CONFIG
                        query = queries[test.name] ?: MediaAppTestingActivity.NO_CONFIG
                        if (test.queryRequired && query == MediaAppTestingActivity.NO_CONFIG) {
                            test.testResult = TestResult.CONFIG_REQUIRED
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
                onFinishTestSuite(iDToResultsMap)
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
    fun interrupt(){
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
    }


    /**
     * Class to store all of a tests resulting info.
     */
    inner class TestCaseResults() {
        var totalRuns = 0
        var numPassing = 0
        var passingLogs = arrayListOf<ArrayList<String>>()
        var failingLogs = arrayListOf<ArrayList<String>>()
    }
}