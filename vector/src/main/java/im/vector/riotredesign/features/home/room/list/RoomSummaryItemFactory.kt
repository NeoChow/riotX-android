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

package im.vector.riotredesign.features.home.room.list

import im.vector.matrix.android.api.session.events.model.EventType
import im.vector.matrix.android.api.session.events.model.toModel
import im.vector.matrix.android.api.session.room.model.RoomSummary
import im.vector.matrix.android.api.session.room.model.message.MessageContent
import im.vector.riotredesign.R
import im.vector.riotredesign.core.extensions.localDateTime
import im.vector.riotredesign.core.resources.ColorProvider
import im.vector.riotredesign.core.resources.DateProvider
import im.vector.riotredesign.features.home.room.detail.timeline.format.NoticeEventFormatter
import im.vector.riotredesign.features.home.room.detail.timeline.helper.TimelineDateFormatter
import im.vector.riotredesign.features.home.room.detail.timeline.helper.senderName
import me.gujun.android.span.span

class RoomSummaryItemFactory(private val noticeEventFormatter: NoticeEventFormatter,
                             private val timelineDateFormatter: TimelineDateFormatter,
                             private val colorProvider: ColorProvider) {

    fun create(roomSummary: RoomSummary, onRoomSelected: (RoomSummary) -> Unit): RoomSummaryItem {
        val unreadCount = roomSummary.notificationCount
        val showHighlighted = roomSummary.highlightCount > 0

        var latestFormattedEvent: CharSequence = ""
        var latestEventTime: CharSequence = ""
        val latestEvent = roomSummary.latestEvent
        if (latestEvent != null) {
            val date = latestEvent.root.localDateTime()
            val currentData = DateProvider.currentLocalDateTime()
            val isSameDay = date.toLocalDate() == currentData.toLocalDate()
            latestFormattedEvent = if (latestEvent.root.type == EventType.MESSAGE) {
                val senderName = latestEvent.senderName() ?: latestEvent.root.sender
                val content = latestEvent.root.content?.toModel<MessageContent>()
                val message = content?.body ?: ""
                if (roomSummary.isDirect.not() && senderName != null) {
                    span {
                        text = senderName
                        textColor = colorProvider.getColorFromAttribute(R.attr.riotx_text_primary)
                    }
                            .append(" - ")
                            .append(message)
                } else {
                    message
                }
            } else {
                span {
                    text = noticeEventFormatter.format(latestEvent) ?: ""
                    textStyle = "italic"
                }
            }
            latestEventTime = if (isSameDay) {
                timelineDateFormatter.formatMessageHour(date)
            } else {
                //TODO: change this
                timelineDateFormatter.formatMessageDay(date)
            }
        }
        return RoomSummaryItem_()
                .id(roomSummary.roomId)
                .roomId(roomSummary.roomId)
                .lastEventTime(latestEventTime)
                .lastFormattedEvent(latestFormattedEvent)
                .roomName(roomSummary.displayName)
                .avatarUrl(roomSummary.avatarUrl)
                .showHighlighted(showHighlighted)
                .unreadCount(unreadCount)
                .listener { onRoomSelected(roomSummary) }
    }
}
