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

package im.vector.matrix.android.internal.session.room.state

import arrow.core.Try
import im.vector.matrix.android.internal.network.executeRequest
import im.vector.matrix.android.internal.session.room.RoomAPI
import im.vector.matrix.android.internal.task.Task

internal interface SendStateTask : Task<SendStateTask.Params, Unit> {
    data class Params(
            val roomId: String,
            val eventType: String,
            val body: Map<String, String>
    )
}

internal class DefaultSendStateTask(private val roomAPI: RoomAPI) : SendStateTask {
    override suspend fun execute(params: SendStateTask.Params): Try<Unit> {
        return executeRequest {
            apiCall = roomAPI.sendStateEvent(params.roomId, params.eventType, params.body)
        }
    }
}
