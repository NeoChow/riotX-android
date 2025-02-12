/*
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

package im.vector.matrix.android.internal.crypto

import android.text.TextUtils
import im.vector.matrix.android.api.auth.data.Credentials
import im.vector.matrix.android.api.session.crypto.keyshare.RoomKeysRequestListener
import im.vector.matrix.android.api.session.events.model.Event
import im.vector.matrix.android.api.session.events.model.toModel
import im.vector.matrix.android.internal.crypto.model.rest.RoomKeyShare
import im.vector.matrix.android.internal.crypto.store.IMXCryptoStore
import timber.log.Timber
import java.util.*
import kotlin.collections.ArrayList

internal class IncomingRoomKeyRequestManager(
        private val credentials: Credentials,
        private val cryptoStore: IMXCryptoStore,
        private val roomDecryptorProvider: RoomDecryptorProvider) {


    // list of IncomingRoomKeyRequests/IncomingRoomKeyRequestCancellations
    // we received in the current sync.
    private val receivedRoomKeyRequests = ArrayList<IncomingRoomKeyRequest>()
    private val receivedRoomKeyRequestCancellations = ArrayList<IncomingRoomKeyRequestCancellation>()

    // the listeners
    private val roomKeysRequestListeners: MutableSet<RoomKeysRequestListener> = HashSet()

    init {
        receivedRoomKeyRequests.addAll(cryptoStore.getPendingIncomingRoomKeyRequests())
    }

    /**
     * Called when we get an m.room_key_request event
     * It must be called on CryptoThread
     *
     * @param event the announcement event.
     */
    suspend fun onRoomKeyRequestEvent(event: Event) {
        val roomKeyShare = event.getClearContent().toModel<RoomKeyShare>()
        when (roomKeyShare?.action) {
            RoomKeyShare.ACTION_SHARE_REQUEST      -> receivedRoomKeyRequests.add(IncomingRoomKeyRequest(event))
            RoomKeyShare.ACTION_SHARE_CANCELLATION -> receivedRoomKeyRequestCancellations.add(IncomingRoomKeyRequestCancellation(event))
            else                                   -> Timber.e("## onRoomKeyRequestEvent() : unsupported action " + roomKeyShare?.action)
        }
    }

    /**
     * Process any m.room_key_request events which were queued up during the
     * current sync.
     * It must be called on CryptoThread
     */
    fun processReceivedRoomKeyRequests() {
        val roomKeyRequestsToProcess = ArrayList(receivedRoomKeyRequests)
        receivedRoomKeyRequests.clear()
        for (request in roomKeyRequestsToProcess) {
            val userId = request.userId
            val deviceId = request.deviceId
            val body = request.requestBody
            val roomId = body!!.roomId
            val alg = body.algorithm

            Timber.v("m.room_key_request from " + userId + ":" + deviceId + " for " + roomId + " / " + body.sessionId + " id " + request.requestId)
            if (userId == null || credentials.userId != userId) {
                // TODO: determine if we sent this device the keys already: in
                Timber.e("## processReceivedRoomKeyRequests() : Ignoring room key request from other user for now")
                return
            }
            // todo: should we queue up requests we don't yet have keys for, in case they turn up later?
            // if we don't have a decryptor for this room/alg, we don't have
            // the keys for the requested events, and can drop the requests.
            val decryptor = roomDecryptorProvider.getRoomDecryptor(roomId, alg)
            if (null == decryptor) {
                Timber.e("## processReceivedRoomKeyRequests() : room key request for unknown $alg in room $roomId")
                continue
            }
            if (!decryptor.hasKeysForKeyRequest(request)) {
                Timber.e("## processReceivedRoomKeyRequests() : room key request for unknown session " + body.sessionId!!)
                cryptoStore.deleteIncomingRoomKeyRequest(request)
                continue
            }

            if (TextUtils.equals(deviceId, credentials.deviceId) && TextUtils.equals(credentials.userId, userId)) {
                Timber.v("## processReceivedRoomKeyRequests() : oneself device - ignored")
                cryptoStore.deleteIncomingRoomKeyRequest(request)
                continue
            }
            request.share = Runnable {
                decryptor.shareKeysWithDevice(request)
                cryptoStore.deleteIncomingRoomKeyRequest(request)
            }
            request.ignore = Runnable {
                cryptoStore.deleteIncomingRoomKeyRequest(request)
            }
            // if the device is verified already, share the keys
            val device = cryptoStore.getUserDevice(deviceId!!, userId)
            if (device != null) {
                if (device.isVerified) {
                    Timber.v("## processReceivedRoomKeyRequests() : device is already verified: sharing keys")
                    cryptoStore.deleteIncomingRoomKeyRequest(request)
                    request.share?.run()
                    continue
                }

                if (device.isBlocked) {
                    Timber.v("## processReceivedRoomKeyRequests() : device is blocked -> ignored")
                    cryptoStore.deleteIncomingRoomKeyRequest(request)
                    continue
                }
            }
            cryptoStore.storeIncomingRoomKeyRequest(request)
            onRoomKeyRequest(request)
        }

        var receivedRoomKeyRequestCancellations: List<IncomingRoomKeyRequestCancellation>? = null

        synchronized(this.receivedRoomKeyRequestCancellations) {
            if (!this.receivedRoomKeyRequestCancellations.isEmpty()) {
                receivedRoomKeyRequestCancellations = this.receivedRoomKeyRequestCancellations.toList()
                this.receivedRoomKeyRequestCancellations.clear()
            }
        }

        if (null != receivedRoomKeyRequestCancellations) {
            for (request in receivedRoomKeyRequestCancellations!!) {
                Timber.v("## ## processReceivedRoomKeyRequests() : m.room_key_request cancellation for " + request.userId
                         + ":" + request.deviceId + " id " + request.requestId)

                // we should probably only notify the app of cancellations we told it
                // about, but we don't currently have a record of that, so we just pass
                // everything through.
                onRoomKeyRequestCancellation(request)
                cryptoStore.deleteIncomingRoomKeyRequest(request)
            }
        }
    }

    /**
     * Dispatch onRoomKeyRequest
     *
     * @param request the request
     */
    private fun onRoomKeyRequest(request: IncomingRoomKeyRequest) {
        synchronized(roomKeysRequestListeners) {
            for (listener in roomKeysRequestListeners) {
                try {
                    listener.onRoomKeyRequest(request)
                } catch (e: Exception) {
                    Timber.e(e, "## onRoomKeyRequest() failed")
                }

            }
        }
    }


    /**
     * A room key request cancellation has been received.
     *
     * @param request the cancellation request
     */
    private fun onRoomKeyRequestCancellation(request: IncomingRoomKeyRequestCancellation) {
        synchronized(roomKeysRequestListeners) {
            for (listener in roomKeysRequestListeners) {
                try {
                    listener.onRoomKeyRequestCancellation(request)
                } catch (e: Exception) {
                    Timber.e(e, "## onRoomKeyRequestCancellation() failed")
                }

            }
        }
    }

    fun addRoomKeysRequestListener(listener: RoomKeysRequestListener) {
        synchronized(roomKeysRequestListeners) {
            roomKeysRequestListeners.add(listener)
        }
    }

    fun removeRoomKeysRequestListener(listener: RoomKeysRequestListener) {
        synchronized(roomKeysRequestListeners) {
            roomKeysRequestListeners.remove(listener)
        }
    }

}