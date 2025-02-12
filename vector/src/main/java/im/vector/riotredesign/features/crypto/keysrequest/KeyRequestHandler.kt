/*
 * Copyright 2016 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
 * Copyright 2018 New Vector Ltd
 * Copyright 2019 New Vector Ltd
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

package im.vector.riotredesign.features.crypto.keysrequest

import android.content.Context
import android.text.TextUtils
import im.vector.matrix.android.api.MatrixCallback
import im.vector.matrix.android.api.session.Session
import im.vector.matrix.android.api.session.crypto.keyshare.RoomKeysRequestListener
import im.vector.matrix.android.api.session.crypto.sas.SasVerificationService
import im.vector.matrix.android.api.session.crypto.sas.SasVerificationTransaction
import im.vector.matrix.android.api.session.crypto.sas.SasVerificationTxState
import im.vector.matrix.android.internal.crypto.IncomingRoomKeyRequest
import im.vector.matrix.android.internal.crypto.IncomingRoomKeyRequestCancellation
import im.vector.matrix.android.internal.crypto.model.MXDeviceInfo
import im.vector.matrix.android.internal.crypto.model.MXUsersDevicesMap
import im.vector.matrix.android.internal.crypto.model.rest.DeviceInfo
import im.vector.matrix.android.internal.crypto.model.rest.DevicesListResponse
import im.vector.riotredesign.R
import im.vector.riotredesign.features.crypto.verification.SASVerificationActivity
import im.vector.riotredesign.features.popup.PopupAlertManager
import timber.log.Timber
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

/**
 * Manage the key share events.
 * Listens for incoming key request and display an alert to the user asking him to ignore / verify
 * calling device / or accept without verifying.
 * If several requests come from same user/device, a single alert is displayed (this alert will accept/reject all request
 * depending on user action)
 */
