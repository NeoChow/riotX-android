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

package im.vector.riotredesign.features.roomdirectory.roompreview

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.appcompat.widget.Toolbar
import im.vector.matrix.android.api.session.room.model.roomdirectory.PublicRoom
import im.vector.riotredesign.R
import im.vector.riotredesign.core.extensions.addFragment
import im.vector.riotredesign.core.platform.ToolbarConfigurable
import im.vector.riotredesign.core.platform.VectorBaseActivity
import im.vector.riotredesign.features.roomdirectory.RoomDirectoryModule
import kotlinx.android.parcel.Parcelize
import org.koin.android.scope.ext.android.bindScope
import org.koin.android.scope.ext.android.getOrCreateScope

@Parcelize
data class RoomPreviewData(
        val roomId: String,
        val roomName: String?,
        val topic: String?,
        val worldReadable: Boolean,
        val avatarUrl: String?
) : Parcelable


class RoomPreviewActivity : VectorBaseActivity(), ToolbarConfigurable {

    companion object {
        private const val ARG = "ARG"

        fun getIntent(context: Context, publicRoom: PublicRoom): Intent {
            return Intent(context, RoomPreviewActivity::class.java).apply {
                putExtra(ARG, RoomPreviewData(
                        roomId = publicRoom.roomId,
                        roomName = publicRoom.name,
                        topic = publicRoom.topic,
                        worldReadable = publicRoom.worldReadable,
                        avatarUrl = publicRoom.avatarUrl
                ))
            }
        }
    }

    override fun getLayoutRes() = R.layout.activity_simple

    override fun configure(toolbar: Toolbar) {
        configureToolbar(toolbar)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bindScope(getOrCreateScope(RoomDirectoryModule.ROOM_DIRECTORY_SCOPE))
    }

    override fun initUiAndData() {
        if (isFirstCreation()) {
            val args = intent.getParcelableExtra<RoomPreviewData>(ARG)

            if (args.worldReadable) {
                // TODO Room preview: Note: M does not recommend to use /events anymore, so for now we just display the room preview
                // TODO the same way if it was not world readable
                addFragment(RoomPreviewNoPreviewFragment.newInstance(args), R.id.simpleFragmentContainer)
            } else {
                addFragment(RoomPreviewNoPreviewFragment.newInstance(args), R.id.simpleFragmentContainer)
            }
        }
    }

}