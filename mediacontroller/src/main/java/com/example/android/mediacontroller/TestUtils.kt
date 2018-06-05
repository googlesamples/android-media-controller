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
package com.example.android.mediacontroller

import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat

/**
 * Central location for utility functions that will be used throughout testing
 */


// Title, Artist, and Duration seem to always be present for a given Media Item, so these
// three Metadata Keys are used to identify unique Media Items
fun MediaMetadataCompat?.isContentSameAs(other: MediaMetadataCompat?): Boolean {
    if (this == null || other == null) {
        if (this == null && other == null) {
            return true
        }
        return false
    }
    return (this.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
            == other.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
            && this.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
            == other.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
            && this.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
            == other.getLong(MediaMetadataCompat.METADATA_KEY_DURATION))
}

fun MediaMetadataCompat?.toBasicString(): String {
    if (this == null) {
        return "{null, null, null}"
    }
    return ("{" + this.getString(MediaMetadataCompat.METADATA_KEY_TITLE) + ", "
            + this.getString(MediaMetadataCompat.METADATA_KEY_ARTIST) + ", "
            + this.getLong(MediaMetadataCompat.METADATA_KEY_DURATION) + "}")
}

fun playbackStateToName(playbackState: Int): String {
    return when (playbackState) {
        PlaybackStateCompat.STATE_NONE -> "STATE_NONE"
        PlaybackStateCompat.STATE_STOPPED -> "STATE_STOPPED"
        PlaybackStateCompat.STATE_PAUSED -> "STATE_PAUSED"
        PlaybackStateCompat.STATE_PLAYING -> "STATE_PLAYING"
        PlaybackStateCompat.STATE_FAST_FORWARDING -> "STATE_FAST_FORWARDING"
        PlaybackStateCompat.STATE_REWINDING -> "STATE_REWINDING"
        PlaybackStateCompat.STATE_BUFFERING -> "STATE_BUFFERING"
        PlaybackStateCompat.STATE_ERROR -> "STATE_ERROR"
        PlaybackStateCompat.STATE_CONNECTING -> "STATE_CONNECTING"
        PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS -> "STATE_SKIPPING_TO_PREVIOUS"
        PlaybackStateCompat.STATE_SKIPPING_TO_NEXT -> "STATE_SKIPPING_TO_NEXT"
        PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM -> "STATE_SKIPPING_TO_QUEUE_ITEM"
        else -> "!Unknown State!"
    }
}

fun errorCodeToName(code: Int): String {
    return when (code) {
        PlaybackStateCompat.ERROR_CODE_UNKNOWN_ERROR -> "ERROR_CODE_UNKNOWN_ERROR"
        PlaybackStateCompat.ERROR_CODE_APP_ERROR -> "ERROR_CODE_APP_ERROR"
        PlaybackStateCompat.ERROR_CODE_NOT_SUPPORTED -> "ERROR_CODE_NOT_SUPPORTED"
        PlaybackStateCompat.ERROR_CODE_AUTHENTICATION_EXPIRED ->
            "ERROR_CODE_AUTHENTICATION_EXPIRED"
        PlaybackStateCompat.ERROR_CODE_PREMIUM_ACCOUNT_REQUIRED ->
            "ERROR_CODE_PREMIUM_ACCOUNT_REQUIRED"
        PlaybackStateCompat.ERROR_CODE_CONCURRENT_STREAM_LIMIT ->
            "ERROR_CODE_CONCURRENT_STREAM_LIMIT"
        PlaybackStateCompat.ERROR_CODE_PARENTAL_CONTROL_RESTRICTED ->
            "ERROR_CODE_PARENTAL_CONTROL_RESTRICTED"
        PlaybackStateCompat.ERROR_CODE_NOT_AVAILABLE_IN_REGION ->
            "ERROR_CODE_NOT_AVAILABLE_IN_REGION"
        PlaybackStateCompat.ERROR_CODE_CONTENT_ALREADY_PLAYING ->
            "ERROR_CODE_CONTENT_ALREADY_PLAYING"
        PlaybackStateCompat.ERROR_CODE_SKIP_LIMIT_REACHED -> "ERROR_CODE_SKIP_LIMIT_REACHED"
        PlaybackStateCompat.ERROR_CODE_ACTION_ABORTED -> "ERROR_CODE_ACTION_ABORTED"
        PlaybackStateCompat.ERROR_CODE_END_OF_QUEUE -> "ERROR_CODE_END_OF_QUEUE"
        else -> "!Unknown Error!"
    }
}

