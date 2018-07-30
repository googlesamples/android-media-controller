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
package com.example.android.mediacontroller

import android.support.v4.media.session.MediaControllerCompat

/**
 * Holds test definitions used by both the mobile and TV apps
 */
fun runPlayTest(
        controller: MediaControllerCompat,
        logger: (tag: String, message: String) -> Unit?
) = Test(Test.androidResources.getString(R.string.play_test_logs_title), controller, logger)
        .apply {
            addStep(ConfigurePlay(this))
            addStep(WaitForPlaying(this))
        }.runTest()