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

package im.vector.matrix.android.internal.crypto.actions

import android.os.Handler
import androidx.annotation.WorkerThread
import im.vector.matrix.android.api.listeners.ProgressListener
import im.vector.matrix.android.internal.crypto.MXOlmDevice
import im.vector.matrix.android.internal.crypto.MegolmSessionData
import im.vector.matrix.android.internal.crypto.OutgoingRoomKeyRequestManager
import im.vector.matrix.android.internal.crypto.RoomDecryptorProvider
import im.vector.matrix.android.internal.crypto.model.ImportRoomKeysResult
import im.vector.matrix.android.internal.crypto.model.rest.RoomKeyRequestBody
import im.vector.matrix.android.internal.crypto.store.IMXCryptoStore
import timber.log.Timber

internal class MegolmSessionDataImporter(private val olmDevice: MXOlmDevice,
                                         private val roomDecryptorProvider: RoomDecryptorProvider,
                                         private val outgoingRoomKeyRequestManager: OutgoingRoomKeyRequestManager,
                                         private val cryptoStore: IMXCryptoStore) {

    /**
     * Import a list of megolm session keys.
     * Must be call on the crypto coroutine thread
     *
     * @param megolmSessionsData megolm sessions.
     * @param backUpKeys         true to back up them to the homeserver.
     * @param progressListener   the progress listener
     * @return import room keys result
     */
    @WorkerThread
    fun handle(megolmSessionsData: List<MegolmSessionData>,
               fromBackup: Boolean,
               uiHandler: Handler,
               progressListener: ProgressListener?): ImportRoomKeysResult {
        val t0 = System.currentTimeMillis()

        val totalNumbersOfKeys = megolmSessionsData.size
        var lastProgress = 0
        var totalNumbersOfImportedKeys = 0

        if (progressListener != null) {
            uiHandler.post {
                progressListener.onProgress(0, 100)
            }
        }
        val olmInboundGroupSessionWrappers = olmDevice.importInboundGroupSessions(megolmSessionsData)

        megolmSessionsData.forEachIndexed { cpt, megolmSessionData ->
            val decrypting = roomDecryptorProvider.getOrCreateRoomDecryptor(megolmSessionData.roomId, megolmSessionData.algorithm)

            if (null != decrypting) {
                try {
                    val sessionId = megolmSessionData.sessionId
                    Timber.v("## importRoomKeys retrieve senderKey " + megolmSessionData.senderKey + " sessionId " + sessionId)

                    totalNumbersOfImportedKeys++

                    // cancel any outstanding room key requests for this session
                    val roomKeyRequestBody = RoomKeyRequestBody()

                    roomKeyRequestBody.algorithm = megolmSessionData.algorithm
                    roomKeyRequestBody.roomId = megolmSessionData.roomId
                    roomKeyRequestBody.senderKey = megolmSessionData.senderKey
                    roomKeyRequestBody.sessionId = megolmSessionData.sessionId

                    outgoingRoomKeyRequestManager.cancelRoomKeyRequest(roomKeyRequestBody)

                    // Have another go at decrypting events sent with this session
                    decrypting.onNewSession(megolmSessionData.senderKey!!, sessionId!!)
                } catch (e: Exception) {
                    Timber.e(e, "## importRoomKeys() : onNewSession failed")
                }
            }

            if (progressListener != null) {
                uiHandler.post {
                    val progress = 100 * cpt / totalNumbersOfKeys

                    if (lastProgress != progress) {
                        lastProgress = progress

                        progressListener.onProgress(progress, 100)
                    }
                }
            }
        }

        // Do not back up the key if it comes from a backup recovery
        if (fromBackup) {
            cryptoStore.markBackupDoneForInboundGroupSessions(olmInboundGroupSessionWrappers)
        }

        val t1 = System.currentTimeMillis()

        Timber.v("## importMegolmSessionsData : sessions import " + (t1 - t0) + " ms (" + megolmSessionsData.size + " sessions)")

        return ImportRoomKeysResult(totalNumbersOfKeys, totalNumbersOfImportedKeys)
    }
}