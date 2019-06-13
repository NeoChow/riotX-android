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

package im.vector.matrix.android.internal.session.room

import com.zhuinden.monarchy.Monarchy
import im.vector.matrix.android.api.session.room.Room
import im.vector.matrix.android.internal.database.mapper.RoomSummaryMapper
import im.vector.matrix.android.internal.session.room.relation.DefaultRelationService
import im.vector.matrix.android.internal.session.room.relation.FindReactionEventForUndoTask
import im.vector.matrix.android.internal.session.room.relation.UpdateQuickReactionTask
import im.vector.matrix.android.internal.session.room.membership.DefaultMembershipService
import im.vector.matrix.android.internal.session.room.membership.LoadRoomMembersTask
import im.vector.matrix.android.internal.session.room.membership.SenderRoomMemberExtractor
import im.vector.matrix.android.internal.session.room.membership.joining.InviteTask
import im.vector.matrix.android.internal.session.room.membership.joining.JoinRoomTask
import im.vector.matrix.android.internal.session.room.membership.leaving.LeaveRoomTask
import im.vector.matrix.android.internal.session.room.read.DefaultReadService
import im.vector.matrix.android.internal.session.room.read.SetReadMarkersTask
import im.vector.matrix.android.internal.session.room.send.DefaultSendService
import im.vector.matrix.android.internal.session.room.send.LocalEchoEventFactory
import im.vector.matrix.android.internal.session.room.state.DefaultStateService
import im.vector.matrix.android.internal.session.room.state.SendStateTask
import im.vector.matrix.android.internal.session.room.timeline.DefaultTimelineService
import im.vector.matrix.android.internal.session.room.timeline.GetContextOfEventTask
import im.vector.matrix.android.internal.session.room.timeline.InMemoryTimelineEventFactory
import im.vector.matrix.android.internal.session.room.timeline.PaginationTask
import im.vector.matrix.android.internal.task.TaskExecutor

internal class RoomFactory(private val monarchy: Monarchy,
                           private val eventFactory: LocalEchoEventFactory,
                           private val roomSummaryMapper: RoomSummaryMapper,
                           private val taskExecutor: TaskExecutor,
                           private val loadRoomMembersTask: LoadRoomMembersTask,
                           private val inviteTask: InviteTask,
                           private val sendStateTask: SendStateTask,
                           private val paginationTask: PaginationTask,
                           private val contextOfEventTask: GetContextOfEventTask,
                           private val setReadMarkersTask: SetReadMarkersTask,
                           private val findReactionEventForUndoTask: FindReactionEventForUndoTask,
                           private val updateQuickReactionTask: UpdateQuickReactionTask,
                           private val joinRoomTask: JoinRoomTask,
                           private val leaveRoomTask: LeaveRoomTask) {

    fun instantiate(roomId: String): Room {
        val timelineEventFactory = InMemoryTimelineEventFactory(SenderRoomMemberExtractor(), EventRelationExtractor())
        val timelineService = DefaultTimelineService(roomId, monarchy, taskExecutor, timelineEventFactory, contextOfEventTask, paginationTask)
        val sendService = DefaultSendService(roomId, eventFactory, monarchy)
        val reactionService = DefaultRelationService(roomId, eventFactory, findReactionEventForUndoTask, updateQuickReactionTask, monarchy, taskExecutor)
        val stateService = DefaultStateService(roomId, taskExecutor, sendStateTask)
        val roomMembersService = DefaultMembershipService(roomId, monarchy, taskExecutor, loadRoomMembersTask, inviteTask, joinRoomTask, leaveRoomTask)
        val readService = DefaultReadService(roomId, monarchy, taskExecutor, setReadMarkersTask)

        return DefaultRoom(
                roomId,
                monarchy,
                roomSummaryMapper,
                timelineService,
                sendService,
                stateService,
                readService,
                reactionService,
                roomMembersService
        )
    }

}