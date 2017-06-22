Media Controller Test
=====================
Create a simple MediaController that connects to a MediaBrowserService
in order to test inter-app media controls.

This app works with the Universal Android Music Player sample,
or any other app that implements the media APIs.
https://github.com/googlesamples/android-UniversalMusicPlayer


Usage
=====

1. Select an app from the list of those presented.
   * Only apps that register a service with an intent filter action of
   "android.media.browse.MediaBrowserService" will be shown.
2. Select the type of action to perform to start the player. Options are:
   * Search: Sends the text provided as a search via _prepareFromSearch()_ or
   _playFromSearch()_.
   * Media ID: Sends the text provided as a media ID via _prepareFromMediaId()_ or
   _playFromMediaId()_.
   * URI: Sends the text provided as a URI via _prepareFromUri()_ or
   _playFromUri()_.
   * No Input: Calls the methods _prepare()_ or _play()_ directly.
3. Text below the ```PREPARE``` and ```PLAY``` buttons updates based on changes to
   the media player state via _onPlaybackStateChanged_ and _onMetadataChanged_ and
   includes the current player state reported via _PlaybackStateCompat.getState()_.
4. Swipe to the left to see typical media controls with the media's art as a
   background, if provided.
5. Press ```back``` to return to the list of media apps.


Screenshots
-----------

![](screenshots/screenshots.png "Controls, URIs, Playback")


License
-------

Copyright 2017 Google Inc. All rights reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