fun actionToString(action: Long): String {
    return when (action) {
        PlaybackStateCompat.ACTION_PREPARE -> "ACTION_PREPARE"
        PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID -> "ACTION_PREPARE_FROM_MEDIA_ID"
        PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH -> "ACTION_PREPARE_FROM_SEARCH"
        PlaybackStateCompat.ACTION_PREPARE_FROM_URI -> "ACTION_PREPARE_FROM_URI"
        PlaybackStateCompat.ACTION_PLAY -> "ACTION_PLAY"
        PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID -> "ACTION_PLAY_FROM_MEDIA_ID"
        PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH -> "ACTION_PLAY_FROM_SEARCH"
        PlaybackStateCompat.ACTION_PLAY_FROM_URI -> "ACTION_PLAY_FROM_URI"
        PlaybackStateCompat.ACTION_PLAY_PAUSE -> "ACTION_PLAY_PAUSE"
        PlaybackStateCompat.ACTION_PAUSE -> "ACTION_PAUSE"
        PlaybackStateCompat.ACTION_STOP -> "ACTION_STOP"
        PlaybackStateCompat.ACTION_SEEK_TO -> "ACTION_SEEK_TO"
        PlaybackStateCompat.ACTION_SKIP_TO_NEXT -> "ACTION_SKIP_TO_NEXT"
        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS -> "ACTION_SKIP_TO_PREVIOUS"
        PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM -> "ACTION_SKIP_TO_QUEUE_ITEM"
        PlaybackStateCompat.ACTION_FAST_FORWARD -> "ACTION_FAST_FORWARD"
        PlaybackStateCompat.ACTION_REWIND -> "ACTION_REWIND"
        PlaybackStateCompat.ACTION_SET_RATING -> "ACTION_SET_RATING"
        PlaybackStateCompat.ACTION_SET_REPEAT_MODE -> "ACTION_SET_REPEAT_MODE"
        PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE -> "ACTION_SET_SHUFFLE_MODE"
        PlaybackStateCompat.ACTION_SET_CAPTIONING_ENABLED -> "ACTION_SET_CAPTIONING_ENABLED"
        else -> "!Unknown Action!"
    }
}

fun actionsToString(actions: Long): String {
    var s = "[\n"
    if (actions and PlaybackStateCompat.ACTION_PREPARE != 0L) {
        s += "\tACTION_PREPARE\n"
    }
    if (actions and PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID != 0L) {
        s += "\tACTION_PREPARE_FROM_MEDIA_ID\n"
    }
    if (actions and PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH != 0L) {
        s += "\tACTION_PREPARE_FROM_SEARCH\n"
    }
    if (actions and PlaybackStateCompat.ACTION_PREPARE_FROM_URI != 0L) {
        s += "\tACTION_PREPARE_FROM_URI\n"
    }
    if (actions and PlaybackStateCompat.ACTION_PLAY != 0L) {
        s += "\tACTION_PLAY\n"
    }
    if (actions and PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID != 0L) {
        s += "\tACTION_PLAY_FROM_MEDIA_ID\n"
    }
    if (actions and PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH != 0L) {
        s += "\tACTION_PLAY_FROM_SEARCH\n"
    }
    if (actions and PlaybackStateCompat.ACTION_PLAY_FROM_URI != 0L) {
        s += "\tACTION_PLAY_FROM_URI\n"
    }
    if (actions and PlaybackStateCompat.ACTION_PLAY_PAUSE != 0L) {
        s += "\tACTION_PLAY_PAUSE\n"
    }
    if (actions and PlaybackStateCompat.ACTION_PAUSE != 0L) {
        s += "\tACTION_PAUSE\n"
    }
    if (actions and PlaybackStateCompat.ACTION_STOP != 0L) {
        s += "\tACTION_STOP\n"
    }
    if (actions and PlaybackStateCompat.ACTION_SEEK_TO != 0L) {
        s += "\tACTION_SEEK_TO\n"
    }
    if (actions and PlaybackStateCompat.ACTION_SKIP_TO_NEXT != 0L) {
        s += "\tACTION_SKIP_TO_NEXT\n"
    }
    if (actions and PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS != 0L) {
        s += "\tACTION_SKIP_TO_PREVIOUS\n"
    }
    if (actions and PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM != 0L) {
        s += "\tACTION_SKIP_TO_QUEUE_ITEM\n"
    }
    if (actions and PlaybackStateCompat.ACTION_FAST_FORWARD != 0L) {
        s += "\tACTION_FAST_FORWARD\n"
    }
    if (actions and PlaybackStateCompat.ACTION_REWIND != 0L) {
        s += "\tACTION_REWIND\n"
    }
    if (actions and PlaybackStateCompat.ACTION_SET_RATING != 0L) {
        s += "\tACTION_SET_RATING\n"
    }
    if (actions and PlaybackStateCompat.ACTION_SET_REPEAT_MODE != 0L) {
        s += "\tACTION_SET_REPEAT_MODE\n"
    }
    if (actions and PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE != 0L) {
        s += "\tACTION_SET_SHUFFLE_MODE\n"
    }
    if (actions and PlaybackStateCompat.ACTION_SET_CAPTIONING_ENABLED != 0L) {
        s += "\tACTION_SET_CAPTIONING_ENABLED\n"
    }
    s += "]"
    return s
}

