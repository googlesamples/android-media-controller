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

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * This class connects to a {@link android.support.v4.media.MediaBrowserServiceCompat}
 * in order to get a {@link MediaControllerCompat}.
 * Buttons are displayed on screen so that the user can exercise
 * the {@link android.support.v4.media.session.MediaSessionCompat.Callback}
 * methods of the media app.
 * <p>
 * Example: If you install the UAMP app and this Monkey Test app, you will be able
 * to test UAMP media controls.
 */
public class MediaAppControllerActivity extends AppCompatActivity {
    private static final String TAG = MediaAppControllerActivity.class.getSimpleName();

    // Key names used for saving/restoring instance state.
    private static final String STATE_APP_DETAILS_KEY =
            "com.example.android.mediacontroller.STATE_APP_DETAILS_KEY";
    private static final String STATE_URI_KEY =
            "com.example.android.mediacontroller.STATE_URI_KEY";

    // Key names for external extras.
    private static final String PACKAGE_NAME_EXTRA =
            "com.example.android.mediacontroller.PACKAGE_NAME";
    private static final String SEARCH_EXTRA = "com.example.android.mediacontroller.SEARCH";
    private static final String URI_EXTRA = "com.example.android.mediacontroller.URI";
    private static final String MEDIA_ID_EXTRA = "com.example.android.mediacontroller.MEDIA_ID";

    // Hint to use the currently loaded app rather than specifying a package.
    private static final String CURRENT_PACKAGE = "current";

    // Parameters for deep link URI.
    private static final String SEARCH_PARAM = "search";
    private static final String MEDIA_ID_PARAM = "id";
    private static final String URI_PARAM = "uri";

    // Key name for Intent extras.
    private static final String APP_DETAILS_EXTRA =
            "com.example.android.mediacontroller.APP_DETAILS_EXTRA";

    // Index values for spinner.
    private static final int SEARCH_INDEX = 0;
    private static final int MEDIA_ID_INDEX = 1;
    private static final int URI_INDEX = 2;

    private MediaAppDetails mMediaAppDetails;
    private MediaControllerCompat mController;
    private MediaBrowserCompat mBrowser;
    private AudioFocusHelper mAudioFocusHelper;

    private View mRootView;
    private Spinner mInputTypeView;
    private EditText mUriInput;
    private TextView mMediaInfoText;
    private Button mStartSessionActivityButton;

    private ImageView mMediaAlbumArtView;
    private TextView mMediaTitleView;
    private TextView mMediaArtistView;
    private TextView mMediaAlbumView;

    private final SparseArray<ImageButton> mActionButtonMap = new SparseArray<>();

    /**
     * Builds an {@link Intent} to launch this Activity with a set of extras.
     *
     * @param activity   The Activity building the Intent.
     * @param appDetails The app details about the media app to connect to.
     * @return An Intent that can be used to start the Activity.
     */
    public static Intent buildIntent(final Activity activity,
                                     final MediaAppDetails appDetails) {
        final Intent intent = new Intent(activity, MediaAppControllerActivity.class);
        intent.putExtra(APP_DETAILS_EXTRA, appDetails);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_app_controller);
        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mRootView = findViewById(R.id.root_view);
        mInputTypeView = findViewById(R.id.input_type);
        mUriInput = findViewById(R.id.uri_id_query);
        mMediaInfoText = findViewById(R.id.media_info);

        mMediaAlbumArtView = findViewById(R.id.media_art);
        mMediaTitleView = findViewById(R.id.media_title);
        mMediaArtistView = findViewById(R.id.media_artist);
        mMediaAlbumView = findViewById(R.id.media_album);

        if (savedInstanceState != null) {
            mMediaAppDetails = savedInstanceState.getParcelable(STATE_APP_DETAILS_KEY);
            mUriInput.setText(savedInstanceState.getString(STATE_URI_KEY));
        }

