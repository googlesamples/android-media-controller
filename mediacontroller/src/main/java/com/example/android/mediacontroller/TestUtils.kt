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
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import org.json.JSONObject

/**
 * Central location for utility functions that will be used throughout testing
 */

const val METADATA_KEY_PREFIX = "android.media.metadata."

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

fun playbackStateToName(playbackState: Int?): String {
    return when (playbackState) {
        null -> "!null!"
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

fun errorCodeToName(code: Int?): String {
    return when (code) {
        null -> "!null!"
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

fun actionToString(action: Long?): String {
    return when (action) {
        null -> "!null!"
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

fun repeatModeToName(mode: Int?): String {
    return when (mode) {
        null -> "!null!"
        PlaybackStateCompat.REPEAT_MODE_ALL -> "ALL"
        PlaybackStateCompat.REPEAT_MODE_GROUP -> "GROUP"
        PlaybackStateCompat.REPEAT_MODE_INVALID -> "INVALID"
        PlaybackStateCompat.REPEAT_MODE_NONE -> "NONE"
        PlaybackStateCompat.REPEAT_MODE_ONE -> "ONE"
        else -> "!Unknown!"
    }
}

fun shuffleModeToName(mode: Int?): String {
    return when (mode) {
        null -> "!null!"
        PlaybackStateCompat.SHUFFLE_MODE_ALL -> "ALL"
        PlaybackStateCompat.SHUFFLE_MODE_GROUP -> "GROUP"
        PlaybackStateCompat.SHUFFLE_MODE_INVALID -> "INVALID"
        PlaybackStateCompat.SHUFFLE_MODE_NONE -> "NONE"
        else -> "!Unknown!"
    }
}

fun formatPlaybackState(state: PlaybackStateCompat?): String {
    if (state == null) {
        return "!null!"
    }

    val errorCode: String
    val errorMessage: String
    if (state.state == PlaybackStateCompat.STATE_ERROR) {
        errorCode = errorCodeToName(state.errorCode)
        errorMessage = state.errorMessage?.toString() ?: "!null!"
    } else {
        errorCode = "N/A"
        errorMessage = "N/A"
    }

    return ("State:                     " + playbackStateToName(state.state)
            + "\nError Code:                " + errorCode
            + "\nError Message:             " + errorMessage
            + "\nPosition:                  " + state.position
            + "\nBuffered Position:         " + state.bufferedPosition
            + "\nLast Position Update Time: " + state.lastPositionUpdateTime
            + "\nPlayback Speed:            " + state.playbackSpeed
            + "\nActive Queue Item ID:      " + state.activeQueueItemId
            + "\nActions: " + actionsToString(state.actions))
}

fun formatPlaybackStateParsable(state: PlaybackStateCompat?): String {
    if (state == null) {
        return "null,null,null,null,null,null,null,null,null"
    }

    return ("${state.state},${state.errorCode},${state.errorMessage},${state.position},"
            + "${state.bufferedPosition},${state.lastPositionUpdateTime},"
            + "${state.playbackSpeed},${state.activeQueueItemId},${state.actions}")
}

fun getMetadataKey(metadata: MediaMetadataCompat?, key: String): String {
    if (metadata == null) {
        return "!null!"
    }

    val longValues = arrayOf(
            MediaMetadataCompat.METADATA_KEY_DISC_NUMBER,
            MediaMetadataCompat.METADATA_KEY_DURATION,
            MediaMetadataCompat.METADATA_KEY_NUM_TRACKS,
            MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER
    )
    val bitmapValues = arrayOf(
            MediaMetadataCompat.METADATA_KEY_ALBUM_ART,
            MediaMetadataCompat.METADATA_KEY_ART,
            MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON
    )
    val ratingValues = arrayOf(
            MediaMetadataCompat.METADATA_KEY_RATING,
            MediaMetadataCompat.METADATA_KEY_USER_RATING
    )

    if (metadata.containsKey(key)) {
        return when {
            longValues.contains(key) -> metadata.getLong(key).toString()
            bitmapValues.contains(key) -> "Bitmap" //metadata.getBitmap(key)
            ratingValues.contains(key) -> "Rating" //metadata.getRating(key)
            else -> {
                // TODO(b/112436855): cleanup
                metadata.getString(key) ?: (if (metadata.getLong(key) == 0L) {
                    "!null or unknown type!"
                } else {
                    metadata.getLong(key).toString()
                })
            }
        }
    }
    return "!Not present!"
}

fun formatMetadata(metadata: MediaMetadataCompat?): String {
    if (metadata == null) {
        return "!null!"
    }

    val keys = metadata.keySet()
    var s = ""
    keys.forEach { key ->
        val label = if (key.startsWith(METADATA_KEY_PREFIX)) {
            "${key.substringAfter(METADATA_KEY_PREFIX)}:".padEnd(20, ' ')
        } else {
            "$key:".padEnd(20, ' ')
        }
        s += "$label ${getMetadataKey(metadata, key)}\n"
    }
    return s.substringBeforeLast("\n")
}

fun formatMetadataParsable(metadata: MediaMetadataCompat?): String {
    if (metadata == null) {
        return "!null!"
    }

    val keys = metadata.keySet()
    val map: MutableMap<String, String> = mutableMapOf()
    keys.forEach { key ->
        map.put(key, getMetadataKey(metadata, key))
    }
    return JSONObject(map).toString()
}

fun queueToString(_queue: MutableList<MediaSessionCompat.QueueItem>?): String {
    val queue = _queue ?: emptyList<MediaSessionCompat.QueueItem>().toMutableList()
    var s = "${queue.size} items in the queue"
    for (item in queue) {
        val desc = item.description
        s += ("\nQueue ID: ${item.queueId}, Title: ${desc.title}, Subtitle: ${desc.subtitle}, "
                + "Media ID: ${desc.mediaId}, Media URI: ${desc.mediaUri}")
    }
    return s
}

fun queueToStringParsable(_queue: MutableList<MediaSessionCompat.QueueItem>?): String {
    val queue = _queue ?: emptyList<MediaSessionCompat.QueueItem>().toMutableList()
    var s = "${queue.size}"
    for (item in queue) {
        val desc = item.description
        s += ",${item.queueId}|${desc.title}|${desc.subtitle}|${desc.mediaId}|${desc.mediaUri}"
    }
    return s
}

/**
 * The Guided Step Fragment's description holds a maximum of 6 lines of text, so this method
 * formats selected Media Controller details to display.
 */
fun MediaControllerCompat?.formatTvDetailsString(): String {
    if (this == null) {
        return "Null MediaController"
    }

    val state = this.playbackState
    val metadata = this.metadata

    val duration = metadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)

    return ("State: ${playbackStateToName(state?.state)}\n"
            + "Position: ${formatMillisToSeconds(state?.position)}\n"
            + "Title: ${metadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)}\n"
            + "Artist: ${metadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)}\n"
            + "Duration: ${formatMillisToSeconds(duration)}\n"
            + "*See Logcat for more details.")
}

fun formatMillisToSeconds(value: Long?): String {
    return value?.let {
        "%.2fs".format(it / 1000f)
    } ?: "null"
}

