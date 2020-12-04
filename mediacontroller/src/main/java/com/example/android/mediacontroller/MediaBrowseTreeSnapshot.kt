package com.example.android.mediacontroller

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.SubscriptionCallback
import android.util.Log
import android.widget.Toast
import java.io.OutputStream
import java.io.PrintWriter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore


class MediaBrowseTreeSnapshot(private val context: Context, private val browser: MediaBrowserCompat) {
    private val TAG = "MediaBrowseTreeSnapshot"

    /**
     * Loads the browsers top level children and runs a DFS on them printing out
     * each media item's contentes as it is visited.
     */
    fun takeBrowserSnapshot(outputStream: OutputStream) {
        val loaded = Semaphore(1)
        val executorService = Executors.newFixedThreadPool(4)
        val mItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        executorService.execute {
            try {
                loaded.acquire()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            browser.subscribe(browser.root, object : SubscriptionCallback() {
                override fun onChildrenLoaded(parentId: String,
                                              children: List<MediaBrowserCompat.MediaItem>) {
                    // Notify the main thread that all of the children have loaded
                    Log.i(TAG, "Children loaded for init")
                    mItems.addAll(children)
                    loaded.release()

                    super.onChildrenLoaded(parentId, children)
                }
            })

            // Wait for all of the media children to be loaded before starting snapshot
            try {
                loaded.acquire()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

            if (mItems.size > 0) {
                runDFSOnBrowseTree(mItems, executorService, outputStream)
            } else {
                notifyUser("No media items found, could not save tree.")
            }
        }
    }

    /**
     * Kicks off the browse tree depth first search by visiting all of the top level media
     * item nodes.
     */
    private fun runDFSOnBrowseTree(mediaItems: MutableList<MediaBrowserCompat.MediaItem>, executorService: ExecutorService, outputStream: OutputStream) {
        val printWriter = PrintWriter(outputStream)
        printWriter.println("Root:")
        val writeCompleted = Semaphore(1)
        executorService.execute {
            for (item in mediaItems) {
                try {
                    writeCompleted.acquire()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
                visitMediaItemNode(item, printWriter, 1,
                        executorService)
                writeCompleted.release()
            }
            printWriter.flush()
            printWriter.close()
            outputStream.close()
            notifyUser("MediaItems saved to specified location.")
        }
    }

    /**
     * Visits a media item node by printing out its contents and then visiting all of its children.
     */
    private fun visitMediaItemNode(mediaItem: MediaBrowserCompat.MediaItem?, printWriter: PrintWriter, depth: Int,
                                   executorService: ExecutorService) {
        if (mediaItem != null) {
            printMediaItemDescription(printWriter, mediaItem, depth)
            val mid = if (mediaItem.mediaId != null) mediaItem.mediaId!! else ""

            // If a media item is not a leaf continue DFS on it
            if (mediaItem.isBrowsable && mid != "") {
                val loaded = Semaphore(1)
                try {
                    loaded.acquire()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
                val mediaChildren: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
                executorService.execute {
                    browser.subscribe(mid,
                            object : SubscriptionCallback() {
                                override fun onChildrenLoaded(parentId: String,
                                                              children: List<MediaBrowserCompat.MediaItem>) {
                                    // Notify the main thread that all of the children have loaded
                                    mediaChildren.addAll(children)
                                    loaded.release()
                                    super.onChildrenLoaded(parentId, children)
                                }
                            })
                }

                // Wait for all of the media children to be loaded before continuing DFS
                try {
                    loaded.acquire()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }

                // Run visit on all of the nodes children
                for (mediaItemChild in mediaChildren) {
                    visitMediaItemNode(mediaItemChild, printWriter, depth + 1,
                            executorService)
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
        Log.i(TAG, "Writing media Item");
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