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
import android.content.ContentValues
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.media.session.MediaController
import android.os.Build
import android.util.Log
import com.example.android.mediacontroller.MediaAppDetails
import java.util.ArrayList

object MediaAppControllerUtils {
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @JvmStatic
    fun getMediaAppsFromControllers(
            controllers: Collection<MediaController>,
            packageManager: PackageManager,
            resources: Resources
    ): List<MediaAppDetails> {
        val mediaApps = ArrayList<MediaAppDetails>()
        for (controller in controllers) {
            val packageName = controller.packageName
            val info: ApplicationInfo
            try {
                info = packageManager.getApplicationInfo(packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                // This should not happen. If we get a media session for a package, then the
                // package must be installed on the device.
                Log.e(ContentValues.TAG, "Unable to load package details", e)
                continue
            }

            mediaApps.add(
                    MediaAppDetails(info, packageManager, resources, controller.sessionToken)
            )
        }
        return mediaApps
    }
}