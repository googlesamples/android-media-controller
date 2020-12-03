package com.example.android.mediacontroller

import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.SubscriptionCallback
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintWriter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

class MediaBrowseTreeSnapshot(private val mBrowser : MediaBrowserCompat, private val mContext: Context) {
    private val TAG = "MediaBrowseTreeSnapshot"


    fun takeBrowserSnapshot(){
        val loaded = Semaphore(1)
        val executorService = Executors.newFixedThreadPool(4)
        val mItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        executorService.execute{
            try {
                loaded.acquire()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            mBrowser.subscribe(mBrowser.root, object : SubscriptionCallback() {
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

            if(mItems.size > 0){
                takeBrowserSnapshotImpl(mItems, executorService)
            }
            else{
                notifyUser("No media items found, could not save tree.")
            }

        }
    }

    private fun takeBrowserSnapshotImpl(mItems: MutableList<MediaBrowserCompat.MediaItem>, executorService: ExecutorService){
        // Create output file
        val root = Environment.getExternalStorageDirectory()
        val dirsPath = root.absolutePath + "/Temp/"
        val dirs = File(dirsPath)
        dirs.mkdirs()
        val file = File(dirs.absolutePath,
                "_BrowseTreeContent.txt")
        if (file.exists()) {
            file.delete()
        }
        try {
            val f = FileOutputStream(file)
            val pw = PrintWriter(f)
            pw.println("Root:")
            val writeCompleted = Semaphore(1)
            executorService.execute {
                for (item in mItems) {
                    try {
                        writeCompleted.acquire()
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                    visitMediaItemNode(item, pw, 1,
                            executorService)
                    writeCompleted.release()
                }
                pw.flush()
                pw.close()
                try {
                    f.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                notifyUser("MediaItems saved to " +
                        file.absolutePath)
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }
    }


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
                val mChildren: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
                executorService.execute {
                    mBrowser.subscribe(mid,
                            object : SubscriptionCallback() {
                                override fun onChildrenLoaded(parentId: String,
                                                              children: List<MediaBrowserCompat.MediaItem>) {
                                    // Notify the main thread that all of the children have loaded
                                    mChildren.addAll(children)
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

                // Run DFS on all of the nodes children
                for (mediaItemChild in mChildren) {
                    visitMediaItemNode(mediaItemChild, printWriter, depth + 1,
                            executorService)
                }
            }
        }
    }

    private fun printMediaItemDescription(printWriter: PrintWriter, mediaItem: MediaBrowserCompat.MediaItem, depth: Int){
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

    private fun notifyUser(textToNotify: String) {
        Handler(Looper.getMainLooper()).post {
            val toast = Toast.makeText(
                    mContext,
                    textToNotify,
                    Toast.LENGTH_LONG)
            toast.setMargin(50f, 50f)
            toast.show()
        }
    }
}