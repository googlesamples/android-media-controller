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
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Drawable;
import android.media.session.MediaSession;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaSessionCompat;

/**
 * Represents a media app, used to populate entries in LaunchActivity.
 */
public abstract class MediaAppEntry {

    public final String appName;
    public final String packageName;
    public final Drawable icon;

    private MediaAppEntry(String name, String pkg, Drawable appIcon) {
        appName = name;
        packageName = pkg;
        icon = appIcon;
    }

    /**
     * Callback for session token, since connecting to a browse service and obtaining a session
     * token is an asynchronous operation.
     */
    public interface SessionTokenAvailableCallback {

        void onSuccess(MediaSessionCompat.Token sessionToken);

        void onFailure();
    }

    public abstract void getSessionToken(Context context, SessionTokenAvailableCallback callback);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MediaAppEntry that = (MediaAppEntry) o;
        return appName.equals(that.appName) && packageName.equals(that.packageName);
    }

    @Override
    public int hashCode() {
        int result = appName.hashCode();
        result = 31 * result + packageName.hashCode();
        return result;
    }

    public static MediaAppEntry fromSessionToken(MediaSession.Token sessionToken,
                                                 String appName,
                                                 String packageName,
                                                 Drawable appIcon) {
        return new MediaAppEntry(appName, packageName, appIcon) {
            @Override
            public void getSessionToken(Context context, SessionTokenAvailableCallback callback) {
                callback.onSuccess(MediaSessionCompat.Token.fromToken(sessionToken));
            }
        };
    }

    public static MediaAppEntry fromBrowseService(ServiceInfo serviceInfo, PackageManager pm) {
        String appName = serviceInfo.loadLabel(pm).toString();
        Drawable appIcon = serviceInfo.loadIcon(pm);
        ComponentName browseService = new ComponentName(serviceInfo.packageName, serviceInfo.name);

        return new MediaAppEntry(appName, serviceInfo.packageName, appIcon) {
            private MediaBrowserCompat browser;

            @Override
            public void getSessionToken(Context context, SessionTokenAvailableCallback callback) {
                browser = new MediaBrowserCompat(context, browseService,
                        new MediaBrowserCompat.ConnectionCallback() {
                            @Override
                            public void onConnected() {
                                callback.onSuccess(browser.getSessionToken());
                                // Once we've obtained the session token, we no longer need to keep
                                // an open connection to the browse service.
                                browser.disconnect();
                                browser = null;
                            }

                            @Override
                            public void onConnectionFailed() {
                                callback.onFailure();
                            }
                        }, null);
                browser.connect();
            }
        };
    }
}
