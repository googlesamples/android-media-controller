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

import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * A sectioned RecyclerView Adapter that displays list(s) of media apps.
 */
public class MediaAppListAdapter extends RecyclerView.Adapter<ViewHolder> {

    /**
     * Click listener for when an app is selected.
     */
    public interface MediaAppSelectedListener {
        void onMediaAppClicked(@NonNull MediaAppDetails mediaAppDetails, @NonNull Boolean isTest);
    }

    /**
     * The types of views that this recycler view adapter displays.
     */
    enum ViewType {
        /**
         * A media app entry, with icon, app name, and package name. Tapping on one of these entries
         * will fire the MediaAppSelectedListener callback.
         */
        AppEntry(R.layout.media_app_item) {
            @Override
            ViewHolder create(ViewGroup itemLayout) {
                return new AppEntry.ViewHolder(itemLayout);
            }
        },
        /**
         * A section header, only displayed if the adapter has multiple sections.
         */
        Header(R.layout.media_app_list_header) {
            @Override
            ViewHolder create(ViewGroup itemLayout) {
                return new Header.ViewHolder(itemLayout);
            }
        },
        /**
         * An error, such as "no apps", or "missing permission". Can optionally provide an action.
         */
        Error(R.layout.media_app_list_error) {
            @Override
            ViewHolder create(ViewGroup itemLayout) {
                return new Error.ViewHolder(itemLayout);
            }
        };

        final int layoutId;

        ViewType(@LayoutRes int layoutId) {
            this.layoutId = layoutId;
        }

        abstract ViewHolder create(ViewGroup itemLayout);
    }

    /**
     * An interface for items in the recycler view.
     */
    interface RecyclerViewItem {

        ViewType viewType();

        void bindTo(ViewHolder holder);
    }

    /**
     * An implementation of {@link RecyclerViewItem} for media apps.
     */
    static class AppEntry implements RecyclerViewItem {

        private final MediaAppDetails appDetails;
        private final MediaAppSelectedListener appSelectedListener;

        public AppEntry(MediaAppDetails appDetails,
                        MediaAppSelectedListener appSelectedListener) {
            this.appDetails = appDetails;
            this.appSelectedListener = appSelectedListener;
        }

        @Override
        public ViewType viewType() {
            return ViewType.AppEntry;
        }

        @Override
        public void bindTo(RecyclerView.ViewHolder vh) {
            ViewHolder holder = (ViewHolder) vh;
            holder.appIconView.setImageBitmap(appDetails.icon);
            holder.appIconView.setContentDescription(
                    holder.appIconView.getContext().getString(R.string.app_icon_desc, appDetails.appName));
            holder.appNameView.setText(appDetails.appName);
            holder.appPackageView.setText(appDetails.packageName);

            holder.controlButton.setOnClickListener(view ->
                    appSelectedListener.onMediaAppClicked(appDetails, false));
            holder.testButton.setOnClickListener(view ->
                    appSelectedListener.onMediaAppClicked(appDetails, true));
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final ImageView appIconView;
            private final TextView appNameView;
            private final TextView appPackageView;
            private final Button controlButton;
            private final Button testButton;

            private ViewHolder(View itemView) {
                super(itemView);
                appIconView = itemView.findViewById(R.id.app_icon);
                appNameView = itemView.findViewById(R.id.app_name);
                appPackageView = itemView.findViewById(R.id.package_name);
                controlButton = itemView.findViewById(R.id.app_control);
                testButton = itemView.findViewById(R.id.app_test);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            AppEntry that = (AppEntry) o;
            return appDetails.equals(that.appDetails);
        }

        @Override
        public int hashCode() {
            return appDetails.hashCode();
        }
    }

    /**
     * An implementation of {@link RecyclerViewItem} for headers.
     */
    static class Header implements RecyclerViewItem {

        private final int labelResId;

        public Header(@StringRes int label) {
            labelResId = label;
        }

        @Override
        public ViewType viewType() {
            return ViewType.Header;
        }

        @Override
        public void bindTo(RecyclerView.ViewHolder vh) {
            ViewHolder holder = (ViewHolder) vh;
            holder.headerView.setText(labelResId);
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView headerView;

            private ViewHolder(View itemView) {
                super(itemView);
                headerView = itemView.findViewById(R.id.header_text);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Header that = (Header) o;
            return labelResId == that.labelResId;
        }

        @Override
        public int hashCode() {
            return labelResId;
        }
    }

    /**
     * An implementation of {@link RecyclerViewItem} for error states, with an optional action.
     */
    static class Error implements RecyclerViewItem {