fun repeatModeToName(mode: Int): String {
    return when (mode) {
        PlaybackStateCompat.REPEAT_MODE_ALL -> "ALL"
        PlaybackStateCompat.REPEAT_MODE_GROUP -> "GROUP"
        PlaybackStateCompat.REPEAT_MODE_INVALID -> "INVALID"
        PlaybackStateCompat.REPEAT_MODE_NONE -> "NONE"
        PlaybackStateCompat.REPEAT_MODE_ONE -> "ONE"
        else -> "!Unknown!"
    }
}

fun shuffleModeToName(mode: Int): String {
    return when (mode) {
        PlaybackStateCompat.SHUFFLE_MODE_ALL -> "ALL"
        PlaybackStateCompat.SHUFFLE_MODE_GROUP -> "GROUP"
        PlaybackStateCompat.SHUFFLE_MODE_INVALID -> "INVALID"
        PlaybackStateCompat.SHUFFLE_MODE_NONE -> "NONE"
        else -> "!Unknown!"
    }
}

fun formatPlaybackState(state: PlaybackStateCompat): String {
    var formattedString = "State:                     " + playbackStateToName(state.state)
    if (state.state == PlaybackStateCompat.STATE_ERROR) {
        formattedString += ("\nError Code:                " + errorCodeToName(state.errorCode)
                + "\nError Message:             " + state.errorMessage)
    }
    formattedString += ("\nPosition:                  " + state.position
            + "\nBuffered Position:         " + state.bufferedPosition
            + "\nLast Position Update Time: " + state.lastPositionUpdateTime
            + "\nPlayback Speed:            " + state.playbackSpeed
            + "\nActive Queue Item ID:      " + state.activeQueueItemId
            + "\nActions: " + actionsToString(state.actions))
    return formattedString
}

fun getMetadataKey(metadata: MediaMetadataCompat, key: String, type: Int = 0): String? {
    if (metadata.containsKey(key)) {
        return when (type) {
            0 -> metadata.getString(key)
            1 -> metadata.getLong(key).toString()
            2 -> "Bitmap" //metadata.getBitmap(key)
            3 -> "Rating" //metadata.getRating(key)
            else -> "!Unknown type!"
        }
    }
    return "!Not present!"
}

fun formatMetadata(metadata: MediaMetadataCompat): String {
    var s = ("MEDIA_ID:            "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_MEDIA_ID))
    s += ("\nADVERTISEMENT:       "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_ADVERTISEMENT))
    s += ("\nALBUM:               "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_ALBUM))
    s += ("\nALBUM_ART:           "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_ALBUM_ART, 2))
    s += ("\nALBUM_ART_URI:       "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI))
    s += ("\nALBUM_ARTIST:        "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST))
    s += ("\nART:                 "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_ART, 2))
    s += ("\nART_URI:             "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_ART_URI))
    s += ("\nARTIST:              "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_ARTIST))
    s += ("\nAUTHOR:              "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_AUTHOR))
    s += ("\nBT_FOLDER_TYPE:      "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_BT_FOLDER_TYPE))
    s += ("\nCOMPILATION:         "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_COMPILATION))
    s += ("\nCOMPOSER:            "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_COMPOSER))
    s += ("\nDATE:                "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_DATE))
    s += ("\nDISC_NUMBER:         "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_DISC_NUMBER, 1))
    s += ("\nDISPLAY_DESCRIPTION: "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION))
    s += ("\nDISPLAY_ICON:        "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, 2))
    s += ("\nDISPLAY_ICON_URI:    "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI))
    s += ("\nDISPLAY_SUBTITLE:    "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE))
    s += ("\nDISPLAY_TITLE:       "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE))
    s += ("\nDOWNLOAD_STATUS:     "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_DOWNLOAD_STATUS))
    s += ("\nDURATION:            "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_DURATION, 1))
    s += ("\nGENRE:               "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_GENRE))
    s += ("\nMEDIA_URI:           "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_MEDIA_URI))
    s += ("\nNUM_TRACKS:          "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, 1))
    s += ("\nRATING:              "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_RATING, 3))
    s += ("\nTITLE:               "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_TITLE))
    s += ("\nTRACK_NUMBER:        "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, 1))
    s += ("\nUSER_RATING:         "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_USER_RATING, 3))
    s += ("\nWRITER:              "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_WRITER))
    s += ("\nYEAR:                "
            + getMetadataKey(metadata, MediaMetadataCompat.METADATA_KEY_YEAR))

    return s
}

fun queueToString(queue: MutableList<MediaSessionCompat.QueueItem>): String {
    var s = "${queue.size} items in the queue"
    for (item in queue) {
        val desc = item.description
        s += ("\nQueue ID: ${item.queueId}, Title: ${desc.title}, Subtitle: ${desc.subtitle}, "
                + "Media ID: ${desc.mediaId}, Media URI: ${desc.mediaUri}")
    }
    return s
}