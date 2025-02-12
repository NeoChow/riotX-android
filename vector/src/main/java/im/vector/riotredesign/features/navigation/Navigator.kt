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

package im.vector.riotredesign.features.navigation

import android.content.Context
import im.vector.matrix.android.api.session.room.model.roomdirectory.PublicRoom

interface Navigator {

    fun openRoom(context: Context, roomId: String, eventId: String? = null)

    fun openNotJoinedRoom(context: Context, roomIdOrAlias: String, eventId: String? = null)

    fun openRoomPreview(publicRoom: PublicRoom, context: Context)

    fun openRoomDirectory(context: Context)

    fun openSettings(context: Context)

    fun openDebug(context: Context)

    fun openKeysBackupSetup(context: Context, showManualExport: Boolean)

    fun openKeysBackupManager(context: Context)

    fun openGroupDetail(groupId: String, context: Context)

    fun openUserDetail(userId: String, context: Context)
}