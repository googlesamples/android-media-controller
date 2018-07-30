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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.RemoteException
import android.support.v17.leanback.app.GuidedStepSupportFragment
import android.support.v4.app.FragmentActivity
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import android.widget.Toast
import com.example.android.mediacontroller.MediaAppDetails
import com.example.android.mediacontroller.R

/**
 * Sets up Media Controller/Browser connections and launches TvTestingFragment
 */
class TvTestingActivity : FragmentActivity() {
    private var mediaAppDetails: MediaAppDetails? = null
    private var mediaController: MediaControllerCompat? = null
    private var mediaBrowser: MediaBrowserCompat? = null

    fun getBrowser(): MediaBrowserCompat? {
        return mediaBrowser
    }

    fun getController(): MediaControllerCompat? {
        return mediaController
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_testing)

        handleIntent(intent)

        mediaAppDetails?.let {
            setupMedia()
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
            showError("Couldn't update MediaAppDetails object")
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

    private fun showError(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    }

    private fun showError(id: Int, vararg args: Any) {
        Toast.makeText(this, getString(id, *args), Toast.LENGTH_LONG).show()
    }

    private fun setupMedia() {
        // setupMedia() is only called when mediaAppDetails is not null, so this should always
        // skip the if function block
        val appDetails = mediaAppDetails
        if (appDetails == null) {
            Log.e(TAG, getString(R.string.setup_media_error_msg))
            showError(R.string.connection_failed_msg, "!Unknown App Name!")
            return
        }

        when {
            appDetails.componentName != null -> {
                mediaBrowser = MediaBrowserCompat(this, appDetails.componentName,
                        object : MediaBrowserCompat.ConnectionCallback() {
                            override fun onConnected() {
                                setupMediaController(true)
                            }

                            override fun onConnectionSuspended() {
                                showError(R.string.connection_lost_msg, appDetails.appName)
                            }

                            override fun onConnectionFailed() {
                                showError(
                                        R.string.connection_failed_hint_reject,
                                        appDetails.appName,
                                        appDetails.componentName.flattenToShortString()
                                )
                            }
                        }, null).apply { connect() }
            }
            appDetails.sessionToken != null -> setupMediaController(false)
            else -> showError(R.string.connection_failed_hint_setup, appDetails.appName)
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
                    showError(
                            R.string.setup_media_controller_error_hint,
                            "MediaBrowser"
                    )
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
                    showError(
                            R.string.setup_media_controller_error_hint,
                            "MediaAppDetails"
                    )
                    return
                }
            }

            mediaController = MediaControllerCompat(this, token)
            setupFragments()

            Log.d(TAG, "MediaControllerCompat created")
        } catch (remoteException: RemoteException) {
            Log.e(TAG, getString(R.string.media_controller_failed_msg), remoteException)
            showError(R.string.media_controller_failed_msg)
        }
    }

    private fun setupFragments() {
        val bundle = Bundle()
        bundle.putParcelable("App Details", mediaAppDetails)
        val testingFragment = TvTestingGuidedStepFragment()
        testingFragment.arguments = bundle

        GuidedStepSupportFragment.addAsRoot(this, testingFragment, android.R.id.content)
    }

    companion object {
        private const val TAG = "TvTestingActivity"

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
            return Intent(activity, TvTestingActivity::class.java).apply {
                putExtra(APP_DETAILS_EXTRA, appDetails)
            }
        }
    }
}