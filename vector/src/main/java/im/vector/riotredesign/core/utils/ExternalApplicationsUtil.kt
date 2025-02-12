/*
 *
 *  * Copyright 2019 New Vector Ltd
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package im.vector.riotredesign.core.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Browser
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import im.vector.riotredesign.BuildConfig
import im.vector.riotredesign.R
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Open a url in the internet browser of the system
 */
fun openUrlInExternalBrowser(context: Context, url: String?) {
    url?.let {
        openUrlInExternalBrowser(context, Uri.parse(it))
    }
}

/**
 * Open a uri in the internet browser of the system
 */
fun openUrlInExternalBrowser(context: Context, uri: Uri?) {
    uri?.let {
        val browserIntent = Intent(Intent.ACTION_VIEW, it).apply {
            putExtra(Browser.EXTRA_APPLICATION_ID, context.packageName)
        }

        try {
            context.startActivity(browserIntent)
        } catch (activityNotFoundException: ActivityNotFoundException) {
            context.toast(R.string.error_no_external_application_found)
        }
    }
}

/**
 * Open sound recorder external application
 */
fun openSoundRecorder(activity: Activity, requestCode: Int) {
    val recordSoundIntent = Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION)

    // Create chooser
    val chooserIntent = Intent.createChooser(recordSoundIntent, activity.getString(R.string.go_on_with))

    try {
        activity.startActivityForResult(chooserIntent, requestCode)
    } catch (activityNotFoundException: ActivityNotFoundException) {
        activity.toast(R.string.error_no_external_application_found)
    }
}

/**
 * Open file selection activity
 */
fun openFileSelection(activity: Activity,
                      fragment: Fragment?,
                      allowMultipleSelection: Boolean,
                      requestCode: Int) {
    val fileIntent = Intent(Intent.ACTION_GET_CONTENT)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        fileIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultipleSelection)
    }

    fileIntent.addCategory(Intent.CATEGORY_OPENABLE)
    fileIntent.type = "*/*"

    try {
        fragment
                ?.startActivityForResult(fileIntent, requestCode)
                ?: run {
                    activity.startActivityForResult(fileIntent, requestCode)
                }
    } catch (activityNotFoundException: ActivityNotFoundException) {
        activity.toast(R.string.error_no_external_application_found)
    }
}

/**
 * Open external video recorder
 */
fun openVideoRecorder(activity: Activity, requestCode: Int) {
    val captureIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)

    // lowest quality
    captureIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0)

    try {
        activity.startActivityForResult(captureIntent, requestCode)
    } catch (activityNotFoundException: ActivityNotFoundException) {
        activity.toast(R.string.error_no_external_application_found)
    }
}

/**
 * Open external camera
 * @return the latest taken picture camera uri
 */
fun openCamera(activity: Activity, titlePrefix: String, requestCode: Int): String? {
    val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

    // the following is a fix for buggy 2.x devices
    val date = Date()
    val formatter = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
    val values = ContentValues()
    values.put(MediaStore.Images.Media.TITLE, titlePrefix + formatter.format(date))
    // The Galaxy S not only requires the name of the file to output the image to, but will also not
    // set the mime type of the picture it just took (!!!). We assume that the Galaxy S takes image/jpegs
    // so the attachment uploader doesn't freak out about there being no mimetype in the content database.
    values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
    var dummyUri: Uri? = null
    try {
        dummyUri = activity.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

        if (null == dummyUri) {
            Timber.e("Cannot use the external storage media to save image")
        }
    } catch (uoe: UnsupportedOperationException) {
        Timber.e(uoe, "Unable to insert camera URI into MediaStore.Images.Media.EXTERNAL_CONTENT_URI " +
                "no SD card? Attempting to insert into device storage.")
    } catch (e: Exception) {
        Timber.e(e, "Unable to insert camera URI into MediaStore.Images.Media.EXTERNAL_CONTENT_URI. $e")
    }

    if (null == dummyUri) {
        try {
            dummyUri = activity.contentResolver.insert(MediaStore.Images.Media.INTERNAL_CONTENT_URI, values)
            if (null == dummyUri) {
                Timber.e("Cannot use the internal storage to save media to save image")
            }

        } catch (e: Exception) {
            Timber.e(e, "Unable to insert camera URI into internal storage. Giving up. $e")
        }
    }

    if (dummyUri != null) {
        captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, dummyUri)
        Timber.v("trying to take a photo on " + dummyUri.toString())
    } else {
        Timber.v("trying to take a photo with no predefined uri")
    }

    // Store the dummy URI which will be set to a placeholder location. When all is lost on Samsung devices,
    // this will point to the data we're looking for.
    // Because Activities tend to use a single MediaProvider for all their intents, this field will only be the
    // *latest* TAKE_PICTURE Uri. This is deemed acceptable as the normal flow is to create the intent then immediately
    // fire it, meaning onActivityResult/getUri will be the next thing called, not another createIntentFor.
    val result = if (dummyUri == null) null else dummyUri.toString()

    try {
        activity.startActivityForResult(captureIntent, requestCode)

        return result
    } catch (activityNotFoundException: ActivityNotFoundException) {
        activity.toast(R.string.error_no_external_application_found)
    }

    return null
}

/**
 * Send an email to address with optional subject and message
 */
fun sendMailTo(address: String, subject: String? = null, message: String? = null, activity: Activity) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts(
            "mailto", address, null))
    intent.putExtra(Intent.EXTRA_SUBJECT, subject)
    intent.putExtra(Intent.EXTRA_TEXT, message)

    try {
        activity.startActivity(intent)
    } catch (activityNotFoundException: ActivityNotFoundException) {
        activity.toast(R.string.error_no_external_application_found)
    }
}

/**
 * Open an arbitrary uri
 */
fun openUri(activity: Activity, uri: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))

    try {
        activity.startActivity(intent)
    } catch (activityNotFoundException: ActivityNotFoundException) {
        activity.toast(R.string.error_no_external_application_found)
    }
}

/**
 * Send media to a third party application.
 *
 * @param activity       the activity
 * @param savedMediaPath the media path
 * @param mimeType       the media mime type.
 */
fun openMedia(activity: Activity, savedMediaPath: String, mimeType: String) {
    val file = File(savedMediaPath)
    val uri = FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".fileProvider", file)

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    try {
        activity.startActivity(intent)
    } catch (activityNotFoundException: ActivityNotFoundException) {
        activity.toast(R.string.error_no_external_application_found)
    }
}

fun shareMedia(context: Context, file: File, mediaMimeType: String?) {

    var mediaUri: Uri? = null
    try {
        mediaUri = FileProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".fileProvider", file)
    } catch (e: Exception) {
        Timber.e("onMediaAction Selected File cannot be shared " + e.message)
    }


    if (null != mediaUri) {
        val sendIntent = Intent()
        sendIntent.action = Intent.ACTION_SEND
        // Grant temporary read permission to the content URI
        sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        sendIntent.type = mediaMimeType
        sendIntent.putExtra(Intent.EXTRA_STREAM, mediaUri)

        context.startActivity(sendIntent)

    }
}
