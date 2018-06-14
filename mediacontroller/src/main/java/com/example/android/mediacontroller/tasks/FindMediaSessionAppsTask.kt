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

import android.annotation.TargetApi
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.res.Resources
import android.media.session.MediaSessionManager
import android.os.Build
import com.example.android.mediacontroller.MediaAppDetails

/**
 * Implementation of [FindMediaAppsTask] that uses active media sessions to populate the
 * list of media apps.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class FindMediaSessionAppsTask constructor(
        private val mediaSessionManager: MediaSessionManager,
        private val listenerComponent: ComponentName,
        private val packageManager: PackageManager,
        private val resources: Resources,
        callback: AppListUpdatedCallback
) : FindMediaAppsTask(callback, sortAlphabetical = false) {

    override val mediaApps: List<MediaAppDetails>
        get() = MediaAppControllerUtils.getMediaAppsFromControllers(
                mediaSessionManager.getActiveSessions(listenerComponent),
                packageManager,
                resources
        )
}