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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.MediaSessionManager.OnActiveSessionsChangedListener;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.widget.ContentLoadingProgressBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * App entry point. Presents a list of apps that implement a MediaBrowser interface
 * (via a receiver that responds to the action "android.media.browse.MediaBrowserService").
 */
public class LaunchActivity extends AppCompatActivity {
    private static final String TAG = LaunchActivity.class.getSimpleName();

    private View mRootView;
    private Snackbar mSnackbar;

    private MediaAppListAdapter.Section mMediaBrowserApps;
    private ContentLoadingProgressBar mSpinner;

    /**
     * Callback used by {@link FindMediaAppsTask}.
     */
    public interface AppListUpdatedCallback {

        void onAppListUpdated(List<MediaAppDetails> mediaAppEntries);
    }

    private final AppListUpdatedCallback mBrowserAppsUpdated = new AppListUpdatedCallback() {
        @Override
        public void onAppListUpdated(List<MediaAppDetails> mediaAppDetails) {
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

        mRootView = findViewById(R.id.root_view);
        mSpinner = findViewById(R.id.spinner);

        MediaAppListAdapter mediaAppsAdapter = new MediaAppListAdapter(app -> {
            if (mSnackbar != null) {
                mSnackbar.dismiss();
                mSnackbar = null;
            }

            final Intent intent = MediaAppControllerActivity.buildIntent(
                LaunchActivity.this, app);
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

    /**
     * Base class for an async task that fetches a list of media apps.
     */
    private static abstract class FindMediaAppsTask
            extends AsyncTask<Void, Void, List<MediaAppDetails>> {

        private final AppListUpdatedCallback mCallback;

        private FindMediaAppsTask(AppListUpdatedCallback callback) {
            mCallback = callback;
        }

        protected abstract List<MediaAppDetails> getMediaApps();

        @Override
        protected List<MediaAppDetails> doInBackground(Void... params) {
            final List<MediaAppDetails> mediaApps = new ArrayList<>(getMediaApps());
            // Sort the list by localized app name for convenience.
            Collections.sort(mediaApps,
                    (left, right) -> left.appName.compareToIgnoreCase(right.appName));
            return mediaApps;
        }

        @Override
        protected void onPostExecute(List<MediaAppDetails> mediaAppEntries) {
            mCallback.onAppListUpdated(mediaAppEntries);
        }
    }

    /**
     * Implementation of {@link FindMediaAppsTask} that uses available implementations of
     * MediaBrowser to populate the list of apps.
     */
    private static class FindMediaBrowserAppsTask extends FindMediaAppsTask {

        private final PackageManager mPackageManager;
        private final Resources mResources;

        private FindMediaBrowserAppsTask(Context context, AppListUpdatedCallback callback) {
            super(callback);
            mPackageManager = context.getPackageManager();
            mResources = context.getResources();
        }

        /**
         * Finds installed packages that have registered a
         * {@link android.service.media.MediaBrowserService} or
         * {@link android.support.v4.media.MediaBrowserServiceCompat} service by
         * looking for packages that have services that respond to the
         * "android.media.browse.MediaBrowserService" action.
         */
        @Override
        protected List<MediaAppDetails> getMediaApps() {
            final List<MediaAppDetails> mediaApps = new ArrayList<>();

            // Build an Intent that only has the MediaBrowserService action and query
            // the PackageManager for apps that have services registered that can
            // receive it.
            final Intent mediaBrowserIntent =
                    new Intent(MediaBrowserServiceCompat.SERVICE_INTERFACE);
            final List<ResolveInfo> services =
                    mPackageManager.queryIntentServices(mediaBrowserIntent,
                            PackageManager.GET_RESOLVED_FILTER);

            if (services != null && !services.isEmpty()) {
                for (final ResolveInfo info : services) {
                    mediaApps.add(
                            new MediaAppDetails(info.serviceInfo, mPackageManager, mResources));
                }
            }
            return mediaApps;
        }
    }

    /**
     * Implementation of {@link FindMediaAppsTask} that uses active media sessions to populate the
     * list of media apps.
     */
    @TargetApi(VERSION_CODES.LOLLIPOP)
    private static class FindMediaSessionAppsTask extends FindMediaAppsTask {

        private final MediaSessionManager mMediaSessionManager;
        private final ComponentName mListenerComponent;
        private final PackageManager mPackageManager;
        private final Resources mResources;

        private FindMediaSessionAppsTask(MediaSessionManager mediaSessionManager,
                                         ComponentName listenerComponent,
                                         PackageManager packageManager,
                                         Resources resources,
                                         AppListUpdatedCallback callback) {
            super(callback);
            mMediaSessionManager = mediaSessionManager;
            mListenerComponent = listenerComponent;
            mPackageManager = packageManager;
            mResources = resources;
        }

        @Override
        protected List<MediaAppDetails> getMediaApps() {
            return getMediaAppsFromControllers(
                    mMediaSessionManager.getActiveSessions(mListenerComponent),
                    mPackageManager, mResources);
        }

        static List<MediaAppDetails> getMediaAppsFromControllers(
                Collection<MediaController> controllers,
                PackageManager packageManager,
                Resources resources) {
            final List<MediaAppDetails> mediaApps = new ArrayList<>();
            for (final MediaController controller : controllers) {
                String packageName = controller.getPackageName();
                ApplicationInfo info;
                try {
                    info = packageManager.getApplicationInfo(packageName, 0);
                } catch (NameNotFoundException e) {
                    // This should not happen. If we get a media session for a package, then the
                    // package must be installed on the device.
                    Log.e(TAG, "Unable to load package details", e);
                    continue;
                }
                final Drawable icon = info.loadIcon(packageManager);
                final String name = info.loadLabel(packageManager).toString();
                mediaApps.add(new MediaAppDetails(packageName, name,
                        BitmapUtils.convertDrawable(resources, icon),
                        controller.getSessionToken()));
            }
            return mediaApps;
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

        private final AppListUpdatedCallback mSessionAppsUpdated = new AppListUpdatedCallback() {
            @Override
            public void onAppListUpdated(List<MediaAppDetails> mediaAppDetails) {
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
                        FindMediaSessionAppsTask.getMediaAppsFromControllers(
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
