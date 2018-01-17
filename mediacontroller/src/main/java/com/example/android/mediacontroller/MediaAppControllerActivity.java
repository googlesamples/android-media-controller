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
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.TabLayout;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
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

import static java.util.Arrays.asList;

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
    private AudioFocusHelper mAudioFocusHelper;
    private CustomControlsAdapter mCustomControlsAdapter = new CustomControlsAdapter();

    private Spinner mInputTypeView;
    private EditText mUriInput;
    private TextView mMediaInfoText;

    private ImageView mMediaAlbumArtView;
    private TextView mMediaTitleView;
    private TextView mMediaArtistView;
    private TextView mMediaAlbumView;

    private ModeHelper mShuffleToggle;
    private ModeHelper mRepeatToggle;

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

        mInputTypeView = findViewById(R.id.input_type);
        mUriInput = findViewById(R.id.uri_id_query);
        mMediaInfoText = findViewById(R.id.media_info);

        mMediaAlbumArtView = findViewById(R.id.media_art);
        mMediaTitleView = findViewById(R.id.media_title);
        mMediaArtistView = findViewById(R.id.media_artist);
        mMediaAlbumView = findViewById(R.id.media_album);

        mShuffleToggle = new ShuffleModeHelper();
        mRepeatToggle = new RepeatModeHelper();

        if (savedInstanceState != null) {
            mMediaAppDetails = savedInstanceState.getParcelable(STATE_APP_DETAILS_KEY);
            mUriInput.setText(savedInstanceState.getString(STATE_URI_KEY));
        }

        handleIntent(getIntent());
        setupButtons();

        if (mMediaAppDetails != null) {
            // mMediaAppDetails == null means that we received just a package name in the starting
            // intent, (e.g. triggered through the URL scheme). If that happens, we need to wait
            // for a media browser connection before we can set up the controller or toolbar, and
            // that will be taken care of by #connectToMediaBrowserPackage in handleIntent.
            setupMediaController();
            setupToolbar(mMediaAppDetails.appName, mMediaAppDetails.icon);
        }

        final ViewPager viewPager = findViewById(R.id.view_pager);
        final int[] pages = {
                R.id.prepare_play_page,
                R.id.controls_page,
                R.id.custom_controls_page,
        };
        // Simplify the adapter by not keeping track of creating/destroying off-screen views.
        viewPager.setOffscreenPageLimit(pages.length);
        viewPager.setAdapter(new PagerAdapter() {

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

        final RecyclerView customControlsList = findViewById(R.id.custom_controls_list);
        customControlsList.setLayoutManager(new LinearLayoutManager(this));
        customControlsList.setHasFixedSize(true);
        customControlsList.setAdapter(mCustomControlsAdapter);
    }

    @Override
    protected void onDestroy() {
        if (mController != null) {
            mController.unregisterCallback(mCallback);
            mController = null;
        }
        super.onDestroy();
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
                connectToMediaBrowserPackage(appPackageName);
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

    private void setupToolbar(String name, Bitmap icon) {
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            final Bitmap toolbarIcon = BitmapUtils.createToolbarIcon(getResources(), icon);
            actionBar.setIcon(new BitmapDrawable(getResources(), toolbarIcon));
            actionBar.setTitle(name);
        }
    }

    private void setupMediaController() {
        try {
            mController = new MediaControllerCompat(this, mMediaAppDetails.sessionToken);
            mController.registerCallback(mCallback);

            // Force update on connect.
            mCallback.onPlaybackStateChanged(mController.getPlaybackState());
            mCallback.onMetadataChanged(mController.getMetadata());

            Log.d(TAG, "MediaControllerCompat created");
        } catch (RemoteException remoteException) {
            Log.e(TAG, "Failed to create MediaController from session token", remoteException);
            showToastAndFinish(getString(R.string.media_controller_failed_msg));
        }
    }

    private void connectToMediaBrowserPackage(final String packageName) {
        final PackageManager packageManager = getPackageManager();

        final Intent mediaBrowserIntent = new Intent(MediaBrowserServiceCompat.SERVICE_INTERFACE);
        final List<ResolveInfo> services =
                packageManager.queryIntentServices(mediaBrowserIntent,
                        PackageManager.GET_RESOLVED_FILTER);
        for (ResolveInfo info : services) {
            if (info.serviceInfo.packageName.equals(packageName)) {
                final Bitmap icon = BitmapUtils.convertDrawable(
                        getResources(), info.loadIcon(packageManager));
                final String name = info.loadLabel(packageManager).toString();

                setupToolbar(name, icon);
                MediaAppEntry appEntry = MediaAppEntry.fromBrowseService(
                        info.serviceInfo, packageManager);
                appEntry.getSessionToken(this, new MediaAppEntry.SessionTokenAvailableCallback() {
                    @Override
                    public void onSuccess(MediaSessionCompat.Token sessionToken) {
                        mMediaAppDetails = new MediaAppDetails(name, icon, sessionToken);
                        setupMediaController();
                    }

                    @Override
                    public void onFailure() {
                        showToastAndFinish(getString(R.string.connection_failed_msg, packageName));
                    }
                });
                return;
            }
        }
        // Failed to find package.
        showToastAndFinish(getString(R.string.no_app_for_package, packageName));
    }

    private void setupButtons() {

        final PreparePlayHandler preparePlayHandler = new PreparePlayHandler(this);
        findViewById(R.id.action_prepare).setOnClickListener(preparePlayHandler);
        findViewById(R.id.action_play).setOnClickListener(preparePlayHandler);

        findViewById(R.id.start_session_activity_button).setOnClickListener(v -> {
            if (mController != null) {
                startSessionActivity(mController);
            } else {
                Log.w(TAG, "Media session does not contain an Activity to start");
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

    private final SparseArray<Long> mActionViewIdMap = new SparseArray<>();

    {
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
    }

    final MediaControllerCompat.Callback mCallback = new MediaControllerCompat.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat playbackState) {
            onUpdate();
            if (playbackState != null) {
                showActions(playbackState.getActions());
                mCustomControlsAdapter.setActions(mController, playbackState.getCustomActions());
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

    private void showToastAndFinish(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        finish();
    }

    /**
     * This updates the buttons on the controller view to show actions that
     * aren't included in the declared supported actions in red to more easily
     * detect potential bugs.
     *
     * @param actions The mask of currently supported actions from {@see
     *                PlaybackStateCompat.getActions()}.
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

        mShuffleToggle.updateView(
                actionSupported(actions, PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE),
                mController.getShuffleMode()
        );
        mRepeatToggle.updateView(
                actionSupported(actions, PlaybackStateCompat.ACTION_SET_REPEAT_MODE),
                mController.getRepeatMode()
        );
    }

    private boolean actionSupported(@PlaybackStateCompat.Actions long actions,
                                    @PlaybackStateCompat.Actions long checkAction) {
        return ((actions & checkAction) != 0);
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

    private class CustomControlsAdapter extends
            RecyclerView.Adapter<CustomControlsAdapter.ViewHolder> {
        private List<PlaybackStateCompat.CustomAction> mActions = Collections.emptyList();
        private MediaControllerCompat.TransportControls mControls;
        private Resources mMediaAppResources;

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(
                    LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.media_custom_control, parent, false));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            PlaybackStateCompat.CustomAction action = mActions.get(position);
            holder.name.setText(action.getName());
            holder.description.setText(action.getAction());
            if (mMediaAppResources != null) {
                Drawable iconDrawable = ResourcesCompat.getDrawable(
                        mMediaAppResources, action.getIcon(), /* theme = */ null);
                holder.icon.setImageDrawable(iconDrawable);
            }
            holder.itemView.setOnClickListener(
                    (v) -> mControls.sendCustomAction(action, new Bundle()));
        }

        @Override
        public int getItemCount() {
            return mActions.size();
        }

        void setActions(MediaControllerCompat controller,
                        List<PlaybackStateCompat.CustomAction> actions) {
            mControls = controller.getTransportControls();
            try {
                mMediaAppResources = getPackageManager()
                        .getResourcesForApplication(controller.getPackageName());
            } catch (PackageManager.NameNotFoundException e) {
                // Shouldn't happen, because the controller must come from an installed app.
                Log.e(TAG, "Failed to fetch resources from media app", e);
            }
            DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override
                public int getOldListSize() {
                    return mActions.size();
                }

                @Override
                public int getNewListSize() {
                    return actions.size();
                }

                @Override
                public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                    return actions.get(oldItemPosition).getAction()
                            .equals(mActions.get(newItemPosition).getAction());
                }

                @Override
                public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                    return actions.get(oldItemPosition).equals(mActions.get(newItemPosition));
                }
            });
            mActions = actions;
            diffResult.dispatchUpdatesTo(this);
        }

        class ViewHolder extends RecyclerView.ViewHolder {

            private final TextView name;
            private final TextView description;
            private final ImageView icon;

            ViewHolder(View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.action_name);
                description = itemView.findViewById(R.id.action_description);
                icon = itemView.findViewById(R.id.action_icon);
            }
        }
    }

    /**
     * Helper class to manage shuffle and repeat "modes"
     */
    private static abstract class ModeHelper implements AdapterView.OnItemSelectedListener {
        private final Context context;
        private final Spinner spinner;
        private final ImageView icon;
        private final ViewGroup container;
        private final List<Integer> modes;

        ModeHelper(ViewGroup container,
                   @IdRes int stateSpinnerView,
                   @IdRes int iconImageView,
                   List<Integer> modes) {
            this.context = container.getContext();
            this.spinner = container.findViewById(stateSpinnerView);
            this.icon = container.findViewById(iconImageView);
            this.container = container;
            this.modes = modes;
            this.spinner.setOnItemSelectedListener(this);
        }

        protected abstract boolean enabled(int mode);

        protected abstract void setMode(int mode);

        void updateView(boolean supported, int mode) {
            if (supported) {
                container.setBackground(null);
                spinner.setVisibility(View.VISIBLE);
                spinner.setSelection(modes.indexOf(mode));
            } else {
                container.setBackgroundResource(R.drawable.bg_unsuported_action);
                spinner.setVisibility(View.GONE);
            }
            final int tint = enabled(mode) ? R.color.colorPrimary : R.color.colorInactive;
            DrawableCompat.setTint(icon.getDrawable(), ContextCompat.getColor(context, tint));
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            setMode(this.modes.get(position));
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }

    private class ShuffleModeHelper extends ModeHelper {
        ShuffleModeHelper() {
            super(findViewById(R.id.group_toggle_shuffle),
                    R.id.shuffle_mode,
                    R.id.shuffle_mode_icon,
                    asList(
                            PlaybackStateCompat.SHUFFLE_MODE_NONE,
                            PlaybackStateCompat.SHUFFLE_MODE_GROUP,
                            PlaybackStateCompat.SHUFFLE_MODE_ALL));
        }

        @Override
        protected boolean enabled(int mode) {
            return mode == PlaybackStateCompat.SHUFFLE_MODE_ALL ||
                    mode == PlaybackStateCompat.SHUFFLE_MODE_GROUP;
        }

        @Override
        protected void setMode(int mode) {
            mController.getTransportControls().setShuffleMode(mode);
        }
    }

    private class RepeatModeHelper extends ModeHelper {
        RepeatModeHelper() {
            super(findViewById(R.id.group_toggle_repeat),
                    R.id.repeat_mode,
                    R.id.repeat_mode_icon,
                    asList(
                            PlaybackStateCompat.REPEAT_MODE_NONE,
                            PlaybackStateCompat.REPEAT_MODE_ONE,
                            PlaybackStateCompat.REPEAT_MODE_GROUP,
                            PlaybackStateCompat.REPEAT_MODE_ALL)
            );
        }

        @Override
        protected boolean enabled(int mode) {
            return mode == PlaybackStateCompat.REPEAT_MODE_ONE ||
                    mode == PlaybackStateCompat.REPEAT_MODE_GROUP ||
                    mode == PlaybackStateCompat.REPEAT_MODE_ALL;
        }

        @Override
        protected void setMode(int mode) {
            mController.getTransportControls().setRepeatMode(mode);
        }
    }
}
