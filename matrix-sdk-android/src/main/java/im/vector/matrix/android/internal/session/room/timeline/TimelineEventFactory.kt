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

package im.vector.matrix.android.internal.session.room.timeline

import im.vector.matrix.android.api.session.crypto.CryptoService
import im.vector.matrix.android.api.session.events.model.Event
import im.vector.matrix.android.api.session.events.model.EventType
import im.vector.matrix.android.api.session.room.timeline.TimelineEvent
import im.vector.matrix.android.api.session.room.timeline.TimelineService
import im.vector.matrix.android.internal.crypto.MXDecryptionException
import im.vector.matrix.android.internal.crypto.MXEventDecryptionResult
import im.vector.matrix.android.internal.database.mapper.asDomain
import im.vector.matrix.android.internal.database.model.EventEntity
import im.vector.matrix.android.internal.session.room.EventRelationExtractor
import im.vector.matrix.android.internal.session.room.membership.RoomMembers
import im.vector.matrix.android.internal.session.room.membership.SenderRoomMemberExtractor
import io.realm.Realm
import timber.log.Timber
import java.util.*

/**
 * This class is responsible for building [TimelineEvent] returned by a [Timeline] through [TimelineService]
 * It handles decryption, extracting additional data around an event as sender data and relation.
 */
internal class TimelineEventFactory(
        private val roomMemberExtractor: SenderRoomMemberExtractor,
        private val relationExtractor: EventRelationExtractor,
        private val cryptoService: CryptoService) {

    private val timelineId = UUID.randomUUID().toString()
    private val senderCache = mutableMapOf<String, SenderData>()
    private val decryptionCache = mutableMapOf<String, MXEventDecryptionResult>()

    fun create(eventEntity: EventEntity, realm: Realm = eventEntity.realm): TimelineEvent {
        val sender = eventEntity.sender
        val cacheKey = sender + eventEntity.localId
        val senderData = senderCache.getOrPut(cacheKey) {
            val senderRoomMember = roomMemberExtractor.extractFrom(eventEntity, realm)
            val isUniqueDisplayName = RoomMembers(realm, eventEntity.roomId).isUniqueDisplayName(senderRoomMember?.displayName)

            SenderData(senderRoomMember?.displayName,
                    isUniqueDisplayName,
                    senderRoomMember?.avatarUrl)
        }
        val event = eventEntity.asDomain()
        if (event.getClearType() == EventType.ENCRYPTED) {
            handleEncryptedEvent(event, eventEntity.localId)
        }

        val relations = relationExtractor.extractFrom(eventEntity, realm)
        return TimelineEvent(
                event,
                eventEntity.localId,
                eventEntity.displayIndex,
                senderData.senderName,
                senderData.isUniqueDisplayName,
                senderData.senderAvatar,
                eventEntity.sendState,
                relations
        )
    }

    private fun handleEncryptedEvent(event: Event, cacheKey: String) {
        Timber.v("Encrypted event: try to decrypt ${event.eventId}")
        val cachedDecryption = decryptionCache[cacheKey]
        if (cachedDecryption != null) {
            Timber.v("Encrypted event ${event.eventId} cached")
            event.setClearData(cachedDecryption)
        } else {
            try {
                val result = cryptoService.decryptEvent(event, timelineId)
                if (result != null) {
                    decryptionCache[cacheKey] = result
                }
                event.setClearData(result)
            } catch (failure: Throwable) {
                Timber.e(failure, "Encrypted event: decryption failed")
                if (failure is MXDecryptionException) {
                    event.setCryptoError(failure.cryptoError)
                }
            }
        }
    }

    fun clear() {
        senderCache.clear()
    }

    private data class SenderData(
            val senderName: String?,
            val isUniqueDisplayName: Boolean,
            val senderAvatar: String?
    )
}