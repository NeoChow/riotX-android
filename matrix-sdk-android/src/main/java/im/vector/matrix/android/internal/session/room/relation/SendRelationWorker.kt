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
package im.vector.matrix.android.internal.session.room.relation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.squareup.moshi.JsonClass
import im.vector.matrix.android.api.failure.Failure
import im.vector.matrix.android.api.session.events.model.Event
import im.vector.matrix.android.api.session.events.model.toModel
import im.vector.matrix.android.api.session.room.model.relation.ReactionContent
import im.vector.matrix.android.api.session.room.model.relation.ReactionInfo
import im.vector.matrix.android.internal.di.MatrixKoinComponent
import im.vector.matrix.android.internal.network.executeRequest
import im.vector.matrix.android.internal.session.room.RoomAPI
import im.vector.matrix.android.internal.session.room.send.SendResponse
import im.vector.matrix.android.internal.util.WorkerParamsFactory
import org.koin.standalone.inject

class SendRelationWorker(context: Context, params: WorkerParameters)
    : CoroutineWorker(context, params), MatrixKoinComponent {


    @JsonClass(generateAdapter = true)
    internal data class Params(
            val roomId: String,
            val event: Event,
            val relationType: String? = null
    )

    private val roomAPI by inject<RoomAPI>()

    override suspend fun doWork(): Result {
        val params = WorkerParamsFactory.fromData<Params>(inputData)
                ?: return Result.failure()

        val localEvent = params.event
        if (localEvent.eventId == null) {
            return Result.failure()
        }
        val relationContent = localEvent.content.toModel<ReactionContent>()
                ?: return Result.failure()
        val relatedEventId = relationContent.relatesTo?.eventId ?: return Result.failure()
        val relationType = (relationContent.relatesTo as? ReactionInfo)?.type ?: params.relationType
        ?: return Result.failure()

        val result = executeRequest<SendResponse> {
            apiCall = roomAPI.sendRelation(
                    roomId = params.roomId,
                    parent_id = relatedEventId,
                    relationType = relationType,
                    eventType = localEvent.type,
                    content = localEvent.content
            )
        }
        return result.fold({
            when (it) {
                is Failure.NetworkConnection -> Result.retry()
                else -> {
                    //TODO mark as failed to send?
                    //always return success, or the chain will be stuck for ever!
                    Result.success()
                }
            }
        }, { Result.success() })
    }
}