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
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.media.session.MediaSession;
import android.os.Parcel;
import android.os.Parcelable;
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
    public final MediaSessionCompat.Token sessionToken;
    public final ComponentName componentName;

    public MediaAppDetails(String packageName, String name, Bitmap appIcon,
                           MediaSessionCompat.Token token) {
        this.packageName = packageName;
        appName = name;
        sessionToken = token;
        icon = appIcon;
        componentName = null;
    }

    public MediaAppDetails(String packageName, String name, Bitmap appIcon,
                           MediaSession.Token token) {
        this(packageName, name, appIcon, MediaSessionCompat.Token.fromToken(token));
    }

    public MediaAppDetails(ServiceInfo serviceInfo, PackageManager pm, Resources resources) {
        packageName = serviceInfo.packageName;
        appName = serviceInfo.loadLabel(pm).toString();
        Drawable appIcon = serviceInfo.loadIcon(pm);
        icon = BitmapUtils.convertDrawable(resources, appIcon);
        componentName = new ComponentName(serviceInfo.packageName, serviceInfo.name);
        sessionToken = null;
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
                return(info.serviceInfo);
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
