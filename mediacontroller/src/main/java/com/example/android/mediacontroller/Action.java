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

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes various {@link Action} objects on a {@link MediaControllerCompat}
 * based on user input.
 *
 * Example: A "Play" action, triggered by "Play" UI button, will call
 * {@link MediaControllerCompat.TransportControls#play()}.
 */
public class Action {

    private static final String TAG = Action.class.getSimpleName();

    private String mName;
    private MediaControllerAction mControllerAction;

    public Action(String name) {
        mName = name;
    }

    public String getName() {
        return mName;
    }

    public void setMediaControllerAction(MediaControllerAction controllerAction) {
        mControllerAction = controllerAction;
    }

    public MediaControllerAction getMediaControllerAction() {
        return mControllerAction;
    }

    public static List<Action> createPreparePlayActions(@NonNull final Context context) {
        List<Action> actions = new ArrayList<>();
        Action action;

        /*
         * The order of these must match the order of the string-array, "input_options",
         * contained in strings.xml.
         */
        action = new Action(context.getString(R.string.action_prepare_search));
        action.setMediaControllerAction(new MediaControllerAction() {
            @Override
            public void run(@NonNull MediaControllerCompat controller, String id, Bundle extras) {
                controller.getTransportControls().prepareFromSearch(id, extras);
            }
        });
        actions.add(action);

        action = new Action(context.getString(R.string.action_play_search));
        action.setMediaControllerAction(new MediaControllerAction() {
            @Override
            public void run(@NonNull MediaControllerCompat controller, String id, Bundle extras) {
                controller.getTransportControls().playFromSearch(id, extras);
            }
        });
        actions.add(action);

        action = new Action(context.getString(R.string.action_prepare_id));
        action.setMediaControllerAction(new MediaControllerAction() {
            @Override
            public void run(@NonNull MediaControllerCompat controller, String id, Bundle extras) {
                controller.getTransportControls().prepareFromMediaId(id, extras);
            }
        });
        actions.add(action);

        action = new Action(context.getString(R.string.action_play_id));
        action.setMediaControllerAction(new MediaControllerAction() {
            @Override
            public void run(@NonNull MediaControllerCompat controller, String id, Bundle extras) {
                controller.getTransportControls().playFromMediaId(id, extras);
            }
        });
        actions.add(action);

        action = new Action(context.getString(R.string.action_prepare_uri));
        action.setMediaControllerAction(new MediaControllerAction() {
            @Override
            public void run(@NonNull MediaControllerCompat controller, String id, Bundle extras) {
                if (TextUtils.isEmpty(id)) {
                    Log.w(TAG, "Must set URI");
                    return;
                }
                Uri uri = Uri.parse(id);
                controller.getTransportControls().prepareFromUri(uri, extras);
            }
        });
        actions.add(action);

        action = new Action(context.getString(R.string.action_play_uri));
        action.setMediaControllerAction(new MediaControllerAction() {
            @Override
            public void run(@NonNull MediaControllerCompat controller, String id, Bundle extras) {
                if (TextUtils.isEmpty(id)) {
                    Log.w(TAG, "Must set URI");
                    return;
                }
                Uri uri = Uri.parse(id);
                controller.getTransportControls().playFromUri(uri, extras);
            }
        });
        actions.add(action);

        action = new Action(context.getString(R.string.action_prepare));
        action.setMediaControllerAction(new MediaControllerAction() {
            @Override
            public void run(@NonNull MediaControllerCompat controller, String id, Bundle extras) {
                controller.getTransportControls().prepare();
            }
        });
        actions.add(action);

        action = new Action(context.getString(R.string.action_play));
        action.setMediaControllerAction(new MediaControllerAction() {
            @Override
            public void run(@NonNull MediaControllerCompat controller, String id, Bundle extras) {
                controller.getTransportControls().play();
            }
        });
        actions.add(action);

        return actions;
    }

    public static List<Action> createActions(@NonNull final Context context) {
        List<Action> actions = new ArrayList<>();
        Action action;

        action = new Action(context.getString(R.string.action_resume));
        action.setMediaControllerAction(new MediaControllerAction() {
            @Override
            public void run(@NonNull MediaControllerCompat controller, String id, Bundle extras) {
                controller.getTransportControls().play();
            }
        });
        actions.add(action);

        action = new Action(context.getString(R.string.action_pause));
        action.setMediaControllerAction(new MediaControllerAction() {
            @Override
            public void run(@NonNull MediaControllerCompat controller, String id, Bundle extras) {
                controller.getTransportControls().pause();
            }
        });
        actions.add(action);

        action = new Action(context.getString(R.string.action_stop));
        action.setMediaControllerAction(new MediaControllerAction() {
            @Override
            public void run(@NonNull MediaControllerCompat controller, String id, Bundle extras) {
                controller.getTransportControls().stop();
            }
        });
        actions.add(action);

        action = new Action(context.getString(R.string.action_next));
        action.setMediaControllerAction(new MediaControllerAction() {
            @Override
            public void run(@NonNull MediaControllerCompat controller, String id, Bundle extras) {
                controller.getTransportControls().skipToNext();
            }
        });
        actions.add(action);

        action = new Action(context.getString(R.string.action_previous));
        action.setMediaControllerAction(new MediaControllerAction() {
            @Override
            public void run(@NonNull MediaControllerCompat controller, String id, Bundle extras) {
                controller.getTransportControls().skipToPrevious();
            }
        });
        actions.add(action);

        action = new Action(context.getString(R.string.action_thumbs_up));
        action.setMediaControllerAction(new MediaControllerAction() {
            @Override
            public void run(@NonNull MediaControllerCompat controller, String id, Bundle extras) {
                controller.getTransportControls().setRating(RatingCompat.newThumbRating(true));
            }
        });
        actions.add(action);

        action = new Action(context.getString(R.string.action_thumbs_down));
        action.setMediaControllerAction(new MediaControllerAction() {
            @Override
            public void run(@NonNull MediaControllerCompat controller, String id, Bundle extras) {
                controller.getTransportControls().setRating(RatingCompat.newThumbRating(false));
            }
        });
        actions.add(action);

        action = new Action(context.getString(R.string.action_skip_30s_backward));
        action.setMediaControllerAction(new MediaControllerAction() {
            @Override
            public void run(@NonNull MediaControllerCompat controller, String id, Bundle extras) {
                long positionMs = controller.getPlaybackState().getPosition();
                controller.getTransportControls().seekTo(positionMs - 1000*30);
            }
        });
        actions.add(action);

        action = new Action(context.getString(R.string.action_skip_30s_forward));
        action.setMediaControllerAction(new MediaControllerAction() {
            @Override
            public void run(@NonNull MediaControllerCompat controller, String id, Bundle extras) {
                long positionMs = controller.getPlaybackState().getPosition();
                controller.getTransportControls().seekTo(positionMs + 1000*30);
            }
        });
        actions.add(action);

        action = new Action(context.getString(R.string.action_fast_forward));
        action.setMediaControllerAction(new MediaControllerAction() {
            @Override
            public void run(@NonNull MediaControllerCompat controller, String id, Bundle extras) {
                controller.getTransportControls().fastForward();
            }
        });
        actions.add(action);

        action = new Action(context.getString(R.string.action_rewind));
        action.setMediaControllerAction(new MediaControllerAction() {
            @Override
            public void run(@NonNull MediaControllerCompat controller, String id, Bundle extras) {
                controller.getTransportControls().rewind();
            }
        });
        actions.add(action);

        return actions;
    }

    public interface MediaControllerAction {
        public void run(@NonNull MediaControllerCompat controller, @Nullable String id,
                        @Nullable Bundle extras);
    }
}