        private final int errorMsgId;
        private final int errorDetailId;
        private final int errorButtonId;
        private final View.OnClickListener clickListener;

        public Error(@StringRes int message, @StringRes int detail, @StringRes int buttonText,
                     @Nullable View.OnClickListener onClickListener) {
            this.errorMsgId = message;
            this.errorDetailId = detail;
            this.errorButtonId = buttonText;
            this.clickListener = onClickListener;
        }

        @Override
        public ViewType viewType() {
            return ViewType.Error;
        }

        @Override
        public void bindTo(RecyclerView.ViewHolder vh) {
            ViewHolder holder = (ViewHolder) vh;
            holder.errorMessage.setText(errorMsgId);
            holder.errorDetail.setText(errorDetailId);
            holder.errorMessage.setVisibility(errorDetailId == 0 ? View.GONE : View.VISIBLE);
            holder.actionButton.setOnClickListener(clickListener);
            if (errorButtonId == 0 || clickListener == null) {
                holder.actionButton.setVisibility(View.GONE);
            } else {
                holder.actionButton.setVisibility(View.VISIBLE);
                holder.actionButton.setText(errorButtonId);
            }
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView errorMessage;
            private final TextView errorDetail;
            private final Button actionButton;

            private ViewHolder(View itemView) {
                super(itemView);
                errorMessage = itemView.findViewById(R.id.error_message);
                errorDetail = itemView.findViewById(R.id.error_detail);
                actionButton = itemView.findViewById(R.id.error_action);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Error that = (Error) o;
            if (errorMsgId != that.errorMsgId) {
                return false;
            }
            if (errorDetailId != that.errorDetailId) {
                return false;
            }
            if (errorButtonId != that.errorButtonId) {
                return false;
            }
            return clickListener != null ? clickListener.equals(that.clickListener)
                    : that.clickListener == null;
        }

        @Override
        public int hashCode() {
            int result = errorMsgId;
            result = 31 * result + errorDetailId;
            result = 31 * result + errorButtonId;
            return result;
        }
    }

    /**
     * Represents a section of items in the recycler view.
     */
    public final class Section {
        @StringRes
        private final int mLabel;
        private final List<RecyclerViewItem> mItems = new ArrayList<>();

        private Section(@StringRes int label) {
            this.mLabel = label;
        }

        public void setError(@StringRes int message, @StringRes int detail) {
            setError(message, detail, 0, null);
        }

        public void setError(@StringRes int message, @StringRes int detail,
                             @StringRes int buttonText, View.OnClickListener onClickListener) {
            mItems.clear();
            mItems.add(new Error(message, detail, buttonText, onClickListener));
            updateData();
        }

        public void setAppsList(@NonNull final List<? extends MediaAppDetails> appEntries) {
            mItems.clear();
            for (MediaAppDetails appEntry : appEntries) {
                mItems.add(new AppEntry(appEntry, mMediaAppSelectedListener));
            }
            updateData();
        }

    }

    private final List<Section> mSections = new ArrayList<>();

    private final List<RecyclerViewItem> mRecyclerViewEntries = new ArrayList<>();
    private final MediaAppSelectedListener mMediaAppSelectedListener;

    MediaAppListAdapter(@NonNull MediaAppSelectedListener itemClickListener) {
        mMediaAppSelectedListener = itemClickListener;
    }

    Section addSection(@StringRes int label) {
        Section section = new Section(label);
        mSections.add(section);
        return section;
    }

    private void updateData() {
        final List<RecyclerViewItem> oldEntries = new ArrayList<>(mRecyclerViewEntries);
        mRecyclerViewEntries.clear();
        for (Section section : mSections) {
            if (mSections.size() > 1) {
                mRecyclerViewEntries.add(new Header(section.mLabel));
            }
            mRecyclerViewEntries.addAll(section.mItems);
        }

        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldEntries.size();
            }

            @Override
            public int getNewListSize() {
                return mRecyclerViewEntries.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return oldEntries.get(oldItemPosition)
                        .equals(mRecyclerViewEntries.get(newItemPosition));
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return areItemsTheSame(oldItemPosition, newItemPosition);
            }
        });
        diffResult.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewType type = ViewType.values()[viewType];
        final ViewGroup itemLayout = (ViewGroup) LayoutInflater.from(parent.getContext())
                .inflate(type.layoutId, parent, false);
        return type.create(itemLayout);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        mRecyclerViewEntries.get(position).bindTo(holder);
    }

    @Override
    public int getItemViewType(int position) {
        return mRecyclerViewEntries.get(position).viewType().ordinal();
    }

    @Override
    public int getItemCount() {
        return mRecyclerViewEntries.size();
    }
}
