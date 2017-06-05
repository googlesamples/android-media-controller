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
import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Stores details about a media app.
 */
public class MediaAppDetails implements Parcelable {
    public final String appName;
    public final ComponentName mediaServiceComponentName;
    public final Bitmap icon;

    public MediaAppDetails(String name,
                           ComponentName mediaServiceName,
                           Bitmap appIcon) {
        appName = name;
        mediaServiceComponentName = mediaServiceName;
        icon = appIcon;
    }

    private MediaAppDetails(final Parcel parcel) {
        appName = parcel.readString();
        mediaServiceComponentName = parcel.readParcelable(MediaAppDetails.class.getClassLoader());
        icon = parcel.readParcelable(MediaAppDetails.class.getClassLoader());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(appName);
        dest.writeParcelable(mediaServiceComponentName, flags);
        dest.writeParcelable(icon, flags);
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
