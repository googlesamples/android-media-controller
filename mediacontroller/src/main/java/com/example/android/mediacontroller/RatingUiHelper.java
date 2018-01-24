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
package com.example.android.mediacontroller;

import android.annotation.SuppressLint;
import android.support.annotation.IdRes;
import android.support.design.widget.TextInputLayout;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.text.Editable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;

/**
 * Helper class to manage displaying and setting different kinds of media {@link RatingCompat}s.
 */
public abstract class RatingUiHelper {

    /**
     * Returns whether the given view is enabled with the current rating
     */
    protected abstract boolean enabled(@IdRes int viewId, RatingCompat rating);

    /**
     * Returns whether the given view is visible for the type of rating.
     * For example, a thumbs up/down rating will not display stars or heart.
     * And a 4-star rating will not display the fifth star.
     */
    protected abstract boolean visible(@IdRes int viewId);

    /**
     * Returns the rating that should be set when the given view is tapped.
     */
    protected abstract RatingCompat ratingFor(@IdRes int viewId, RatingCompat currentRating);

    /**
     * Returns the rating type that this RatingUiHelper handles.
     */
    protected abstract int ratingStyle();

    private final ViewGroup rootView;
    private final MediaControllerCompat controller;
    private RatingCompat currentRating;

    public RatingUiHelper(ViewGroup viewGroup, MediaControllerCompat mediaController) {
        rootView = viewGroup;
        for (int i = 0; i < rootView.getChildCount(); ++i) {
            View view = rootView.getChildAt(i);
            view.setVisibility(visible(view.getId()) ? View.VISIBLE : View.GONE);
            if (!(view instanceof Editable)) {
                view.setOnClickListener(this::onClick);
            }
        }
        controller = mediaController;
        currentRating = unrated();
    }

    private void onClick(View view) {
        RatingCompat newRating = ratingFor(view.getId(), currentRating);
        controller.getTransportControls().setRating(newRating);
        currentRating = newRating;
    }

    public void setRating(RatingCompat rating) {
        if (rating == null) {
            rating = unrated();
        }
        for (int i = 0; i < rootView.getChildCount(); ++i) {
            View view = rootView.getChildAt(i);
            if (view instanceof ImageView) {
                ImageView icon = (ImageView) view;
                final int tint = enabled(view.getId(), rating)
                        ? R.color.colorPrimary
                        : R.color.colorInactive;
                DrawableCompat.setTint(icon.getDrawable(),
                        ContextCompat.getColor(rootView.getContext(), tint));
            } else {
                view.setEnabled(enabled(view.getId(), rating));
            }
        }
        currentRating = rating;
    }

    public RatingCompat unrated() {
        return RatingCompat.newUnratedRating(ratingStyle());
    }

    public static class Stars3 extends RatingUiHelper {

        public Stars3(ViewGroup viewGroup, MediaControllerCompat mediaController) {
            super(viewGroup, mediaController);
        }

        @Override
        protected boolean enabled(int viewId, RatingCompat rating) {
            float starRating = rating.getStarRating();
            if (viewId == R.id.rating_star_1) {
                return starRating >= 1.0f;
            }
            if (viewId == R.id.rating_star_2) {
                return starRating >= 2.0f;
            }
            if (viewId == R.id.rating_star_3) {
                return starRating >= 3.0f;
            }
            return false;
        }

        @Override
        protected boolean visible(int viewId) {
            return viewId == R.id.rating_star_1
                    || viewId == R.id.rating_star_2
                    || viewId == R.id.rating_star_3;
        }

        @Override
        protected RatingCompat ratingFor(int viewId, RatingCompat currentRating) {
            if (viewId == R.id.rating_star_1) {
                return stars(1);
            }
            if (viewId == R.id.rating_star_2) {
                return stars(2);
            }
            if (viewId == R.id.rating_star_3) {
                return stars(3);
            }
            return null;
        }

        @Override
        protected int ratingStyle() {
            return RatingCompat.RATING_3_STARS;
        }

        protected RatingCompat stars(int starCount) {
            return RatingCompat.newStarRating(ratingStyle(), starCount);
        }
    }

    public static class Stars4 extends Stars3 {

        public Stars4(ViewGroup viewGroup, MediaControllerCompat mediaController) {
            super(viewGroup, mediaController);
        }

        @Override
        protected boolean enabled(int viewId, RatingCompat rating) {
            if (viewId == R.id.rating_star_4) {
                return rating.getStarRating() >= 4.0f;
            }
            return super.enabled(viewId, rating);
        }

        @Override
        protected boolean visible(int viewId) {
            if (viewId == R.id.rating_star_4) {
                return true;
            }
            return super.visible(viewId);
        }

