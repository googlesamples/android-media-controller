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
import android.support.v4.media.session.MediaControllerCompat;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes various {@link Action} objects on a {@link MediaControllerCompat}
 * based on user input.
 * <p>
 * Example: A "Play" action, triggered by "Play" UI button, will call
 * {@link MediaControllerCompat.TransportControls#play()}.
 */
public class Action {

    private static final String TAG = Action.class.getSimpleName();

    private int mId;
    private String mName;
    private MediaControllerAction mControllerAction;

    public Action(String name) {
        this(0, name);
    }

    public Action(int id, String name) {
        mId = id;
        mName = name;
    }

    public String getName() {
        return mName;
    }

    public int getId() {
        return mId;
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
        action.setMediaControllerAction((controller, id, extras) ->
                controller.getTransportControls().prepareFromSearch(id, extras));
        actions.add(action);

        action = new Action(context.getString(R.string.action_play_search));
        action.setMediaControllerAction((controller, id, extras) ->
                controller.getTransportControls().playFromSearch(id, extras));
        actions.add(action);

        action = new Action(context.getString(R.string.action_prepare_id));
        action.setMediaControllerAction((controller, id, extras) ->
                controller.getTransportControls().prepareFromMediaId(id, extras));
        actions.add(action);

        action = new Action(context.getString(R.string.action_play_id));
        action.setMediaControllerAction((controller, id, extras) ->
                controller.getTransportControls().playFromMediaId(id, extras));
        actions.add(action);

        action = new Action(context.getString(R.string.action_prepare_uri));
        action.setMediaControllerAction((controller, id, extras) -> {
            if (TextUtils.isEmpty(id)) {
                Log.w(TAG, "Must set URI");
                return;
            }
            Uri uri = Uri.parse(id);
            controller.getTransportControls().prepareFromUri(uri, extras);
        });
        actions.add(action);

        action = new Action(context.getString(R.string.action_play_uri));
        action.setMediaControllerAction((controller, id, extras) -> {
            if (TextUtils.isEmpty(id)) {
                Log.w(TAG, "Must set URI");
                return;
            }
            Uri uri = Uri.parse(id);
            controller.getTransportControls().playFromUri(uri, extras);
        });
        actions.add(action);

        action = new Action(context.getString(R.string.action_prepare));
        action.setMediaControllerAction((controller, id, extras) ->
                controller.getTransportControls().prepare());
        actions.add(action);

        action = new Action(context.getString(R.string.action_play));
        action.setMediaControllerAction((controller, id, extras) ->
                controller.getTransportControls().play());
        actions.add(action);

        return actions;
    }

    public static List<Action> createActions(@NonNull final Context context) {
        List<Action> actions = new ArrayList<>();
        Action action;

        action = new Action(R.id.action_resume, context.getString(R.string.action_resume));
        action.setMediaControllerAction((controller, id, extras) ->
                controller.getTransportControls().play());
        actions.add(action);

        action = new Action(R.id.action_pause, context.getString(R.string.action_pause));
        action.setMediaControllerAction((controller, id, extras) ->
                controller.getTransportControls().pause());
        actions.add(action);

        action = new Action(R.id.action_stop, context.getString(R.string.action_stop));
        action.setMediaControllerAction((controller, id, extras) ->
                controller.getTransportControls().stop());
        actions.add(action);

        action = new Action(R.id.action_skip_next, context.getString(R.string.action_skip_next));
        action.setMediaControllerAction((controller, id, extras) ->
                controller.getTransportControls().skipToNext());
        actions.add(action);

        action = new Action(R.id.action_skip_previous,
                context.getString(R.string.action_skip_previous));
        action.setMediaControllerAction((controller, id, extras) ->
                controller.getTransportControls().skipToPrevious());
        actions.add(action);

        action = new Action(R.id.action_skip_30s_backward,
                context.getString(R.string.action_skip_30s_backward));
        action.setMediaControllerAction((controller, id, extras) -> {
            long positionMs = controller.getPlaybackState().getPosition();
            controller.getTransportControls().seekTo(positionMs - 1000 * 30);
        });
        actions.add(action);

        action = new Action(R.id.action_skip_30s_forward,
                context.getString(R.string.action_skip_30s_forward));
        action.setMediaControllerAction((controller, id, extras) -> {
            long positionMs = controller.getPlaybackState().getPosition();
            controller.getTransportControls().seekTo(positionMs + 1000 * 30);
        });
        actions.add(action);

        action = new Action(R.id.action_fast_forward,
                context.getString(R.string.action_fast_forward));
        action.setMediaControllerAction((controller, id, extras) ->
                controller.getTransportControls().fastForward());
        actions.add(action);

        action = new Action(R.id.action_fast_rewind,
                context.getString(R.string.action_fast_rewind));
        action.setMediaControllerAction((controller, id, extras) ->
                controller.getTransportControls().rewind());
        actions.add(action);

        return actions;
    }

    public interface MediaControllerAction {
        void run(@NonNull MediaControllerCompat controller, @Nullable String id,
                 @Nullable Bundle extras);
    }
}
