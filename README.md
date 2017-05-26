Media Controller Test
=====================
Create a simple MediaController that connects to a MediaBrowserService
in order to test inter-app media controls.

This app works with the Universal Android Music Player sample,
or any other app that implements the media APIs.
https://github.com/googlesamples/android-UniversalMusicPlayer


Usage
=====

    adb shell am start -n com.example.android.mediacontroller/.MainActivity --es p PACKAGE_NAME --es c MEDIA_BROWSER_SERVICE_NAME --es i MEDIA_URI_MEDIA_ID_OR_QUERY


UAMP Example
============

    adb shell am start -n com.example.android.mediacontroller/.MainActivity --es p com.example.android.uamp --es c com.example.android.uamp.MusicService --es i "https://www.example.com"


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

