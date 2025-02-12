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

package im.vector.matrix.android.internal.crypto.model.rest

class SendToDeviceBody {

    // `Any` should implement SendToDeviceObject, but we cannot use interface here because of Gson serialization
    /**
     * The messages to send. A map from user ID, to a map from device ID to message body.
     * The device ID may also be *, meaning all known devices for the user.
     */
    var messages: Map<String, Map<String, Any>>? = null
}