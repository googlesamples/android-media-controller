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

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;

/**
 * Utility class for {@link Bitmap}s.
 */
public final class BitmapUtils {

    private BitmapUtils() {
        // Utility class.
    }

    /**
     * Converts a {@link Drawable} to an appropriately sized {@link Bitmap}.
     *
     * @param resources Resources for the current {@link android.content.Context}.
     * @param drawable  The {@link Drawable} to convert to a Bitmap.
     * @param downScale Will downscale the Bitmap to {@code R.dimen.app_icon_size} dp.
     * @return A Bitmap, no larger than {@code R.dimen.app_icon_size} dp if desired.
     */
    public static Bitmap convertDrawable(@NonNull final Resources resources,
                                         @NonNull final Drawable drawable,
                                         final boolean downScale) {

        final Bitmap bitmap;
        if (drawable instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable) drawable).getBitmap();
        } else {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                    drawable.getIntrinsicHeight(),
                    Bitmap.Config.ARGB_8888);
            final Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
        }

        if (!downScale) {
            return bitmap;
        }

        final int iconSize = resources.getDimensionPixelSize(R.dimen.app_icon_size);
        if (bitmap.getHeight() > iconSize || bitmap.getWidth() > iconSize) {
            // Which needs to be scaled to fit.
            final int height = bitmap.getHeight();
            final int width = bitmap.getWidth();

            final int scaleHeight;
            final int scaleWidth;

            // Calculate the new size based on which dimension is larger.
            if (height > width) {
                scaleHeight = iconSize;
                scaleWidth = (int) (width * ((float) iconSize) / height);
            } else {
                scaleWidth = iconSize;
                scaleHeight = (int) (height * ((float) iconSize) / width);
            }

            return Bitmap.createScaledBitmap(bitmap, scaleWidth, scaleHeight, false);
        } else {
            return bitmap;
        }
    }

    /**
     * Creates a Material Design compliant {@link android.support.v7.widget.Toolbar} icon
     * from a given full sized icon.
     *
     * @param resources Resources for the current {@link android.content.Context}.
     * @param icon      The bitmap to convert.
     * @return A scaled Bitmap of the appropriate size and in-built padding.
     */
    public static Bitmap createToolbarIcon(@NonNull Resources resources,
                                           @NonNull final Bitmap icon) {
        final int padding = resources.getDimensionPixelSize(R.dimen.margin_small);
        final int iconSize = resources.getDimensionPixelSize(R.dimen.toolbar_icon_size);
        final int sizeWithPadding = iconSize + (2 * padding);

        // Create a Bitmap backed Canvas to be the toolbar icon.
        final Bitmap toolbarIcon =
                Bitmap.createBitmap(sizeWithPadding, sizeWithPadding, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(toolbarIcon);
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        // Resize the app icon to Material Design size.
        final Bitmap scaledIcon = Bitmap.createScaledBitmap(icon, iconSize, iconSize, false);
        canvas.drawBitmap(scaledIcon, padding, padding, null);

        return toolbarIcon;
    }
}
