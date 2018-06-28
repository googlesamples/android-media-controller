/*
 * Copyright 2017 Google Inc. All rights reserved.
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
package com.example.android.mediacontroller;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.media.session.MediaSessionManager;
import android.media.session.MediaSessionManager.OnActiveSessionsChangedListener;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.widget.Toast;

import com.example.android.mediacontroller.tasks.FindMediaAppsTask;
import com.example.android.mediacontroller.tasks.FindMediaBrowserAppsTask;
import com.example.android.mediacontroller.tasks.FindMediaSessionAppsTask;
import com.example.android.mediacontroller.tasks.MediaAppControllerUtils;

import java.util.List;

/**
 * App entry point. Presents a list of apps that implement a MediaBrowser interface
 * (via a receiver that responds to the action "android.media.browse.MediaBrowserService").
 */
public class LaunchActivity extends AppCompatActivity {
    private static final String TAG = LaunchActivity.class.getSimpleName();
    private static final String PACKAGE_NAME_EXTRA =
            "com.example.android.mediacontroller.PACKAGE_NAME";

    private Snackbar mSnackbar;

    private MediaAppListAdapter.Section mMediaBrowserApps;

    private final FindMediaAppsTask.AppListUpdatedCallback mBrowserAppsUpdated =
            new FindMediaAppsTask.AppListUpdatedCallback() {
                @Override
                public void onAppListUpdated(
                        @NonNull List<? extends MediaAppDetails> mediaAppDetails) {

                    if (mediaAppDetails.isEmpty()) {
                        // Show an error if no apps were found.
                        mMediaBrowserApps.setError(
                                R.string.no_apps_found,
                                R.string.no_apps_reason_no_media_browser_service);
                        return;
                    }
                    mMediaBrowserApps.setAppsList(mediaAppDetails);
                }
            };

    // MediaSessionManager is only supported on API 21+, so all related logic is bundled in a
    // separate inner class that's only instantiated if the device is running L or later.
    private final MediaSessionListener mMediaSessionListener =
            Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP
                    ? new MediaSessionListener()
                    : null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_launch);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        MediaAppListAdapter mediaAppsAdapter = new MediaAppListAdapter((app, isTest) -> {
            if (mSnackbar != null) {
                mSnackbar.dismiss();
                mSnackbar = null;
            }

            final Intent intent = isTest ?
                    MediaAppTestingActivity.Companion.buildIntent(LaunchActivity.this, app)
                    : MediaAppControllerActivity.buildIntent(LaunchActivity.this, app);
            startActivity(intent);
        });

        if (mMediaSessionListener != null) {
            mMediaSessionListener.onCreate(mediaAppsAdapter);
        }
        mMediaBrowserApps = mediaAppsAdapter.addSection(R.string.media_app_header_browse);

        RecyclerView mediaAppsList = findViewById(R.id.app_list);
        mediaAppsList.setLayoutManager(new LinearLayoutManager(this));
        mediaAppsList.setHasFixedSize(true);
        mediaAppsList.setAdapter(mediaAppsAdapter);

        if (getIntent() != null && getIntent().getExtras() != null) {
            handleIntentExtras(getIntent().getExtras());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (intent.getExtras() != null) {
            handleIntentExtras(intent.getExtras());
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Update the list of media session in onStart so that if the user returns from the
        // permissions screen after granting notification listener permission we re-scan correctly.
        if (mMediaSessionListener != null) {
            mMediaSessionListener.onStart(this);
        }
        // Update the list of media browser apps in onStart so if a new app is installed it will
        // appear on the list when the user comes back to it.
        new FindMediaBrowserAppsTask(this, mBrowserAppsUpdated).execute();
    }

    @Override
    protected void onStop() {
        if (mMediaSessionListener != null) {
            mMediaSessionListener.onStop();
        }
        super.onStop();
    }

    private void handleIntentExtras(@NonNull Bundle extras) {
        if (extras.containsKey(PACKAGE_NAME_EXTRA)) {
            String packageName = extras.getString(PACKAGE_NAME_EXTRA);
            if (packageName != null && !packageName.isEmpty()) {
                openAppWithPackage(packageName, extras);
            }
        }
    }

    private void openAppWithPackage(@NonNull String packageName) {
        openAppWithPackage(packageName, Bundle.EMPTY);
    }

    private void openAppWithPackage(@NonNull String packageName, @NonNull Bundle extras) {
        PackageManager pm = getPackageManager();
        ServiceInfo serviceInfo = MediaAppDetails.findServiceInfo(packageName, pm);
        if (serviceInfo != null) {
            Resources res = getResources();
            MediaAppDetails app = new MediaAppDetails(serviceInfo, pm, res);

            Intent intent =
                    MediaAppControllerActivity.buildIntent(LaunchActivity.this, app);
            intent.putExtras(extras);
            startActivity(intent);
        } else {
            Toast.makeText(this,
                    getString(R.string.no_app_for_package, packageName),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Encapsulates the API 21+ functionality of looking for and observing updates to active media
     * sessions. We only construct an instance of this class if the device is running L or later,
     * to avoid any ClassNotFoundExceptions due to the use of MediaSession and related classes.
     */
    @TargetApi(VERSION_CODES.LOLLIPOP)
    private final class MediaSessionListener {
        private MediaAppListAdapter.Section mMediaSessionApps;

        private final FindMediaAppsTask.AppListUpdatedCallback mSessionAppsUpdated =
                new FindMediaAppsTask.AppListUpdatedCallback() {
                    @Override
                    public void onAppListUpdated(
                            @NonNull List<? extends MediaAppDetails> mediaAppDetails) {

                        if (mediaAppDetails.isEmpty()) {
                            // Show an error if no apps were found.
                            mMediaSessionApps.setError(
                                    R.string.no_apps_found,
                                    R.string.no_apps_reason_no_active_sessions);
                            return;
                        }
                        mMediaSessionApps.setAppsList(mediaAppDetails);
                    }
                };

        private final OnActiveSessionsChangedListener mSessionsChangedListener =
                list -> mSessionAppsUpdated.onAppListUpdated(
                        MediaAppControllerUtils.getMediaAppsFromControllers(
                                list, getPackageManager(), getResources()));
        private MediaSessionManager mMediaSessionManager;

        void onCreate(MediaAppListAdapter mediaAppListAdapter) {
            mMediaSessionApps = mediaAppListAdapter.addSection(R.string.media_app_header_session);
            mMediaSessionManager = (MediaSessionManager)
                    getSystemService(Context.MEDIA_SESSION_SERVICE);
        }

        void onStart(Context context) {
            if (!NotificationListener.isEnabled(context)) {
                mMediaSessionApps.setError(
                        R.string.no_apps_found,
                        R.string.no_apps_reason_missing_permission,
                        R.string.action_notification_permissions_settings,
                        (v) -> startActivity(new Intent(
                                "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
                return;
            }
            if (mMediaSessionManager == null) {
                return;
            }
            ComponentName listenerComponent =
                    new ComponentName(context, NotificationListener.class);
            mMediaSessionManager.addOnActiveSessionsChangedListener(
                    mSessionsChangedListener, listenerComponent);
            new FindMediaSessionAppsTask(mMediaSessionManager, listenerComponent,
                    getPackageManager(), getResources(), mSessionAppsUpdated).execute();
        }

        void onStop() {
            if (mMediaSessionManager == null) {
                return;
            }
            mMediaSessionManager.removeOnActiveSessionsChangedListener(mSessionsChangedListener);
        }
    }
}
