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

import static androidx.media.MediaBrowserServiceCompat.BrowserRoot.EXTRA_SUGGESTED;
import static java.util.Arrays.asList;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.util.Supplier;
import androidx.media.MediaBrowserServiceCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;

import com.example.android.mediacontroller.databinding.ActivityMediaAppControllerBinding;
import com.google.android.material.tabs.TabLayout;

import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

/**
 * This class connects to a {@link MediaBrowserServiceCompat}
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
    private static final String SEARCH_EXTRA = "com.example.android.mediacontroller.SEARCH";
    private static final String URI_EXTRA = "com.example.android.mediacontroller.URI";
    private static final String MEDIA_ID_EXTRA = "com.example.android.mediacontroller.MEDIA_ID";

    // Key name for Intent extras.
    private static final String APP_DETAILS_EXTRA =
            "com.example.android.mediacontroller.APP_DETAILS_EXTRA";
    private static final String DEFAULT_BROWSE_TREE_FILE_NAME = "_BrowseTreeContent.txt";

    // Index values for spinner.
    private static final int SEARCH_INDEX = 0;
    private static final int MEDIA_ID_INDEX = 1;
    private static final int URI_INDEX = 2;

    // Used for user storage permission request
    private static final int CREATE_DOCUMENT_REQUEST_FOR_SNAPSHOT = 1;

    private MediaAppDetails mMediaAppDetails;
    private MediaControllerCompat mController;
    private MediaBrowserCompat mBrowser;
    private MediaBrowserCompat mBrowserExtraSuggested;
    private AudioFocusHelper mAudioFocusHelper;
    private RatingUiHelper mRatingUiHelper;
    private final CustomControlsAdapter mCustomControlsAdapter = new CustomControlsAdapter();
    private BrowseMediaItemsAdapter mBrowseMediaItemsAdapter = new BrowseMediaItemsAdapter(
        new Supplier<MediaBrowserCompat>() {
            @Override
            public MediaBrowserCompat get() {
                return mBrowser;
            }
        });
    @Nullable
    private BrowseMediaItemsAdapter mBrowseMediaItemsExtraSuggestedAdapter = new BrowseMediaItemsAdapter(
        new Supplier<MediaBrowserCompat>() {
            @Override
            public MediaBrowserCompat get() {
                return mBrowserExtraSuggested;
            }
        });
    private final SearchMediaItemsAdapter mSearchMediaItemsAdapter = new SearchMediaItemsAdapter(
        new Supplier<MediaBrowserCompat>() {
            @Override
            public MediaBrowserCompat get() {
                return mBrowser;
            }
        });

    private ModeHelper mShuffleToggle;
    private ModeHelper mRepeatToggle;

    private ViewGroup mRatingViewGroup;

    private MediaBrowseTreeSnapshot mMediaBrowseTreeSnapshot;

    private final SparseArray<ImageButton> mActionButtonMap = new SparseArray<>();
    private ActivityMediaAppControllerBinding binding;

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
        binding = ActivityMediaAppControllerBinding.inflate(LayoutInflater.from(this));
        setContentView(binding.getRoot());
        final Toolbar toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        mShuffleToggle = new ShuffleModeHelper();
        mRepeatToggle = new RepeatModeHelper();

        mRatingViewGroup = findViewById(R.id.rating);

        if (savedInstanceState != null) {
            mMediaAppDetails = savedInstanceState.getParcelable(STATE_APP_DETAILS_KEY);
            binding.preparePlayPage.uriIdQuery.setText(savedInstanceState.getString(STATE_URI_KEY));
        }

        mMediaAppDetails = handleIntent(getIntent());
        setupButtons();

        if (mMediaAppDetails != null) {
            setupMedia();
            setupToolbar(mMediaAppDetails.appName, mMediaAppDetails.icon);
        } else {
            // App details weren't passed in for some reason.
            Toast.makeText(
                    this,
                    getString(R.string.media_app_details_update_failed),
                    Toast.LENGTH_SHORT
            ).show();

            // Go back to the launcher ASAP.
            startActivity(new Intent(this, LaunchActivity.class));
            finish();
            return;
        }

        final int[] pages = {
                R.id.prepare_play_page,
                R.id.controls_page,
                R.id.custom_controls_page,
                R.id.browse_tree_page,
                R.id.media_search_page,
        };
        // Simplify the adapter by not keeping track of creating/destroying off-screen views.
        binding.viewPager.setOffscreenPageLimit(pages.length);
        binding.viewPager.setAdapter(new PagerAdapter() {

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
        pageIndicator.setupWithViewPager(binding.viewPager);

        final RecyclerView customControlsList = findViewById(R.id.custom_controls_list);
        customControlsList.setLayoutManager(new LinearLayoutManager(this));
        customControlsList.setHasFixedSize(true);
        customControlsList.setAdapter(mCustomControlsAdapter);

        final RecyclerView browseTreeList = findViewById(R.id.media_items_list);
        browseTreeList.setLayoutManager(new LinearLayoutManager(this));
        browseTreeList.setHasFixedSize(true);
        browseTreeList.setAdapter(mBrowseMediaItemsAdapter);
        mBrowseMediaItemsAdapter.init(findViewById(R.id.media_browse_tree_top),
                findViewById(R.id.media_browse_tree_up), findViewById(R.id.media_browse_tree_save));

        final RecyclerView browseTreeListExtraSuggested = findViewById(R.id.media_items_list_extra_suggested);
        browseTreeListExtraSuggested.setLayoutManager(new LinearLayoutManager(this));
        browseTreeListExtraSuggested.setHasFixedSize(true);
        browseTreeListExtraSuggested.setAdapter(mBrowseMediaItemsExtraSuggestedAdapter);
        mBrowseMediaItemsExtraSuggestedAdapter.init(findViewById(R.id.media_browse_tree_top_extra_suggested),
                findViewById(R.id.media_browse_tree_up_extra_suggested), findViewById(R.id.media_browse_tree_save));

        final RecyclerView searchItemsList = findViewById(R.id.search_items_list);
        searchItemsList.setLayoutManager(new LinearLayoutManager(this));
        searchItemsList.setHasFixedSize(true);
        searchItemsList.setAdapter(mSearchMediaItemsAdapter);
        mSearchMediaItemsAdapter.init(null, null, null);

        findViewById(R.id.search_button).setOnClickListener(v -> {
            CharSequence queryText = ((TextView) findViewById(R.id.search_query)).getText();
            if (!TextUtils.isEmpty(queryText)) {
                mSearchMediaItemsAdapter.setRoot(queryText.toString());
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CREATE_DOCUMENT_REQUEST_FOR_SNAPSHOT) {
            if (resultCode == RESULT_OK && mMediaBrowseTreeSnapshot != null) {
                Uri uri = data.getData();
                OutputStream outputStream = null;
                try {
                    outputStream = getContentResolver().openOutputStream(uri);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                mMediaBrowseTreeSnapshot.takeBrowserSnapshot(outputStream);
                Toast.makeText(this, "Output file location: " + uri.getPath(), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "File could not be saved.", Toast.LENGTH_SHORT).show();
            }


        }
    }

    @Override
    protected void onDestroy() {
        if (mController != null) {
            mController.unregisterCallback(mCallback);
            mController = null;
        }

        if (mBrowser != null && mBrowser.isConnected()) {
            mBrowser.disconnect();
        }
        mBrowser = null;

        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        handleIntent(intent);
    }

    /**
     * This is the single point where the MediaBrowser and MediaController are setup.
     */
    private MediaAppDetails handleIntent(Intent intent) {
        if (intent == null) {
            return null;
        }

        final Bundle extras = intent.getExtras();
        if (extras != null) {
            // Pull data out of the extras, if they're there.
            if (extras.containsKey(SEARCH_EXTRA)) {
                binding.preparePlayPage.inputType.setSelection(SEARCH_INDEX);
                binding.preparePlayPage.uriIdQuery.setText(extras.getString(SEARCH_EXTRA));
            } else if (extras.containsKey(MEDIA_ID_EXTRA)) {
                binding.preparePlayPage.inputType.setSelection(MEDIA_ID_INDEX);
                binding.preparePlayPage.uriIdQuery.setText(extras.getString(MEDIA_ID_EXTRA));
            } else if (extras.containsKey(URI_EXTRA)) {
                binding.preparePlayPage.inputType.setSelection(URI_INDEX);
                binding.preparePlayPage.uriIdQuery.setText(extras.getString(URI_EXTRA));
            }

            // It's also possible we're here from LaunchActivity, which did all this work for us.
            if (extras.containsKey(APP_DETAILS_EXTRA)) {
                return extras.getParcelable(APP_DETAILS_EXTRA);
            }
        }

        // If we get here and don't have app details, something went wrong.
        if (mMediaAppDetails == null) {
            Toast.makeText(
                    this,
                    getString(R.string.media_app_details_update_failed),
                    Toast.LENGTH_SHORT
            ).show();
        }

        return null;
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);

        out.putParcelable(STATE_APP_DETAILS_KEY, mMediaAppDetails);
        out.putString(STATE_URI_KEY, binding.preparePlayPage.uriIdQuery.getText().toString());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        mMediaAppDetails = savedInstanceState.getParcelable(STATE_APP_DETAILS_KEY);
        binding.preparePlayPage.uriIdQuery.setText(savedInstanceState.getString(STATE_URI_KEY));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.controller, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item != null && item.getItemId() == R.id.start_session) {
            if (mController != null) {
                startSessionActivity(mController);
            } else {
                Toast.makeText(this, R.string.no_session, Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return false;
    }

    private void setupToolbar(String name, Bitmap icon) {
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            final Bitmap toolbarIcon = BitmapUtils.createToolbarIcon(getResources(), icon);
            actionBar.setIcon(new BitmapDrawable(getResources(), toolbarIcon));
            actionBar.setTitle(name);
        }
    }

    private void setupMedia() {
        // Should now have a viable details.. connect to browser and service as needed.
        if (mMediaAppDetails.componentName != null) {
            mBrowser = new MediaBrowserCompat(this, mMediaAppDetails.componentName,
                    new MediaBrowserCompat.ConnectionCallback() {
                        @Override
                        public void onConnected() {
                            setupMediaController();
                            mBrowseMediaItemsAdapter.setRoot(mBrowser.getRoot());
                        }

                        @Override
                        public void onConnectionSuspended() {
                            //TODO(rasekh): shut down browser.
                            mBrowseMediaItemsAdapter.setRoot(null);
                        }

                        @Override
                        public void onConnectionFailed() {
                            showToastAndFinish(getString(
                                    R.string.connection_failed_msg, mMediaAppDetails.appName));
                        }

                    }, null);
            mBrowser.connect();

            Bundle bundle = new Bundle();
            bundle.putBoolean(EXTRA_SUGGESTED, true);

            mBrowserExtraSuggested = new MediaBrowserCompat(this, mMediaAppDetails.componentName,
                    new MediaBrowserCompat.ConnectionCallback() {
                        @Override
                        public void onConnected() {
                            mBrowseMediaItemsExtraSuggestedAdapter.setRoot(mBrowserExtraSuggested.getRoot());
                        }

                        @Override
                        public void onConnectionSuspended() {
                            mBrowseMediaItemsExtraSuggestedAdapter.setRoot(null);
                        }

                        @Override
                        public void onConnectionFailed() {
                            showToastAndFinish(getString(
                                    R.string.connection_failed_msg, mMediaAppDetails.appName));
                        }

                    }, bundle);
            mBrowserExtraSuggested.connect();
        } else if (mMediaAppDetails.sessionToken != null) {
            setupMediaController();
        } else {
            showToastAndFinish(getString(R.string.connection_failed_msg, mMediaAppDetails.appName));
        }
    }

    private void setupMediaController() {
        try {
            MediaSessionCompat.Token token = mMediaAppDetails.sessionToken;
            if (token == null) {
                token = mBrowser.getSessionToken();
            }
            mController = new MediaControllerCompat(this, token);
            mController.registerCallback(mCallback);
            mRatingUiHelper = ratingUiHelperFor(mController.getRatingType());

            // Force update on connect.
            mCallback.onPlaybackStateChanged(mController.getPlaybackState());
            mCallback.onMetadataChanged(mController.getMetadata());

            // Ensure views are visible.
            binding.viewPager.setVisibility(View.VISIBLE);
        } catch (RemoteException remoteException) {
            Log.e(TAG, "Failed to create MediaController from session token", remoteException);
            showToastAndFinish(getString(R.string.media_controller_failed_msg));
        }
    }

    private void setupButtons() {
        final PreparePlayHandler preparePlayHandler = new PreparePlayHandler(this);
        findViewById(R.id.action_prepare).setOnClickListener(preparePlayHandler);
        findViewById(R.id.action_play).setOnClickListener(preparePlayHandler);

        mAudioFocusHelper = new AudioFocusHelper(this,
                findViewById(R.id.audio_focus_button),
                findViewById(R.id.audio_focus_type));

        mActionButtonMap.clear();
        final List<Action> mediaActions = Action.createActions(this);
        for (final Action action : mediaActions) {
            final View button = findViewById(action.getId());
            button.setOnClickListener(view -> {
                if (mController != null) {
                    String id = binding.preparePlayPage.uriIdQuery.getText().toString();
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

            binding.controlsPage.mediaTitle.setText(
                    mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE));
            binding.controlsPage.mediaArtist.setText(
                    mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST));
            binding.controlsPage.mediaAlbum.setText(
                    mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM));

            final Bitmap art = mediaMetadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
            if (art != null) {
                binding.controlsPage.mediaArt.setImageBitmap(art);
            } else {
                binding.controlsPage.mediaArt.setImageResource(R.drawable.ic_album_black_24dp);
            }
            // Prefer user rating, but fall back to global rating if available.
            RatingCompat rating =
                    mediaMetadata.getRating(MediaMetadataCompat.METADATA_KEY_USER_RATING);
            if (rating == null) {
                rating = mediaMetadata.getRating(MediaMetadataCompat.METADATA_KEY_RATING);
            }
            mRatingUiHelper.setRating(rating);
        } else {
            binding.controlsPage.mediaArtist.setText(R.string.media_info_default);
            binding.controlsPage.mediaArt.setImageResource(R.drawable.ic_album_black_24dp);
            mRatingUiHelper.setRating(null);
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

        final List<String> sortedKeys = new ArrayList<>(mediaInfos.keySet());
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

    private RatingUiHelper ratingUiHelperFor(int ratingStyle) {
        switch (ratingStyle) {
            case RatingCompat.RATING_3_STARS:
                return new RatingUiHelper.Stars3(mRatingViewGroup, mController);
            case RatingCompat.RATING_4_STARS:
                return new RatingUiHelper.Stars4(mRatingViewGroup, mController);
            case RatingCompat.RATING_5_STARS:
                return new RatingUiHelper.Stars5(mRatingViewGroup, mController);
            case RatingCompat.RATING_HEART:
                return new RatingUiHelper.Heart(mRatingViewGroup, mController);
            case RatingCompat.RATING_THUMB_UP_DOWN:
                return new RatingUiHelper.Thumbs(mRatingViewGroup, mController);
            case RatingCompat.RATING_PERCENTAGE:
                return new RatingUiHelper.Percentage(mRatingViewGroup, mController);
            case RatingCompat.RATING_NONE:
            default:
                return new RatingUiHelper.None(mRatingViewGroup, mController);
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
                Log.e(TAG, "Failed to start session activity", e);
            }
        }
        Toast.makeText(this, R.string.session_start_failed, Toast.LENGTH_SHORT).show();
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
            switch (binding.preparePlayPage.inputType.getSelectedItemPosition()) {
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
                            binding.preparePlayPage.inputType.getSelectedItemPosition());
            }

            if (mController != null) {
                final String data = binding.preparePlayPage.uriIdQuery.getText().toString();
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

    private final MediaControllerCompat.Callback mCallback = new MediaControllerCompat.Callback() {
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

        @Override
        public void onSessionDestroyed() {
            showToastAndFinish("MediaSession has been released");
        }

        private void onUpdate() {
            String mediaInfoStr = fetchMediaInfo();
            if (mediaInfoStr != null) {
                binding.preparePlayPage.mediaInfo.setText(mediaInfoStr);
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
                button.setBackgroundResource(R.drawable.bg_unsupported_action);
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

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(
                    LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.media_custom_control, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
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
                    return actions.size() == mActions.size() &&
                            actions.get(oldItemPosition).getAction()
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
                container.setBackgroundResource(R.drawable.bg_unsupported_action);
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
            if (mController != null) {
                mController.getTransportControls().setShuffleMode(mode);
            }
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
            if (mController != null) {
                mController.getTransportControls().setRepeatMode(mode);
            }
        }
    }

    /**
     * Helper class which manages a MediaBrowser tree. Handles modifying the adapter when selecting
     * an item would cause the browse tree to change or play a media item. Only subscribes to a
     * single level at once.
     * <p>
     * The class keeps track of two pieces of data. (1) The Items to be displayed in mItems and
     * (2) the stack of mNodes from the root to the current node. Depending on the current state
     * different values are displayed in the adapter.
     * (a) mItems == null and mNodes.size() == 0 -> No Browser.
     * (b) mItems == null and mNodes.size() > 0 -> Loading.
     * (c) mItems != null && mItems.size() == 0 -> Empty.
     * (d) mItems.
     */
    private class BrowseMediaItemsAdapter extends
            RecyclerView.Adapter<BrowseMediaItemsAdapter.ViewHolder> {

        private final Supplier<MediaBrowserCompat> mBrowserSupplier;
        private List<MediaBrowserCompat.MediaItem> mItems;
        private final Stack<String> mNodes = new Stack<>();

        public BrowseMediaItemsAdapter(Supplier<MediaBrowserCompat> browserSupplier) {
            mBrowserSupplier = browserSupplier;
        }

        MediaBrowserCompat.SubscriptionCallback callback =
                new MediaBrowserCompat.SubscriptionCallback() {
                    @Override
                    public void onChildrenLoaded(@NonNull String parentId,
                                                 @NonNull List<MediaItem> children) {
                        updateItemsEmptyIfNull(children);
                    }
                };

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(
                    LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.media_browse_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (mNodes.size() == 0) {
                holder.name.setText(getString(R.string.media_no_browser));
                holder.name.setVisibility(View.VISIBLE);
                holder.description.setVisibility(View.GONE);
                holder.icon.setVisibility(View.GONE);
                holder.itemView.setOnClickListener((v) -> {
                });
                return;
            }
            if (mItems == null) {
                holder.name.setText(getString(R.string.media_browse_tree_loading));
                holder.name.setVisibility(View.VISIBLE);
                holder.description.setVisibility(View.GONE);
                holder.icon.setVisibility(View.GONE);
                holder.itemView.setOnClickListener((v) -> {
                });
                return;
            }
            if (mItems.size() == 0) {
                holder.name.setText(getString(R.string.media_browse_tree_empty));
                holder.name.setVisibility(View.VISIBLE);
                holder.description.setVisibility(View.GONE);
                holder.icon.setVisibility(View.GONE);
                holder.itemView.setOnClickListener((v) -> {
                });
                return;
            }

            final MediaBrowserCompat.MediaItem item = mItems.get(position);
            holder.name.setText(item.getDescription().getTitle());
            holder.name.setVisibility(View.VISIBLE);
            holder.description.setText(item.getDescription().getSubtitle());
            holder.description.setVisibility(View.VISIBLE);
            Uri iconUri = item.getDescription().getIconUri();
            Bitmap iconBitmap = item.getDescription().getIconBitmap();
            if (iconBitmap != null) {
                holder.icon.setImageBitmap(iconBitmap);
                holder.icon.setVisibility(View.VISIBLE);
            } else if (iconUri != null) {
                holder.icon.setImageURI(iconUri);
                holder.icon.setVisibility(View.VISIBLE);
            } else {
                holder.icon.setVisibility(View.GONE);
            }
            holder.itemView.setOnClickListener(
                    (v) -> {
                        if (item.isBrowsable()) {
                            unsubscribe();
                            mNodes.push(item.getMediaId());
                            subscribe();
                        }
                        if (item.isPlayable() && mController != null) {
                            mController.getTransportControls().playFromMediaId(item.getMediaId(),
                                    null);
                        }
                    });
        }

        @Override
        public int getItemCount() {
            if (mNodes.size() == 0 || mItems == null || mItems.size() == 0) {
                return 1;
            }
            return mItems.size();
        }

        void updateItemsEmptyIfNull(List<MediaBrowserCompat.MediaItem> items) {
            if (items == null) {
                updateItems(Collections.emptyList());
            } else {
                updateItems(items);
            }
        }

        void updateItems(List<MediaBrowserCompat.MediaItem> items) {
            mItems = items;
            notifyDataSetChanged();
        }

        /**
         * Assigns click handlers to the buttons if provided for moving to the top of the tree or
         * for moving up one level in the tree.
         */
        void init(View topButtonView, View upButtonView, View saveButtonView) {
            if (topButtonView != null) {
                topButtonView.setOnClickListener(v -> {
                    if (mNodes.size() > 1) {
                        unsubscribe();
                        while (mNodes.size() > 1) {
                            mNodes.pop();
                        }
                        subscribe();
                    }
                });
            }

            if (upButtonView != null) {
                upButtonView.setOnClickListener(v -> {
                    if (mNodes.size() > 1) {
                        unsubscribe();
                        mNodes.pop();
                        subscribe();
                    }
                });
            }
            if (saveButtonView != null) {
                saveButtonView.setOnClickListener(v -> {
                    takeMediaBrowseTreeSnapshot();
                });
            }

        }

        private void takeMediaBrowseTreeSnapshot(){
            if(mBrowser != null) {
                if(mMediaBrowseTreeSnapshot == null) {
                    mMediaBrowseTreeSnapshot = new MediaBrowseTreeSnapshot(
                            MediaAppControllerActivity.this, mBrowser);
                }
                Intent saveTextFileIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                saveTextFileIntent.addCategory(Intent.CATEGORY_OPENABLE);
                saveTextFileIntent.setType("text/plain");
                saveTextFileIntent.putExtra(
                        Intent.EXTRA_TITLE, DEFAULT_BROWSE_TREE_FILE_NAME);
                MediaAppControllerActivity.this.startActivityForResult(saveTextFileIntent,
                        CREATE_DOCUMENT_REQUEST_FOR_SNAPSHOT);

            }else{
                Log.e(TAG, "Media browser is null");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(),"No media browser to snapshot",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

        protected void subscribe() {
            if (mNodes.size() > 0) {
                mBrowserSupplier.get().subscribe(mNodes.peek(), callback);
            }
        }

        protected void unsubscribe() {
            if (mNodes.size() > 0) {
                mBrowserSupplier.get().unsubscribe(mNodes.peek(), callback);
            }
            updateItems(null);
        }

        int treeDepth() {
            return mNodes.size();
        }

        String getCurrentNode() {
            return mNodes.peek();
        }

        void setRoot(String root) {
            unsubscribe();
            mNodes.clear();
            if (root != null) {
                mNodes.push(root);
                subscribe();
            }
        }

        class ViewHolder extends RecyclerView.ViewHolder {

            private final TextView name;
            private final TextView description;
            private final ImageView icon;

            ViewHolder(View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.item_name);
                description = itemView.findViewById(R.id.item_description);
                icon = itemView.findViewById(R.id.item_icon);
            }
        }
    }

    /**
     * Helper class which gets the search tree and presents it as a browse tree. Overrides
     * subscription function to perform search at the root node.
     */
    private class SearchMediaItemsAdapter extends BrowseMediaItemsAdapter {

        public SearchMediaItemsAdapter(Supplier<MediaBrowserCompat> browserSupplier) {
            super(browserSupplier);
        }

        @Override
        protected void subscribe() {
            if (treeDepth() == 1) {
                mBrowser.search(getCurrentNode(), null,
                        new MediaBrowserCompat.SearchCallback() {
                            @Override
                            public void onSearchResult(@NonNull String query, Bundle extras,
                                                       @NonNull List<MediaBrowserCompat.MediaItem> items) {
                                if (query.equals(getCurrentNode())) {
                                    updateItemsEmptyIfNull(items);
                                }
                            }

                            @Override
                            public void onError(@NonNull String query, Bundle extras) {
                                super.onError(query, extras);
                            }
                        });
            } else {
                super.subscribe();
            }
        }

        @Override()
        protected void unsubscribe() {
            if (treeDepth() == 1) {
                return;
            }
            super.unsubscribe();
        }
    }
}
