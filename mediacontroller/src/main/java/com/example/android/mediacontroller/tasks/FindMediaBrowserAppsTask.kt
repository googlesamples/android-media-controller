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
package com.example.android.mediacontroller.tasks

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.support.v4.media.MediaBrowserServiceCompat
import com.example.android.mediacontroller.MediaAppDetails
import java.util.ArrayList

/**
 * Implementation of [FindMediaAppsTask] that uses available implementations of
 * MediaBrowser to populate the list of apps.
 */
class FindMediaBrowserAppsTask constructor(
        context: Context, callback: AppListUpdatedCallback
) : FindMediaAppsTask(callback, sortAlphabetical = true) {

    private val packageManager: PackageManager = context.packageManager
    private val resources: Resources = context.resources

    /**
     * Finds installed packages that have registered a
     * [android.service.media.MediaBrowserService] or
     * [android.support.v4.media.MediaBrowserServiceCompat] service by
     * looking for packages that have services that respond to the
     * "android.media.browse.MediaBrowserService" action.
     */
    override val mediaApps: List<MediaAppDetails>
        get() {
            val mediaApps = ArrayList<MediaAppDetails>()
            val mediaBrowserIntent = Intent(MediaBrowserServiceCompat.SERVICE_INTERFACE)

            // Build an Intent that only has the MediaBrowserService action and query
            // the PackageManager for apps that have services registered that can
            // receive it.
            val services = packageManager.queryIntentServices(
                    mediaBrowserIntent,
                    PackageManager.GET_RESOLVED_FILTER
            )

            if (services != null && !services.isEmpty()) {
                for (info in services) {
                    mediaApps.add(
                            MediaAppDetails(info.serviceInfo, packageManager, resources)
                    )
                }
            }
            return mediaApps
        }
}