        handleIntent(getIntent());
        setupButtons();
        setupMediaController();

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            final Bitmap toolbarIcon =
                    BitmapUtils.createToolbarIcon(getResources(), mMediaAppDetails.icon);
            actionBar.setIcon(new BitmapDrawable(getResources(), toolbarIcon));
            actionBar.setTitle(mMediaAppDetails.appName);
        }

        final ViewPager viewPager = findViewById(R.id.view_pager);
        viewPager.setAdapter(new PagerAdapter() {
            private final int[] pages = {
                    R.id.prepare_play_page,
                    R.id.controls_page
            };

            @Override
            public int getCount() {
                return pages.length;
            }

            @Override
            public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
                return view == object;
            }

            @NonNull
            @Override
            public Object instantiateItem(@NonNull ViewGroup container, int position) {
                return findViewById(pages[position]);
            }
        });
        final TabLayout pageIndicator = findViewById(R.id.page_indicator);
        pageIndicator.setupWithViewPager(viewPager);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        final Uri data = intent.getData();
        final String appPackageName;
        if (data != null) {
            appPackageName = data.getHost();

            final Set<String> params = data.getQueryParameterNames();
            if (params.contains(SEARCH_PARAM)) {
                mInputTypeView.setSelection(SEARCH_INDEX);
                mUriInput.setText(data.getQueryParameter(SEARCH_PARAM));
            } else if (params.contains(MEDIA_ID_PARAM)) {
                mInputTypeView.setSelection(MEDIA_ID_INDEX);
                mUriInput.setText(data.getQueryParameter(MEDIA_ID_PARAM));
            } else if (params.contains(URI_PARAM)) {
                mInputTypeView.setSelection(URI_INDEX);
                mUriInput.setText(data.getQueryParameter(URI_PARAM));
            }
        } else if (intent.hasExtra(PACKAGE_NAME_EXTRA)) {
            appPackageName = intent.getStringExtra(PACKAGE_NAME_EXTRA);
        } else {
            appPackageName = null;
        }

        // Create app details from URI, if one was present.
        if (appPackageName != null) {
            if (mMediaAppDetails == null || !appPackageName.equals(CURRENT_PACKAGE)) {
                final MediaAppDetails appDetails = buildMediaDetailsFromPackage(appPackageName);

                if (appDetails == null) {
                    // Couldn't build the media app details so alert the user and exit.
                    Toast.makeText(this,
                            getString(R.string.no_app_for_package, appPackageName),
                            Toast.LENGTH_LONG)
                            .show();
                    finish();
                    return;
                } else {
                    mMediaAppDetails = appDetails;
                }
            }
        }

        final Bundle extras = intent.getExtras();
        if (extras != null) {
            // Pull data out of the extras, if they're there.
            if (extras.containsKey(SEARCH_EXTRA)) {
                mInputTypeView.setSelection(SEARCH_INDEX);
                mUriInput.setText(extras.getString(SEARCH_EXTRA));
            } else if (extras.containsKey(MEDIA_ID_EXTRA)) {
                mInputTypeView.setSelection(MEDIA_ID_INDEX);
                mUriInput.setText(extras.getString(MEDIA_ID_EXTRA));
            } else if (extras.containsKey(URI_EXTRA)) {
                mInputTypeView.setSelection(URI_INDEX);
                mUriInput.setText(extras.getString(URI_EXTRA));
            }

            // It's also possible we're here from LaunchActivity, which did all this work for us.
            if (extras.containsKey(APP_DETAILS_EXTRA)) {
                mMediaAppDetails = extras.getParcelable(APP_DETAILS_EXTRA);
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);

        out.putParcelable(STATE_APP_DETAILS_KEY, mMediaAppDetails);
        out.putString(STATE_URI_KEY, mUriInput.getText().toString());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        mMediaAppDetails = savedInstanceState.getParcelable(STATE_APP_DETAILS_KEY);
        mUriInput.setText(savedInstanceState.getString(STATE_URI_KEY));
    }

    private void setupMediaController() {
        if (mBrowser != null) {
            mBrowser.disconnect();
            mBrowser = null;
            mController = null;
        }

        mBrowser = new MediaBrowserCompat(this, mMediaAppDetails.mediaServiceComponentName,
                new MyConnectionCallback(this), null);
        mBrowser.connect();
    }

    private MediaAppDetails buildMediaDetailsFromPackage(final String packageName) {
        final PackageManager packageManager = getPackageManager();

        final Intent mediaBrowserIntent = new Intent(MediaBrowserServiceCompat.SERVICE_INTERFACE);
        final List<ResolveInfo> services =
                packageManager.queryIntentServices(mediaBrowserIntent,
                        PackageManager.GET_RESOLVED_FILTER);
        for (ResolveInfo info : services) {
            if (info.serviceInfo.packageName.equals(packageName)) {
                final Drawable icon = info.loadIcon(packageManager);
                final String name = info.loadLabel(packageManager).toString();
                final String serviceName = info.serviceInfo.name;
                final ComponentName serviceComponentName =
                        new ComponentName(packageName, serviceName);
                return new MediaAppDetails(
                        name,
                        serviceComponentName,
                        BitmapUtils.convertDrawable(getResources(), icon));
            }
        }

        // Failed to find package.
        return null;
    }

    private void setupButtons() {

        final PreparePlayHandler preparePlayHandler = new PreparePlayHandler(this);
        findViewById(R.id.action_prepare).setOnClickListener(preparePlayHandler);
        findViewById(R.id.action_play).setOnClickListener(preparePlayHandler);

        mStartSessionActivityButton = findViewById(R.id.start_session_activity_button);
        mStartSessionActivityButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mController != null) {
                    startSessionActivity(mController);
                } else {
                    Log.w(TAG, "Media session does not contain an Activity to start");
                }
            }
        });

        mAudioFocusHelper = new AudioFocusHelper(this,
                findViewById(R.id.audio_focus_button),
                findViewById(R.id.audio_focus_type));

        mActionButtonMap.clear();
        final List<Action> mediaActions = Action.createActions(this);
        for (final Action action : mediaActions) {
            final View button = findViewById(action.getId());
            button.setOnClickListener(view -> {
                if (mController != null) {
                    String id = mUriInput.getText().toString();
                    action.getMediaControllerAction().run(mController, id, null);
                }
            });
            mActionButtonMap.put(action.getId(), (ImageButton) button);
        }
    }

    @Nullable
    private String fetchMediaInfo() {
        if (mController == null) {
            Log.e(TAG, "Failed to update media info, null MediaController.");
            return null;
        }

        PlaybackStateCompat playbackState = mController.getPlaybackState();
        if (playbackState == null) {
            Log.e(TAG, "Failed to update media info, null PlaybackState.");
            return null;
        }

        Map<String, String> mediaInfos = new HashMap<>();
        mediaInfos.put(getString(R.string.info_state_string),
                playbackStateToName(playbackState.getState()));

        MediaMetadataCompat mediaMetadata = mController.getMetadata();
        if (mediaMetadata != null) {
            addMediaInfo(
                    mediaInfos,
                    getString(R.string.info_title_string),
                    mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE));
            addMediaInfo(
                    mediaInfos,
                    getString(R.string.info_artist_string),
                    mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST));
            addMediaInfo(
                    mediaInfos,
                    getString(R.string.info_album_string),
                    mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM));

            mMediaTitleView.setText(
                    mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE));
            mMediaArtistView.setText(
                    mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST));
            mMediaAlbumView.setText(
                    mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM));

            final Bitmap art = mediaMetadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
            if (art != null) {
                mMediaAlbumArtView.setImageBitmap(art);
            } else {
                mMediaAlbumArtView.setImageResource(R.drawable.ic_album_black_24dp);
            }
        } else {
            mMediaArtistView.setText(R.string.media_info_default);
            mMediaAlbumArtView.setImageResource(R.drawable.ic_album_black_24dp);
        }

        final long actions = playbackState.getActions();

        if ((actions & PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH) != 0) {
            addMediaInfo(mediaInfos, "ACTION_PREPARE_FROM_SEARCH", "Supported");
        }
        if ((actions & PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH) != 0) {
            addMediaInfo(mediaInfos, "ACTION_PLAY_FROM_SEARCH", "Supported");
        }

        if ((actions & PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID) != 0) {
            addMediaInfo(mediaInfos, "ACTION_PREPARE_FROM_MEDIA_ID", "Supported");
        }
        if ((actions & PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID) != 0) {
            addMediaInfo(mediaInfos, "ACTION_PLAY_FROM_MEDIA_ID", "Supported");
        }

        if ((actions & PlaybackStateCompat.ACTION_PREPARE_FROM_URI) != 0) {
            addMediaInfo(mediaInfos, "ACTION_PREPARE_FROM_URI", "Supported");
        }
        if ((actions & PlaybackStateCompat.ACTION_PLAY_FROM_URI) != 0) {
            addMediaInfo(mediaInfos, "ACTION_PLAY_FROM_URI", "Supported");
        }

        if ((actions & PlaybackStateCompat.ACTION_PREPARE) != 0) {
            addMediaInfo(mediaInfos, "ACTION_PREPARE", "Supported");
        }
        if ((actions & PlaybackStateCompat.ACTION_PLAY) != 0) {
            addMediaInfo(mediaInfos, "ACTION_PLAY", "Supported");
        }

        final StringBuilder stringBuilder = new StringBuilder();

        final List<String> sortedKeys = new ArrayList<>();
        sortedKeys.addAll(mediaInfos.keySet());
        Collections.sort(sortedKeys, new KeyComparator());

        for (final String key : sortedKeys) {
            stringBuilder.append(key).append(" = ").append(mediaInfos.get(key)).append('\n');
        }
        return stringBuilder.toString();
    }

    private String playbackStateToName(final int playbackState) {
        switch (playbackState) {
            case PlaybackStateCompat.STATE_NONE:
                return "STATE_NONE";
            case PlaybackStateCompat.STATE_STOPPED:
                return "STATE_STOPPED";
            case PlaybackStateCompat.STATE_PAUSED:
                return "STATE_PAUSED";
            case PlaybackStateCompat.STATE_PLAYING:
                return "STATE_PLAYING";
            case PlaybackStateCompat.STATE_FAST_FORWARDING:
                return "STATE_FAST_FORWARDING";
            case PlaybackStateCompat.STATE_REWINDING:
                return "STATE_REWINDING";
            case PlaybackStateCompat.STATE_BUFFERING:
                return "STATE_BUFFERING";
            case PlaybackStateCompat.STATE_ERROR:
                return "STATE_ERROR";
            case PlaybackStateCompat.STATE_CONNECTING:
                return "STATE_CONNECTING";
            case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS:
                return "STATE_SKIPPING_TO_PREVIOUS";
            case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT:
                return "STATE_SKIPPING_TO_NEXT";
            case PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM:
                return "STATE_SKIPPING_TO_QUEUE_ITEM";
            default:
                return "!Unknown State!";
        }
    }

    private void addMediaInfo(Map<String, String> mediaInfos, String key, String value) {
        if (!TextUtils.isEmpty(value)) {
            mediaInfos.put(key, value);
        }
    }

    private void startSessionActivity(MediaControllerCompat mediaController) {
        PendingIntent intent = mediaController.getSessionActivity();
        if (intent != null) {
            try {
                intent.send();
                return;
            } catch (PendingIntent.CanceledException e) {
                e.printStackTrace();
                Log.e(TAG, e.toString());
            }
        }
        Log.w(TAG, "Failed to open app by session activity.");
    }

    private class PreparePlayHandler implements View.OnClickListener {
        /*
         * Indexes of the values in the "input_options" string array.
         */
        private static final int INDEX_SEARCH = 0;
        private static final int INDEX_MEDIA_ID = 1;
        private static final int INDEX_URI = 2;
        private static final int INDEX_NO_PARAM = 3;

        /*
         * Indexes to the Actions returned by Action.createPreparePlayActions(Context).
         */
        private static final int ACTION_INDEX_SEARCH = INDEX_SEARCH * 2;
        private static final int ACTION_INDEX_MEDIA_ID = INDEX_MEDIA_ID * 2;
        private static final int ACTION_INDEX_URI = INDEX_URI * 2;
        private static final int ACTION_INDEX_NO_PARAM = INDEX_NO_PARAM * 2;

        private final List<Action> mPreparePlayActions;

        private PreparePlayHandler(final Context context) {
            mPreparePlayActions = Action.createPreparePlayActions(context);
        }

        @Override
        public void onClick(final View button) {
            final int prepareOrPlay = button.getId() == R.id.action_prepare ? 0 : 1;

            final Action action;
            switch (mInputTypeView.getSelectedItemPosition()) {
                case INDEX_NO_PARAM:
                    action = mPreparePlayActions.get(ACTION_INDEX_NO_PARAM + prepareOrPlay);
                    break;
                case INDEX_MEDIA_ID:
                    action = mPreparePlayActions.get(ACTION_INDEX_MEDIA_ID + prepareOrPlay);
                    break;
                case INDEX_SEARCH:
                    action = mPreparePlayActions.get(ACTION_INDEX_SEARCH + prepareOrPlay);
                    break;
                case INDEX_URI:
                    action = mPreparePlayActions.get(ACTION_INDEX_URI + prepareOrPlay);
                    break;
                default:
                    throw new IllegalStateException("Unknown input type: " +
                            mInputTypeView.getSelectedItemPosition());
            }

            if (mController != null) {
                final String data = mUriInput.getText().toString();
                action.getMediaControllerAction().run(mController, data, null);
            }
        }
    }

    private class MyConnectionCallback extends MediaBrowserCompat.ConnectionCallback {
        private final Context mContext;
        private final SparseArray<Long> mActionViewIdMap;
        private final ImageButton mShuffleToggle;

        MyConnectionCallback(@NonNull Context context) {
            mContext = context;

            mActionViewIdMap = new SparseArray<>();
            mActionViewIdMap.put(R.id.action_skip_previous,
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS);
            mActionViewIdMap.put(R.id.action_fast_rewind, PlaybackStateCompat.ACTION_REWIND);
            mActionViewIdMap.put(R.id.action_resume, PlaybackStateCompat.ACTION_PLAY);
            mActionViewIdMap.put(R.id.action_pause, PlaybackStateCompat.ACTION_PAUSE);
            mActionViewIdMap.put(R.id.action_stop, PlaybackStateCompat.ACTION_STOP);
            mActionViewIdMap.put(R.id.action_fast_forward, PlaybackStateCompat.ACTION_FAST_FORWARD);
            mActionViewIdMap.put(R.id.action_skip_next, PlaybackStateCompat.ACTION_SKIP_TO_NEXT);

            // They're the same action, but each of the buttons should be colored anyway.
            mActionViewIdMap.put(R.id.action_skip_30s_backward, PlaybackStateCompat.ACTION_SEEK_TO);
            mActionViewIdMap.put(R.id.action_skip_30s_forward, PlaybackStateCompat.ACTION_SEEK_TO);

            mActionViewIdMap.put(R.id.action_toggle_shuffle,
                    PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE);

            mShuffleToggle = findViewById(R.id.action_toggle_shuffle);
        }

        @Override
        public void onConnected() {
            try {
                mController = new MediaControllerCompat(
                        MediaAppControllerActivity.this,
                        mBrowser.getSessionToken());

                final MediaControllerCompat.Callback callback =
                        new MediaControllerCompat.Callback() {

                            @Override
                            public void onPlaybackStateChanged(PlaybackStateCompat playbackState) {
                                onUpdate();

                                if (playbackState != null) {
                                    showActions(playbackState.getActions());
                                }
                            }

                            @Override
                            public void onMetadataChanged(MediaMetadataCompat metadata) {
                                onUpdate();
                            }

                            private void onUpdate() {
                                String mediaInfoStr = fetchMediaInfo();
                                if (mediaInfoStr != null) {
                                    mMediaInfoText.setText(mediaInfoStr);
                                }
                            }
                        };
                mController.registerCallback(callback);

                // Force update on connect
                callback.onPlaybackStateChanged(mController.getPlaybackState());
                callback.onMetadataChanged(mController.getMetadata());

                Log.d(TAG, "MediaControllerCompat created");
            } catch (RemoteException remoteException) {
                Log.e(TAG, "Failed to connect with session token: " + remoteException);
                showDisconnected(R.string.media_controller_failed_msg);
            }
        }

        @Override
        public void onConnectionSuspended() {
            Log.d(TAG, "MediaBrowser connection suspended");
            showDisconnected(R.string.connection_suspended_msg);
        }

        @Override
        public void onConnectionFailed() {
            Log.e(TAG, "MediaBrowser connection failed");
            showDisconnected(R.string.connection_failed_msg);
        }

        private void showDisconnected(@StringRes final int stringResource) {
            final Snackbar snackbar =
                    Snackbar.make(mRootView, stringResource, Snackbar.LENGTH_INDEFINITE);
            snackbar.setAction(R.string.reconnect, view -> setupMediaController());
            snackbar.show();
        }

        /**
         * This updates the buttons on the controller view to show actions that
         * aren't included in the declared supported actions in red to more easily
         * detect potential bugs.
         *
         * @param actions The mask of currently supported actions from
         *                {@see PlaybackStateCompat.getActions()}.
         */
        private void showActions(@PlaybackStateCompat.Actions long actions) {
            final int count = mActionViewIdMap.size();
            for (int i = 0; i < count; ++i) {
                final int viewId = mActionViewIdMap.keyAt(i);
                final long action = mActionViewIdMap.valueAt(i);

                final ImageButton button = mActionButtonMap.get(viewId);
                if (actionSupported(actions, action)) {
                    button.setBackground(null);
                } else {
                    button.setBackgroundResource(R.drawable.bg_unsuported_action);
                }
            }

            final boolean shuffleEnabled =
                    mController.getShuffleMode() == PlaybackStateCompat.SHUFFLE_MODE_ALL ||
                            mController.getShuffleMode() == PlaybackStateCompat.SHUFFLE_MODE_GROUP;
            final int shuffleTint = shuffleEnabled
                    ? ContextCompat.getColor(mContext, R.color.colorPrimary)
                    : ContextCompat.getColor(mContext, R.color.colorInactive);
            DrawableCompat.setTint(mShuffleToggle.getDrawable(), shuffleTint);
        }

        private boolean actionSupported(@PlaybackStateCompat.Actions long actions,
                            @PlaybackStateCompat.Actions long checkAction) {
            return ((actions & checkAction) != 0);
        }
    }

    /**
     * Helper class to manage audio focus requests and the UI surrounding this feature.
     */
    private static class AudioFocusHelper
            implements View.OnClickListener,
            AudioManager.OnAudioFocusChangeListener,
            AdapterView.OnItemSelectedListener {

        /**
         * This list MUST match the order of the string-array
         * {@see R.array.audio_focus_types}.
         */
        private static final int[] FOCUS_TYPES = {
                AudioManager.AUDIOFOCUS_GAIN,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        };

        private final AudioManager mAudioManager;
        private final ToggleButton mToggleButton;
        private final Spinner mFocusTypeSpinner;

        private AudioFocusHelper(@NonNull Context context,
                                 @NonNull ToggleButton focusToggleButton,
                                 @NonNull Spinner focusTypeSpinner) {

            mAudioManager = (AudioManager) context.getSystemService(AUDIO_SERVICE);
            mToggleButton = focusToggleButton;
            mFocusTypeSpinner = focusTypeSpinner;

            mToggleButton.setOnClickListener(this);
            mFocusTypeSpinner.setOnItemSelectedListener(this);
        }

        @Override
        public void onClick(View v) {
            if (mToggleButton.isChecked()) {
                requestAudioFocus(getSelectedFocusType());
            } else {
                mAudioManager.abandonAudioFocus(this);
            }
        }

        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                    mToggleButton.setChecked(true);
                    break;
                default:
                    mToggleButton.setChecked(false);
            }
        }

        private int getSelectedFocusType() {
            return FOCUS_TYPES[mFocusTypeSpinner.getSelectedItemPosition()];
        }

        private void requestAudioFocus(final int hint) {
            mAudioManager.requestAudioFocus(this, AudioManager.STREAM_VOICE_CALL, hint);
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            // If we're holding audio focus and the type should change, automatically
            // request the new type of focus.
            if (mToggleButton.isChecked()) {
                requestAudioFocus(getSelectedFocusType());
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
            // Nothing to do.
        }
    }

    private static class KeyComparator implements Comparator<String> {
        private final Set<String> mCapKeys = new HashSet<>();

        @Override
        public int compare(String leftSide, String rightSide) {
            final boolean leftCaps = isAllCaps(leftSide);
            final boolean rightCaps = isAllCaps(rightSide);

            if (leftCaps && rightCaps) {
                return leftSide.compareTo(rightSide);
            } else if (leftCaps) {
                return 1;
            } else if (rightCaps) {
                return -1;
            }
            return leftSide.compareTo(rightSide);
        }

        private boolean isAllCaps(@NonNull final String stringToCheck) {
            if (mCapKeys.contains(stringToCheck)) {
                return true;
            } else if (stringToCheck.equals(stringToCheck.toUpperCase(Locale.US))) {
                mCapKeys.add(stringToCheck);
                return true;
            }
            return false;
        }
    }
}