class KeyRequestHandler(val context: Context,
                        val session: Session)
    : RoomKeysRequestListener,
        SasVerificationService.SasVerificationListener {

    private val alertsToRequests = HashMap<String, ArrayList<IncomingRoomKeyRequest>>()

    init {
        session.getSasVerificationService().addListener(this)

        session.addRoomKeysRequestListener(this)
    }

    fun ensureStarted() = Unit

    /**
     * Handle incoming key request.
     *
     * @param request the key request.
     */
    override fun onRoomKeyRequest(request: IncomingRoomKeyRequest) {
        val userId = request.userId
        val deviceId = request.deviceId
        val requestId = request.requestId

        if (userId.isNullOrBlank() || deviceId.isNullOrBlank() || requestId.isNullOrBlank()) {
            Timber.e("## handleKeyRequest() : invalid parameters")
            return
        }

        //Do we already have alerts for this user/device
        val mappingKey = keyForMap(deviceId, userId)
        if (alertsToRequests.containsKey(mappingKey)) {
            //just add the request, there is already an alert for this
            alertsToRequests[mappingKey]?.add(request)
            return
        }

        alertsToRequests[mappingKey] = ArrayList<IncomingRoomKeyRequest>().apply { this.add(request) }

        //Add a notification for every incoming request
        session.downloadKeys(Arrays.asList(userId), false, object : MatrixCallback<MXUsersDevicesMap<MXDeviceInfo>> {
            override fun onSuccess(data: MXUsersDevicesMap<MXDeviceInfo>) {
                val deviceInfo = data.getObject(deviceId, userId)

                if (null == deviceInfo) {
                    Timber.e("## displayKeyShareDialog() : No details found for device $userId:$deviceId")
                    //ignore
                    return
                }

                if (deviceInfo.isUnknown) {
                    session.setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_UNVERIFIED, deviceId, userId)

                    deviceInfo.verified = MXDeviceInfo.DEVICE_VERIFICATION_UNVERIFIED

                    //can we get more info on this device?
                    session.getDevicesList(object : MatrixCallback<DevicesListResponse> {
                        override fun onSuccess(data: DevicesListResponse) {
                            data.devices?.find { it.deviceId == deviceId }?.let {
                                postAlert(context, userId, deviceId, true, deviceInfo, it)
                            } ?: run {
                                postAlert(context, userId, deviceId, true, deviceInfo)
                            }
                        }

                        override fun onFailure(failure: Throwable) {
                            postAlert(context, userId, deviceId, true, deviceInfo)
                        }

                    })
                } else {
                    postAlert(context, userId, deviceId, false, deviceInfo)
                }
            }

            override fun onFailure(failure: Throwable) {
                //ignore
                Timber.e(failure, "## displayKeyShareDialog : downloadKeys")
            }
        })

    }

    private fun postAlert(context: Context,
                          userId: String,
                          deviceId: String,
                          wasNewDevice: Boolean,
                          deviceInfo: MXDeviceInfo?,
                          moreInfo: DeviceInfo? = null) {
        val deviceName = if (TextUtils.isEmpty(deviceInfo!!.displayName())) deviceInfo.deviceId else deviceInfo.displayName()
        val dialogText: String?

        if (moreInfo != null) {
            val lastSeenIp = if (moreInfo.lastSeenIp.isNullOrBlank()) {
                context.getString(R.string.encryption_information_unknown_ip)
            } else {
                moreInfo.lastSeenIp
            }

            val lastSeenTime = moreInfo.lastSeenTs?.let { ts ->
                val dateFormatTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                val date = Date(ts)

                val time = dateFormatTime.format(date)
                val dateFormat = DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault())

                dateFormat.format(date) + ", " + time
            } ?: "-"

            val lastSeenInfo = context.getString(R.string.devices_details_last_seen_format, lastSeenIp, lastSeenTime)
            dialogText = if (wasNewDevice) {
                context.getString(R.string.you_added_a_new_device_with_info, deviceName, lastSeenInfo)
            } else {
                context.getString(R.string.your_unverified_device_requesting_with_info, deviceName, lastSeenInfo)
            }
        } else {
            dialogText = if (wasNewDevice) {
                context.getString(R.string.you_added_a_new_device, deviceName)
            } else {
                context.getString(R.string.your_unverified_device_requesting, deviceName)
            }
        }


        val alert = PopupAlertManager.VectorAlert(
                alertManagerId(deviceId, userId),
                context.getString(R.string.key_share_request),
                dialogText,
                R.drawable.key_small
        )

        alert.colorRes = R.color.key_share_req_accent_color

        val mappingKey = keyForMap(deviceId, userId)
        alert.dismissedAction = Runnable {
            denyAllRequests(mappingKey)
        }

        alert.addButton(
                context.getString(R.string.start_verification_short_label),
                Runnable {
                    alert.weakCurrentActivity?.get()?.let {
                        val intent = SASVerificationActivity.outgoingIntent(it,
                                session.sessionParams.credentials.userId,
                                userId, deviceId)
                        it.startActivity(intent)
                    }
                },
                false
        )

        alert.addButton(context.getString(R.string.share_without_verifying_short_label), Runnable {
            shareAllSessions(mappingKey)
        })

        alert.addButton(context.getString(R.string.ignore_request_short_label), Runnable {
            denyAllRequests(mappingKey)
        })

        PopupAlertManager.postVectorAlert(alert)
    }

    private fun denyAllRequests(mappingKey: String) {
        alertsToRequests[mappingKey]?.forEach {
            it.ignore?.run()
        }
        alertsToRequests.remove(mappingKey)
    }

    private fun shareAllSessions(mappingKey: String) {
        alertsToRequests[mappingKey]?.forEach {
            it.share?.run()
        }
        alertsToRequests.remove(mappingKey)
    }

    /**
     * Manage a cancellation request.
     *
     * @param request the cancellation request.
     */
    override fun onRoomKeyRequestCancellation(request: IncomingRoomKeyRequestCancellation) {
        // see if we can find the request in the queue
        val userId = request.userId
        val deviceId = request.deviceId
        val requestId = request.requestId

        if (TextUtils.isEmpty(userId) || TextUtils.isEmpty(deviceId) || TextUtils.isEmpty(requestId)) {
            Timber.e("## handleKeyRequestCancellation() : invalid parameters")
            return
        }

        val alertMgrUniqueKey = alertManagerId(deviceId!!, userId!!)
        alertsToRequests[alertMgrUniqueKey]?.removeAll {
            it.deviceId == request.deviceId
                    && it.userId == request.userId
                    && it.requestId == request.requestId
        }
        if (alertsToRequests[alertMgrUniqueKey]?.isEmpty() == true) {
            PopupAlertManager.cancelAlert(alertMgrUniqueKey)
            alertsToRequests.remove(keyForMap(deviceId, userId))
        }
    }

    override fun transactionCreated(tx: SasVerificationTransaction) {
    }

    override fun transactionUpdated(tx: SasVerificationTransaction) {
        val state = tx.state
        if (state == SasVerificationTxState.Verified) {
            //ok it's verified, see if we have key request for that
            shareAllSessions("${tx.otherDeviceId}${tx.otherUserId}")
            PopupAlertManager.cancelAlert("ikr_${tx.otherDeviceId}${tx.otherUserId}")
        }
    }

    override fun markedAsManuallyVerified(userId: String, deviceId: String) {
        //accept related requests
        shareAllSessions(keyForMap(deviceId, userId))
        PopupAlertManager.cancelAlert(alertManagerId(deviceId, userId))
    }

    private fun keyForMap(deviceId: String, userId: String) = "$deviceId$userId"

    private fun alertManagerId(deviceId: String, userId: String) = "ikr_$deviceId$userId"
}
