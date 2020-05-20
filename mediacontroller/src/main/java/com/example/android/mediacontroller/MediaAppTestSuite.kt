package com.example.android.mediacontroller

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.media_test_option.view.card_header
import kotlinx.android.synthetic.main.media_test_option.view.card_text
import kotlinx.android.synthetic.main.media_test_suite_result.view.*
import java.util.concurrent.Semaphore
import kotlin.concurrent.thread


class MediaAppTestSuite(testSuiteName: String, testSuiteDescription: String, testList: Array<TestOptionDetails>, private val testSuiteResultsLayout: RecyclerView, context: Context): View.OnClickListener {
    val name = testSuiteName
    val description = testSuiteDescription
    private val singleSuiteTestList = testList
    val context = context
    private val resultsAdapter = ResultsAdapter(singleSuiteTestList)
    private val testSemaphore = Semaphore(1)
    private val mHandler = Handler(Looper.getMainLooper())
    private val iDToPositionMap = hashMapOf<Int, Int>()
    private val TAG = "MediaAppTestSuite"
    private var suiteRunning = false

    init {
        for(i in testList.indices){
            iDToPositionMap[testList[i].id] = i
        }
    }

    val callback = { result: TestResult, testId: Int, testLogs: ArrayList<String> ->
        val index = iDToPositionMap[testId]
        Log.i(TAG, "Finished Test: " + testList[index!!].name + " with result " + result)
        testList[index!!].testResult = result
        testList[index!!].testLogs = testLogs
        mHandler.post {   resultsAdapter.notifyItemChanged(index!!) }
        testSemaphore.release()


    }

    fun suiteIsRunning(): Boolean{
        return suiteRunning
    }

    fun runSuite(numIter: Int){
        suiteRunning = true
        resetTests()
        thread(start=true) {
            Looper.prepare()
            for (test in singleSuiteTestList) {
                testSemaphore.acquire()
                Log.i(TAG, "Starting test:" + test.name)
                test.runTest("", callback, test.id);

            }
            suiteRunning = false
        }
        displayResults()

    }

    private fun resetTests(){
        for(test in singleSuiteTestList){
            test.testResult = TestResult.NONE
            test.testLogs = Test.NO_LOGS
            mHandler.post { resultsAdapter.notifyItemChanged(test.id)}
        }
    }

    private fun displayResults(){
        testSuiteResultsLayout.layoutManager = LinearLayoutManager(context)
        testSuiteResultsLayout.setHasFixedSize(true)
        testSuiteResultsLayout.adapter = resultsAdapter
    }

    // Adapter to display test details
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
            holder.cardView.card_header.text = tests[position].name
            holder.cardView.card_text.text = tests[position].desc
            holder.cardView.total_tests.text = "1"
            if (tests[position].testResult == TestResult.PASS){
                holder.cardView.tests_passing.text = "1"
                holder.cardView.loading_bar.visibility = View.INVISIBLE
                holder.cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.test_result_pass))
            }
            else if(tests[position].testResult == TestResult.FAIL){
                holder.cardView.tests_passing.text = "0"
                holder.cardView.loading_bar.visibility = View.INVISIBLE
                holder.cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.test_result_fail))
            }
            else{
                holder.cardView.tests_passing.text = "?"
                holder.cardView.loading_bar.visibility = View.VISIBLE
                holder.cardView.setCardBackgroundColor(Color.WHITE)
            }
            val onResultsClickedListener = OnResultsClickedListener(tests[position], this@MediaAppTestSuite.context)
            holder.cardView.setOnClickListener(onResultsClickedListener)
        }

        override fun getItemCount() = tests.size
    }


    override fun onClick(p0: View?) {

    }
    class OnResultsClickedListener(private val testDetails: TestOptionDetails, val context: Context): View.OnClickListener{

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
                for (line in testDetails.testLogs){
                    val tv_newLine = TextView(context)
                    tv_newLine.text = line
                    results_log.addView(tv_newLine)
                }
            }
            dialog.show()
        }
    }
}