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

package im.vector.matrix.android.internal.di

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import im.vector.matrix.android.internal.task.TaskExecutor
import im.vector.matrix.android.internal.util.BackgroundDetectionObserver
import im.vector.matrix.android.internal.util.MatrixCoroutineDispatchers
import im.vector.matrix.android.internal.util.StringProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.asCoroutineDispatcher
import org.koin.dsl.module.module


class MatrixModule(private val context: Context) {

    val definition = module {

        single {
            context
        }

        single {
            val THREAD_CRYPTO_NAME = "Crypto_Thread"
            val handlerThread = HandlerThread(THREAD_CRYPTO_NAME)
            handlerThread.start()

            MatrixCoroutineDispatchers(io = Dispatchers.IO,
                    computation = Dispatchers.IO,
                    main = Dispatchers.Main,
                    crypto = Handler(handlerThread.looper).asCoroutineDispatcher("crypto")
            )
        }

        single {
            TaskExecutor(get())
        }
        single {
            StringProvider(context.resources)
        }

        single {
            BackgroundDetectionObserver()
        }

    }
}