        @Override
        protected RatingCompat ratingFor(int viewId, RatingCompat currentRating) {
            if (viewId == R.id.rating_star_4) {
                return stars(4);
            }
            return super.ratingFor(viewId, currentRating);
        }

        @Override
        protected int ratingStyle() {
            return RatingCompat.RATING_4_STARS;
        }
    }

    public static class Stars5 extends Stars4 {

        public Stars5(ViewGroup viewGroup, MediaControllerCompat mediaController) {
            super(viewGroup, mediaController);
        }

        @Override
        protected boolean enabled(int viewId, RatingCompat rating) {
            if (viewId == R.id.rating_star_5) {
                return rating.getStarRating() >= 5.0f;
            }
            return super.enabled(viewId, rating);
        }

        @Override
        protected boolean visible(int viewId) {
            if (viewId == R.id.rating_star_5) {
                return true;
            }
            return super.visible(viewId);
        }

        @Override
        protected RatingCompat ratingFor(int viewId, RatingCompat currentRating) {
            if (viewId == R.id.rating_star_5) {
                return stars(5);
            }
            return super.ratingFor(viewId, currentRating);
        }

        @Override
        protected int ratingStyle() {
            return RatingCompat.RATING_5_STARS;
        }
    }

    public static class Thumbs extends RatingUiHelper {

        public Thumbs(ViewGroup viewGroup, MediaControllerCompat mediaController) {
            super(viewGroup, mediaController);
        }

        @Override
        protected boolean enabled(int viewId, RatingCompat rating) {
            return (rating.isThumbUp() && viewId == R.id.rating_thumb_up)
                    || (isThumbDown(rating) && viewId == R.id.rating_thumb_down);
        }

        @Override
        protected boolean visible(int viewId) {
            return viewId == R.id.rating_thumb_up || viewId == R.id.rating_thumb_down;
        }

        @Override
        protected RatingCompat ratingFor(int viewId, RatingCompat currentRating) {
            if (enabled(viewId, currentRating)) {
                // User tapped on current thumb rating, so reset the rating.
                return unrated();
            }
            return RatingCompat.newThumbRating(viewId == R.id.rating_thumb_up);
        }

        @Override
        protected int ratingStyle() {
            return RatingCompat.RATING_THUMB_UP_DOWN;
        }

        private static boolean isThumbDown(RatingCompat rating) {
            return rating.isRated() && !rating.isThumbUp();
        }
    }

    public static class Heart extends RatingUiHelper {

        public Heart(ViewGroup viewGroup, MediaControllerCompat mediaController) {
            super(viewGroup, mediaController);
        }

        @Override
        protected boolean enabled(int viewId, RatingCompat rating) {
            return rating.hasHeart();
        }

        @Override
        protected boolean visible(int viewId) {
            return viewId == R.id.rating_heart;
        }

        @Override
        protected RatingCompat ratingFor(int viewId, RatingCompat currentRating) {
            return RatingCompat.newHeartRating(!currentRating.hasHeart());
        }

        @Override
        protected int ratingStyle() {
            return RatingCompat.RATING_HEART;
        }
    }

    public static class Percentage extends RatingUiHelper {

        private final EditText percentageEditText;

        public Percentage(ViewGroup viewGroup, MediaControllerCompat mediaController) {
            super(viewGroup, mediaController);
            percentageEditText = ((TextInputLayout) viewGroup.findViewById(R.id.rating_percentage))
                    .getEditText();
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void setRating(RatingCompat rating) {
            if (rating == null) {
                rating = unrated();
            }
            percentageEditText.setText(
                    Integer.toString((int) (rating.getPercentRating() * 100), 10));
        }

        @Override
        protected boolean enabled(int viewId, RatingCompat rating) {
            return true;
        }

        @Override
        protected boolean visible(int viewId) {
            return viewId == R.id.rating_percentage || viewId == R.id.rating_percentage_set;
        }

        @Override
        protected RatingCompat ratingFor(int viewId, RatingCompat currentRating) {
            float percentage = Integer.parseInt(percentageEditText.getText().toString(), 10);
            return RatingCompat.newPercentageRating(percentage / 100.0f);
        }

        @Override
        protected int ratingStyle() {
            return RatingCompat.RATING_PERCENTAGE;
        }
    }

    public static class None extends RatingUiHelper {

        public None(ViewGroup viewGroup, MediaControllerCompat mediaController) {
            super(viewGroup, mediaController);
        }

        @Override
        protected boolean enabled(int viewId, RatingCompat rating) {
            return false;
        }

        @Override
        protected boolean visible(int viewId) {
            return false;
        }

        @Override
        protected RatingCompat ratingFor(int viewId, RatingCompat currentRating) {
            return null;
        }

        @Override
        protected int ratingStyle() {
            return RatingCompat.RATING_NONE;
        }
    }
}
