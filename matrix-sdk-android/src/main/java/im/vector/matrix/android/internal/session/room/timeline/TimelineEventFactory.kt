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

import im.vector.matrix.android.api.session.room.timeline.TimelineEvent
import im.vector.matrix.android.internal.database.mapper.asDomain
import im.vector.matrix.android.internal.database.model.EventEntity
import im.vector.matrix.android.internal.session.room.EventRelationExtractor
import im.vector.matrix.android.internal.session.room.membership.SenderRoomMemberExtractor
import io.realm.Realm


internal interface TimelineEventFactory {
    fun create(eventEntity: EventEntity, realm: Realm = eventEntity.realm): TimelineEvent
}

internal interface CacheableTimelineEventFactory : TimelineEventFactory {
    fun clear()
}

internal class SimpleTimelineEventFactory(private val roomMemberExtractor: SenderRoomMemberExtractor,
                                          private val relationExtractor: EventRelationExtractor
) : TimelineEventFactory {
    override fun create(eventEntity: EventEntity, realm: Realm): TimelineEvent {
        val senderRoomMember = roomMemberExtractor.extractFrom(eventEntity, realm)
        val relations = relationExtractor.extractFrom(eventEntity, realm)
        return TimelineEvent(
                eventEntity.asDomain(),
                eventEntity.localId,
                eventEntity.displayIndex,
                senderRoomMember?.displayName,
                senderRoomMember?.avatarUrl,
                eventEntity.sendState,
                relations
        )
    }
}

internal class InMemoryTimelineEventFactory(
        private val roomMemberExtractor: SenderRoomMemberExtractor,
        private val relationExtractor: EventRelationExtractor) : CacheableTimelineEventFactory {

    private val cached = mutableMapOf<String, SenderData>()

    override fun create(eventEntity: EventEntity, realm: Realm): TimelineEvent {
        val sender = eventEntity.sender
        val cacheKey = sender + eventEntity.stateIndex
        val senderData = cached.getOrPut(cacheKey) {
            val senderRoomMember = roomMemberExtractor.extractFrom(eventEntity, realm)
            SenderData(senderRoomMember?.displayName, senderRoomMember?.avatarUrl)
        }
        val relations = relationExtractor.extractFrom(eventEntity, realm)
        return TimelineEvent(
                eventEntity.asDomain(),
                eventEntity.localId,
                eventEntity.displayIndex,
                senderData.senderName,
                senderData.senderAvatar,
                eventEntity.sendState,
                relations
        )
    }

    override fun clear() {
        cached.clear()
    }

    private data class SenderData(
            val senderName: String?,
            val senderAvatar: String?
    )

}