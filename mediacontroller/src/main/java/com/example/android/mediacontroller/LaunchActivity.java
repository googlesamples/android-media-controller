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

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

/**
 * App entry point. Presents a list of apps that implement a MediaBrowser interface
 * (via a receiver that responds to the action "android.media.browse.MediaBrowserService").
 */
public class LaunchActivity extends AppCompatActivity {

    /**
     * Callback used by {@link FindMediaAppsTask}.
     */
    private interface AppListUpdatedCallback {
        void onAppListUpdated(List<MediaAppDetails> mediaAppDetailses);
    }

    /**
     * Click listener used by {@link MediaListAdapter}.
     */
    private interface MediaAppSelectedListener {
        void onMediaAppClicked(@NonNull MediaAppDetails mediaAppDetails);
    }

    private RecyclerView mMediaAppsList;
    private MediaListAdapter mMediaAppsAdapter;

    private View mNoAppsFoundView;

    private AppListUpdatedCallback mAppListUpdatedCallback = new AppListUpdatedCallback() {
        @Override
        public void onAppListUpdated(List<MediaAppDetails> mediaAppDetails) {
            if (mediaAppDetails.isEmpty()) {
                // Show an error if no apps were found.
                mMediaAppsList.setVisibility(View.GONE);
                mNoAppsFoundView.setVisibility(View.VISIBLE);
                return;
            }

            mMediaAppsAdapter.setAppsList(mediaAppDetails);

            mMediaAppsList.setVisibility(View.VISIBLE);
            mNoAppsFoundView.setVisibility(View.GONE);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mMediaAppsAdapter = new MediaListAdapter(new MediaAppSelectedListener() {
            @Override
            public void onMediaAppClicked(@NonNull MediaAppDetails appDetails) {
                final Intent intent = MediaAppControllerActivity.buildIntent(
                        LaunchActivity.this,
                        appDetails);
                startActivity(intent);
            }
        });

        mMediaAppsList = (RecyclerView) findViewById(R.id.app_list);
        mMediaAppsList.setLayoutManager(new LinearLayoutManager(this));
        mMediaAppsList.setHasFixedSize(true);
        mMediaAppsList.setAdapter(mMediaAppsAdapter);

        mNoAppsFoundView = findViewById(R.id.no_apps_found);
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Update the list of apps in onStart so if a new app is installed it will appear
        // on the list when the user comes back to it.
        new FindMediaAppsTask(mAppListUpdatedCallback).execute();
    }

    private class FindMediaAppsTask extends AsyncTask<Void, Void, List<MediaAppDetails>> {
        private static final String MEDIA_BROWSER_ACTION =
                "android.media.browse.MediaBrowserService";

        private final AppListUpdatedCallback mCallback;

        private FindMediaAppsTask(@NonNull AppListUpdatedCallback callback) {
            mCallback = callback;
        }

        /**
         * Finds installed packages that have registered a
         * {@link android.service.media.MediaBrowserService} or
         * {@link android.support.v4.media.MediaBrowserServiceCompat} service by
         * looking for packages that have services that respond to the
         * "android.media.browse.MediaBrowserService" action.
         */
        @Override
        protected List<MediaAppDetails> doInBackground(Void... params) {
            final PackageManager packageManager = getPackageManager();
            final List<MediaAppDetails> mediaApps = new LinkedList<>();

            // Build an Intent that only has the MediaBrowserService action and query
            // the PackageManager for apps that have services registered that can
            // receive it.
            final Intent mediaBrowserIntent = new Intent(MEDIA_BROWSER_ACTION);
            final List<ResolveInfo> services =
                    packageManager.queryIntentServices(mediaBrowserIntent,
                            PackageManager.GET_RESOLVED_FILTER);

            if (services != null && !services.isEmpty()) {
                for (final ResolveInfo info : services) {
                    final Drawable icon = info.loadIcon(packageManager);
                    final String name = info.loadLabel(packageManager).toString();
                    final String packageName = info.serviceInfo.packageName;
                    final String serviceName = info.serviceInfo.name;
                    final ComponentName serviceComponentName =
                            new ComponentName(packageName, serviceName);
                    mediaApps.add(new MediaAppDetails(
                            name,
                            serviceComponentName,
                            BitmapUtils.convertDrawable(getResources(), icon)));
                }
            }

            // Sort the list by localized app name for convenience.
            Collections.sort(mediaApps, new Comparator<MediaAppDetails>() {
                @Override
                public int compare(MediaAppDetails left, MediaAppDetails right) {
                    return left.appName.compareToIgnoreCase(right.appName);
                }
            });

            return mediaApps;
        }

        @Override
        protected void onPostExecute(List<MediaAppDetails> mediaAppDetailses) {
            mCallback.onAppListUpdated(mediaAppDetailses);
        }
    }

    private final class ViewHolder extends RecyclerView.ViewHolder {
        private final ViewGroup rootView;
        private final ImageView appIconView;
        private final TextView appNameView;
        private final TextView appPackageView;

        private ViewHolder(View itemView) {
            super(itemView);

            rootView = (ViewGroup) itemView;
            appIconView = (ImageView) itemView.findViewById(R.id.app_icon);
            appNameView = (TextView) itemView.findViewById(R.id.app_name);
            appPackageView = (TextView) itemView.findViewById(R.id.package_name);
        }
    }

    private class MediaListAdapter extends RecyclerView.Adapter<ViewHolder> {

        private final List<MediaAppDetails> mMediaAppDetailsList = new ArrayList<>();
        private final MediaAppSelectedListener mMediaAppSelectedListener;

        private MediaListAdapter(@NonNull MediaAppSelectedListener itemClickListener) {
            mMediaAppSelectedListener = itemClickListener;
        }

        private void setAppsList(final List<MediaAppDetails> newList) {
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return mMediaAppDetailsList.size();
                }

                @Override
                public int getNewListSize() {
                    return newList.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    final String oldPackage = mMediaAppDetailsList.get(oldItemPosition)
                            .mediaServiceComponentName.getPackageName();
                    final String newPackage = newList.get(oldItemPosition)
                            .mediaServiceComponentName.getPackageName();

                    return oldPackage.equals(newPackage);
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    return mMediaAppDetailsList.get(oldItemPosition)
                            .equals(newList.get(newItemPosition));
                }
            });

            mMediaAppDetailsList.clear();
            mMediaAppDetailsList.addAll(newList);

            diffResult.dispatchUpdatesTo(this);
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            final ViewGroup itemLayout = (ViewGroup) getLayoutInflater()
                    .inflate(R.layout.media_app_item, parent, false);
            return new ViewHolder(itemLayout);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            final MediaAppDetails appDetails = mMediaAppDetailsList.get(position);

            holder.appIconView.setImageBitmap(appDetails.icon);
            holder.appIconView.setContentDescription(
                    getString(R.string.app_icon_desc, appDetails.appName));
            holder.appNameView.setText(appDetails.appName);
            holder.appPackageView.setText(appDetails.mediaServiceComponentName.getPackageName());

            holder.rootView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mMediaAppSelectedListener.onMediaAppClicked(appDetails);
                }
            });
        }

        @Override
        public int getItemCount() {
            return mMediaAppDetailsList.size();
        }
    }
}
