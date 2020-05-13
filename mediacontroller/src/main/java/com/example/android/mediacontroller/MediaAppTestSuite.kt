package com.example.android.mediacontroller

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.media_test_option.view.*
import kotlinx.android.synthetic.main.media_test_option.view.card_header
import kotlinx.android.synthetic.main.media_test_option.view.card_text
import kotlinx.android.synthetic.main.media_test_suite_result.view.*
import org.w3c.dom.Text
import java.text.DateFormat
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

class MediaAppTestSuite(testSuiteName: String, testSuiteDescripton: String, testList: Array<TestOptionDetails>, testSuiteResultsLayout: RecyclerView, context: Context): View.OnClickListener {
    val name = testSuiteName
    val description = testSuiteDescripton
    val singleSuiteTestList = testList
    val testSuiteResultsLayout = testSuiteResultsLayout
    val context = context
    val resultsAdapter = ResultsAdapter(singleSuiteTestList)

    val callback = { result: TestResult, testId: Int ->
        Log.i(name, "Finished Test: " + testList[testId].name + " with result " + result)
        testList[testId].testResult = result
        resultsAdapter.notifyItemChanged(testId)
    }

    fun runSuite(numIter: Int){


        for (test in singleSuiteTestList){
            test.runTest("", callback, test.id)
        }
        displayResults()

    }

    fun displayResults(){
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
                holder.cardView.setCardBackgroundColor(Color.GREEN)
            }
            else if(tests[position].testResult == TestResult.FAIL){
                holder.cardView.tests_passing.text = "0"
                holder.cardView.setCardBackgroundColor(Color.RED)
            }
            else{
                holder.cardView.tests_passing.text = "?"
                holder.cardView.setCardBackgroundColor(Color.YELLOW)
            }
            val onResultsClickedListener = OnResultsClickedListener(tests[position], this@MediaAppTestSuite.context)
            holder.cardView.setOnClickListener(onResultsClickedListener)
        }

        override fun getItemCount() = tests.size
    }


    override fun onClick(p0: View?) {

    }
    class OnResultsClickedListener(testDetails: TestOptionDetails, context: Context): View.OnClickListener{
        val testDetails = testDetails
        val context = context

        override fun onClick(p0: View?) {
            var dialog = Dialog(context)
            dialog .requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.test_suite_results_dialog)
            val results_title = dialog.findViewById(R.id.results_title) as TextView
            results_title.text = testDetails.name
            val results_subtitle = dialog.findViewById(R.id.results_subtitle) as TextView
            results_subtitle.text = testDetails.desc
            val results_log = dialog.findViewById(R.id.results_log) as TextView
            results_log.text = "This is the test results"
            dialog.show()
        }
    }
}