/*
 * Copyright 2019 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.matrix.android.api.session.room.model.message

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import im.vector.matrix.android.internal.crypto.model.rest.EncryptedFileInfo

@JsonClass(generateAdapter = true)
data class ImageInfo(
        /**
         * The mimetype of the image, e.g. "image/jpeg".
         */
        @Json(name = "mimetype") val mimeType: String,

        /**
         * The intended display width of the image in pixels. This may differ from the intrinsic dimensions of the image file.
         */
        @Json(name = "w") val width: Int = 0,

        /**
         * The intended display height of the image in pixels. This may differ from the intrinsic dimensions of the image file.
         */
        @Json(name = "h") val height: Int = 0,

        /**
         * Size of the image in bytes.
         */
        @Json(name = "size") val size: Int = 0,

        /**
         * Not documented
         */
        @Json(name = "rotation") val rotation: Int = 0,

        /**
         * Not documented
         */
        @Json(name = "orientation") val orientation: Int = 0,

        /**
         * Metadata about the image referred to in thumbnail_url.
         */
        @Json(name = "thumbnail_info") val thumbnailInfo: ThumbnailInfo? = null,

        /**
         * The URL (typically MXC URI) to a thumbnail of the image. Only present if the thumbnail is unencrypted.
         */
        @Json(name = "thumbnail_url") val thumbnailUrl: String? = null,

        /**
         * Information on the encrypted thumbnail file, as specified in End-to-end encryption. Only present if the thumbnail is encrypted.
         */
        @Json(name = "thumbnail_file") val thumbnailFile: EncryptedFileInfo? = null
)