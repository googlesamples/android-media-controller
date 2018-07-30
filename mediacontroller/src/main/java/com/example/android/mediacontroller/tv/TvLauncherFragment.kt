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
import android.os.Build
import android.os.Bundle
import android.support.v17.leanback.app.BrowseSupportFragment
import android.support.v17.leanback.widget.ArrayObjectAdapter
import android.support.v17.leanback.widget.DiffCallback
import android.support.v17.leanback.widget.HeaderItem
import android.support.v17.leanback.widget.ListRow
import android.support.v17.leanback.widget.ListRowPresenter
import android.support.v17.leanback.widget.OnItemViewClickedListener
import android.support.v17.leanback.widget.Presenter
import com.example.android.mediacontroller.MediaAppDetails
import com.example.android.mediacontroller.R
import com.example.android.mediacontroller.tasks.FindMediaAppsTask
import com.example.android.mediacontroller.tasks.FindMediaBrowserAppsTask

/**
 * Displays a list of media apps that currently implement MediaBrowserService.
 */
class TvLauncherFragment : BrowseSupportFragment() {
    private val browserAppsUpdated = object : FindMediaAppsTask.AppListUpdatedCallback {
        override fun onAppListUpdated(mediaAppEntries: List<MediaAppDetails>) {
            listBrowserApps(mediaAppEntries)
        }
    }

    private val mediaAppsRowAdapter = ArrayObjectAdapter(ListRowPresenter())
    private val mediaAppClickedListener = OnItemViewClickedListener { _, item, _, _ ->
        if (item is MediaAppDetails) {
            activity?.let {
                startActivity(TvTestingActivity.buildIntent(it, item))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupUIElements()

        adapter = mediaAppsRowAdapter
        onItemViewClickedListener = mediaAppClickedListener
    }

    override fun onStart() {
        super.onStart()

        FindMediaBrowserAppsTask(requireContext(), browserAppsUpdated).execute()
    }

    @TargetApi(Build.VERSION_CODES.M)
    private fun setupUIElements() {
        badgeDrawable = activity?.resources?.getDrawable(R.drawable.tv_banner, null)
        headersState = HEADERS_DISABLED
    }

    private inline fun <reified T> Collection<T>.toArrayObjectAdapter(
            presenter: Presenter
    ): ArrayObjectAdapter {
        val adapter = ArrayObjectAdapter(presenter)
        this.forEach { item -> adapter.add(item) }
        return adapter
    }

    private fun listBrowserApps(apps: List<MediaAppDetails>) {
        val listRowAdapter = apps.toArrayObjectAdapter(MediaAppCardPresenter())
        val headerItem = HeaderItem(BROWSER_HEADER_ID, BROWSER_HEADER_NAME)
        val diffCallback = object : DiffCallback<ListRow>() {
            override fun areItemsTheSame(oldItem: ListRow, newItem: ListRow): Boolean {
                return oldItem.headerItem.id == newItem.headerItem.id
            }

            override fun areContentsTheSame(oldItem: ListRow, newItem: ListRow): Boolean {
                val oldAdapter = oldItem.adapter
                val newAdapter = newItem.adapter
                val sameSize = oldAdapter.size() == newAdapter.size()
                if (!sameSize) {
                    return false
                }

                return (0 until oldAdapter.size()).none {
                    oldAdapter[it] as MediaAppDetails != newAdapter[it] as MediaAppDetails
                }
            }
        }
        mediaAppsRowAdapter.setItems(
                mutableListOf(ListRow(headerItem, listRowAdapter)),
                diffCallback
        )
    }

    companion object {
        private const val BROWSER_HEADER_ID = 1L
        private const val BROWSER_HEADER_NAME = "MediaBrowserService Implementations"
    }
}
