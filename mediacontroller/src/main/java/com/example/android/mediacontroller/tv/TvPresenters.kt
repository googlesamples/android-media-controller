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
package com.example.android.mediacontroller.tv

import android.annotation.TargetApi
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.support.v17.leanback.widget.ImageCardView
import android.support.v17.leanback.widget.Presenter
import android.text.TextUtils
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.example.android.mediacontroller.MediaAppDetails
import com.example.android.mediacontroller.R

/**
 * Used to display apps in TvLauncherFragment
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class MediaAppCardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup?): ViewHolder {
        if (parent == null) {
            return ViewHolder(null)
        }

        val card = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            findViewById<TextView>(R.id.title_text).apply {
                ellipsize = TextUtils.TruncateAt.END
            }
            findViewById<TextView>(R.id.content_text).apply {
                ellipsize = TextUtils.TruncateAt.MIDDLE
            }
        }
        return ViewHolder(card)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder?, item: Any?) {
        if (item == null) {
            return
        }

        val appDetails = item as MediaAppDetails
        (viewHolder?.view as ImageCardView).apply {
            titleText = appDetails.appName
            contentText = appDetails.packageName

            val hasBanner = (appDetails.banner != null)
            val image = appDetails.banner ?: appDetails.icon
            mainImage = BitmapDrawable(viewHolder.view.context.resources, image)
            if (hasBanner) {
                val width = resources.getDimensionPixelSize(R.dimen.tv_banner_width)
                val height = resources.getDimensionPixelSize(R.dimen.tv_banner_height)
                setMainImageDimensions(width, height)
            } else {
                val width = resources.getDimensionPixelSize(R.dimen.tv_icon_width)
                val height = resources.getDimensionPixelSize(R.dimen.tv_icon_height)
                setMainImageDimensions(width, height)
            }
            // FIT_XY scales the icon/banner such that it fills the ImageView; the dimensions of the
            // ImageView are set above to ensure that the image retains its original aspect ratio
            setMainImageScaleType(ImageView.ScaleType.FIT_XY)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder?) {
        (viewHolder?.view as ImageCardView).apply {
            mainImage = null
        }
    }
}