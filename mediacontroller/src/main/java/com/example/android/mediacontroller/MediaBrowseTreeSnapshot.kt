package com.example.android.mediacontroller

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.SubscriptionCallback
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.io.PrintWriter
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class MediaBrowseTreeSnapshot(private val context: Context, private val browser: MediaBrowserCompat):ViewModel() {
    private val TAG = "MediaBrowseTreeSnapshot"


    /**
     * Loads the browsers top level children and runs a DFS on them printing out
     * each media item's contentes as it is visited.
     */
    fun takeBrowserSnapshot(outputStream: OutputStream) {

        viewModelScope.launch {
            val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = getChildNodes(browser.root)
            if (mediaItems.isNotEmpty()) {
                runDFSOnBrowseTree(mediaItems, outputStream)
                for (item in mediaItems) {
                    Log.i(TAG, item.toString())
                }
            } else {
                notifyUser("No media items found, could not save tree.")
            }
        }
    }

    private suspend fun getChildNodes(rootItemMid: String): MutableList<MediaBrowserCompat.MediaItem> =
            suspendCoroutine {
                val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
                browser.subscribe(rootItemMid, object : SubscriptionCallback() {
                    override fun onChildrenLoaded(parentId: String,
                                                  children: List<MediaBrowserCompat.MediaItem>) {
                        // Notify the main thread that all of the children have loaded
                        mediaItems.addAll(children)
                        super.onChildrenLoaded(parentId, children)
                        it.resume(mediaItems)
                    }
                })
            }

    /**
     * Kicks off the browse tree depth first search by visiting all of the top level media
     * item nodes.
     */
    private suspend fun runDFSOnBrowseTree(mediaItems: MutableList<MediaBrowserCompat.MediaItem>, outputStream: OutputStream) {
        val printWriter = PrintWriter(outputStream)
        printWriter.println("Root:")
        for (item in mediaItems) {
            visitMediaItemNode(item, printWriter, 1)
        }
        printWriter.flush()
        printWriter.close()
        outputStream.close()
        notifyUser("MediaItems saved to specified location.")
    }

    /**
     * Visits a media item node by printing out its contents and then visiting all of its children.
     */
    private suspend fun visitMediaItemNode(mediaItem: MediaBrowserCompat.MediaItem?, printWriter: PrintWriter, depth: Int) {
        if (mediaItem != null) {
            printMediaItemDescription(printWriter, mediaItem, depth)
            val mid = if (mediaItem.mediaId != null) mediaItem.mediaId!! else ""

            // If a media item is not a leaf continue DFS on it
            if (mediaItem.isBrowsable && mid != "") {

                val mediaChildren: MutableList<MediaBrowserCompat.MediaItem> = getChildNodes(mid)

                // Run visit on all of the nodes children
                for (mediaItemChild in mediaChildren) {
                    visitMediaItemNode(mediaItemChild, printWriter, depth + 1)
                    Log.i(TAG, "Visiting:" + mediaItemChild.toString())
                }
            }
        }
    }

    /**
     * Prints the contents of a media item using a print writer.
     */
    private fun printMediaItemDescription(printWriter: PrintWriter, mediaItem: MediaBrowserCompat.MediaItem, depth: Int) {
        val descriptionCompat = mediaItem.description
        // Tab the media item to the respective depth
        val tabStr = String(CharArray(depth)).replace("\u0000",
                "\t")
        val titleStr = if (descriptionCompat.title != null) descriptionCompat.title.toString() else "NAN"
        val subTitleStr = if (descriptionCompat.subtitle != null) descriptionCompat.subtitle.toString() else "NAN"
        val mIDStr = if (descriptionCompat.mediaId != null) descriptionCompat.mediaId else "NAN"
        val uriStr = if (descriptionCompat.mediaUri != null) descriptionCompat.mediaUri.toString() else "NAN"
        val desStr = if (descriptionCompat.description != null) descriptionCompat.description.toString() else "NAN"
        val infoStr = String.format(
                "%sTitle:%s,Subtitle:%s,MediaId:%s,URI:%s,Description:%s",
                tabStr, titleStr, subTitleStr, mIDStr, uriStr, desStr)
        printWriter.println(infoStr)
    }

    /**
     * Display formatted toast to user.
     */
    private fun notifyUser(textToNotify: String) {
        Handler(Looper.getMainLooper()).post {
            val toast = Toast.makeText(
                    context,
                    textToNotify,
                    Toast.LENGTH_LONG)
            toast.setMargin(50f, 50f)
            toast.show()
        }
    }
}