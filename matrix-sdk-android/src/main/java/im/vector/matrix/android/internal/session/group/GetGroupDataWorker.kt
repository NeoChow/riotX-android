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

package im.vector.matrix.android.internal.session.group

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import arrow.core.Try
import com.squareup.moshi.JsonClass
import im.vector.matrix.android.internal.di.MatrixKoinComponent
import im.vector.matrix.android.internal.util.WorkerParamsFactory
import org.koin.standalone.inject

internal class GetGroupDataWorker(context: Context,
                                  workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters), MatrixKoinComponent {

    @JsonClass(generateAdapter = true)
    internal data class Params(
            val groupIds: List<String>
    )

    private val getGroupDataTask by inject<GetGroupDataTask>()

    override suspend fun doWork(): Result {
        val params = WorkerParamsFactory.fromData<Params>(inputData)
                     ?: return Result.failure()

        val results = params.groupIds.map { groupId ->
            fetchGroupData(groupId)
        }
        val isSuccessful = results.none { it.isFailure() }
        return if (isSuccessful) Result.success() else Result.retry()
    }

    private suspend fun fetchGroupData(groupId: String): Try<Unit> {
        return getGroupDataTask.execute(GetGroupDataTask.Params(groupId))
    }

}