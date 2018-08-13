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
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.session.MediaSession;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.session.MediaSessionCompat;

import java.util.List;

/**
 * Stores details about a media app.
 */
public class MediaAppDetails implements Parcelable {
    public final String packageName;
    public final String appName;
    public final Bitmap icon;
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @Nullable
    public Bitmap banner;
    public final MediaSessionCompat.Token sessionToken;
    public final ComponentName componentName;

    public MediaAppDetails(String packageName, String name, Bitmap appIcon,
                           @Nullable Bitmap appBanner, MediaSessionCompat.Token token) {
        this.packageName = packageName;
        appName = name;
        sessionToken = token;
        icon = appIcon;
        // This TV app targets min sdk version 21, and a banner will only be present for the TV app
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            banner = appBanner;
        }
        componentName = null;
    }

    public MediaAppDetails(String packageName, String name, Bitmap appIcon,
                           @Nullable Bitmap appBanner, MediaSession.Token token) {
        this(packageName, name, appIcon, appBanner, MediaSessionCompat.Token.fromToken(token));
    }

    public MediaAppDetails(PackageItemInfo info, PackageManager pm, Resources resources,
                           MediaSession.Token token) {
        packageName = info.packageName;
        appName = info.loadLabel(pm).toString();
        Drawable appIcon = info.loadIcon(pm);
        icon = BitmapUtils.convertDrawable(resources, appIcon, true);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            Drawable appBanner = info.loadBanner(pm);
            if (appBanner != null) {
                banner = BitmapUtils.convertDrawable(resources, appBanner, false);
            }
        }

        if (token != null) {
            // If we have a MediaSession Token, then we don't need to connect to the
            // MediaBrowserService implementation, so componentName is null.
            componentName = null;
            sessionToken = MediaSessionCompat.Token.fromToken(token);
        } else {
            // If we don't have a MediaSession Token, then we need to connect to the
            // MediaBrowserService implementation.
            componentName = new ComponentName(info.packageName, info.name);
            sessionToken = null;
        }
    }

    public MediaAppDetails(PackageItemInfo info, PackageManager pm, Resources resources) {
        this(info, pm, resources, null);
    }

    /**
     * Helper function to get the service info for the packagemanager for a given package.
     */
    public static ServiceInfo findServiceInfo(String packageName, PackageManager pm) {
        final Intent mediaBrowserIntent = new Intent(MediaBrowserServiceCompat.SERVICE_INTERFACE);
        final List<ResolveInfo> services =
                pm.queryIntentServices(mediaBrowserIntent,
                        PackageManager.GET_RESOLVED_FILTER);
        for (ResolveInfo info : services) {
            if (info.serviceInfo.packageName.equals(packageName)) {
                return (info.serviceInfo);
            }
        }
        return null;
    }

    private MediaAppDetails(final Parcel parcel) {
        packageName = parcel.readString();
        appName = parcel.readString();
        icon = parcel.readParcelable(MediaAppDetails.class.getClassLoader());
        sessionToken = parcel.readParcelable(MediaAppDetails.class.getClassLoader());
        componentName = parcel.readParcelable(MediaAppDetails.class.getClassLoader());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(packageName);
        dest.writeString(appName);
        dest.writeParcelable(icon, flags);
        dest.writeParcelable(sessionToken, flags);
        dest.writeParcelable(componentName, flags);
    }

    public static final Parcelable.Creator<MediaAppDetails> CREATOR =
            new Parcelable.Creator<MediaAppDetails>() {

                public MediaAppDetails createFromParcel(Parcel source) {
                    return new MediaAppDetails(source);
                }

                public MediaAppDetails[] newArray(int size) {
                    return new MediaAppDetails[size];
                }
            };
}
