/*
 * Copyright 2016 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
 * Copyright 2018 New Vector Ltd
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

package im.vector.matrix.android.internal.crypto.model

import android.text.TextUtils
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import im.vector.matrix.android.api.util.JsonDict
import im.vector.matrix.android.internal.crypto.model.rest.DeviceKeys
import java.io.Serializable
import java.util.*

@JsonClass(generateAdapter = true)
data class MXDeviceInfo(

        /**
         * The id of this device.
         */
        @Json(name = "device_id")
        var deviceId: String,

        /**
         * the user id
         */
        @Json(name = "user_id")
        var userId: String,

        /**
         * The list of algorithms supported by this device.
         */
        @Json(name = "algorithms")
        var algorithms: List<String>? = null,

        /**
         * A map from "<key type>:<deviceId>" to "<base64-encoded key>".
         */
        @Json(name = "keys")
        var keys: Map<String, String>? = null,

        /**
         * The signature of this MXDeviceInfo.
         * A map from "<userId>" to a map from "<key type>:<deviceId>" to "<signature>"
         */
        @Json(name = "signatures")
        var signatures: Map<String, Map<String, String>>? = null,

        /*
         * Additional data from the home server.
         */
        @Json(name = "unsigned")
        var unsigned: JsonDict? = null,

        /**
         * Verification state of this device.
         */
        var verified: Int = DEVICE_VERIFICATION_UNKNOWN
) : Serializable {
    /**
     * Tells if the device is unknown
     *
     * @return true if the device is unknown
     */
    val isUnknown: Boolean
        get() = verified == DEVICE_VERIFICATION_UNKNOWN

    /**
     * Tells if the device is verified.
     *
     * @return true if the device is verified
     */
    val isVerified: Boolean
        get() = verified == DEVICE_VERIFICATION_VERIFIED

    /**
     * Tells if the device is unverified.
     *
     * @return true if the device is unverified
     */
    val isUnverified: Boolean
        get() = verified == DEVICE_VERIFICATION_UNVERIFIED

    /**
     * Tells if the device is blocked.
     *
     * @return true if the device is blocked
     */
    val isBlocked: Boolean
        get() = verified == DEVICE_VERIFICATION_BLOCKED

    /**
     * @return the fingerprint
     */
    fun fingerprint(): String? {
        return if (null != keys && !TextUtils.isEmpty(deviceId)) {
            keys!!["ed25519:$deviceId"]
        } else null

    }

    /**
     * @return the identity key
     */
    fun identityKey(): String? {
        return if (null != keys && !TextUtils.isEmpty(deviceId)) {
            keys!!["curve25519:$deviceId"]
        } else null

    }

    /**
     * @return the display name
     */
    fun displayName(): String? {
        return if (null != unsigned) {
            unsigned!!["device_display_name"] as String?
        } else null

    }

    /**
     * @return the signed data map
     */
    fun signalableJSONDictionary(): Map<String, Any> {
        val map = HashMap<String, Any>()

        map["device_id"] = deviceId

        if (null != userId) {
            map["user_id"] = userId!!
        }

        if (null != algorithms) {
            map["algorithms"] = algorithms!!
        }

        if (null != keys) {
            map["keys"] = keys!!
        }

        return map
    }

    /**
     * @return a dictionary of the parameters
     */
    fun toDeviceKeys(): DeviceKeys {
        return DeviceKeys(
                userId = userId,
                deviceId = deviceId,
                algorithms = algorithms!!,
                keys = keys!!,
                signatures = signatures!!
        )
    }

    override fun toString(): String {
        return "MXDeviceInfo $userId:$deviceId"
    }

    companion object {
        // This device is a new device and the user was not warned it has been added.
        const val DEVICE_VERIFICATION_UNKNOWN = -1

        // The user has not yet verified this device.
        const val DEVICE_VERIFICATION_UNVERIFIED = 0

        // The user has verified this device.
        const val DEVICE_VERIFICATION_VERIFIED = 1

        // The user has blocked this device.
        const val DEVICE_VERIFICATION_BLOCKED = 2
    }
}