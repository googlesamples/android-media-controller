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
package com.example.android.mediacontroller.tv

import android.os.Bundle
import android.support.v17.leanback.app.GuidedStepSupportFragment
import android.support.v17.leanback.widget.GuidanceStylist
import android.support.v17.leanback.widget.GuidedAction
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.android.mediacontroller.MediaAppDetails
import com.example.android.mediacontroller.R
import com.example.android.mediacontroller.Test
import com.example.android.mediacontroller.formatTvDetailsString
import com.example.android.mediacontroller.runPlayTest

/**
 * Lists test options and displays selected MediaController details
 */
class TvTestingGuidedStepFragment : GuidedStepSupportFragment() {
    private var mediaAppDetails: MediaAppDetails? = null
    private var mediaController: MediaControllerCompat? = null
    private var mediaBrowser: MediaBrowserCompat? = null

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        mediaAppDetails = arguments?.getParcelable("App Details")
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        Test.androidResources = resources

        activity?.let {
            if (it is TvTestingActivity) {
                mediaBrowser = it.getBrowser()
                mediaController = it.getController()
            }
        }
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        return GuidanceStylist.Guidance(
                mediaAppDetails?.appName,
                getString(R.string.tv_intro_description),
                getString(R.string.tv_intro_breadcrumb),
                null
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        super.onCreateActions(actions, savedInstanceState)
        actions.add(GuidedAction.Builder(context)
                .title(getString(R.string.play_test_title))
                .description(getString(R.string.play_test_desc))
                .id(PLAY_TEST)
                .build()
        )
    }

    override fun onGuidedActionClicked(action: GuidedAction?) {
        super.onGuidedActionClicked(action)
        mediaController?.let {
            when (action?.id) {
                PLAY_TEST -> runPlayTest(it, ::logTestUpdate)
                else -> Toast.makeText(
                        activity,
                        getString(R.string.tv_invalid_test),
                        Toast.LENGTH_LONG
                ).show()
            }
        }

    }

    private fun logTestUpdate(logTag: String, message: String) {
        activity?.runOnUiThread {
            guidanceStylist.descriptionView.text = mediaController?.formatTvDetailsString()
            guidanceStylist.breadcrumbView.text = message
        }
    }

    companion object {
        const val PLAY_TEST = 0L
    }
}