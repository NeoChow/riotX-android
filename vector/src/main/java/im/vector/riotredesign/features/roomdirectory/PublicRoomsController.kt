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

package im.vector.riotredesign.features.roomdirectory

import com.airbnb.epoxy.TypedEpoxyController
import com.airbnb.epoxy.VisibilityState
import com.airbnb.mvrx.Fail
import com.airbnb.mvrx.Incomplete
import com.airbnb.mvrx.Success
import im.vector.matrix.android.api.session.room.model.roomdirectory.PublicRoom
import im.vector.riotredesign.R
import im.vector.riotredesign.core.epoxy.errorWithRetryItem
import im.vector.riotredesign.core.epoxy.loadingItem
import im.vector.riotredesign.core.epoxy.noResultItem
import im.vector.riotredesign.core.error.ErrorFormatter
import im.vector.riotredesign.core.resources.StringProvider

class PublicRoomsController(private val stringProvider: StringProvider,
                            private val errorFormatter: ErrorFormatter) : TypedEpoxyController<PublicRoomsViewState>() {

    var callback: Callback? = null

    override fun buildModels(viewState: PublicRoomsViewState) {
        val publicRooms = viewState.publicRooms

        if (publicRooms.isEmpty()
                && viewState.asyncPublicRoomsRequest is Success) {
            // No result
            noResultItem {
                id("noResult")
                text(stringProvider.getString(R.string.no_result_placeholder))
            }
        } else {
            publicRooms.forEach {
                buildPublicRoom(it, viewState)
            }

            if ((viewState.hasMore && viewState.asyncPublicRoomsRequest is Success)
                    || viewState.asyncPublicRoomsRequest is Incomplete) {
                loadingItem {
                    // Change id to avoid list to scroll automatically when first results are displayed
                    if (publicRooms.isEmpty()) {
                        id("loading")
                    } else {
                        id("loadMore")
                    }
                    onVisibilityStateChanged { _, _, visibilityState ->
                        if (visibilityState == VisibilityState.VISIBLE) {
                            callback?.loadMore()
                        }
                    }
                }
            }
        }

        if (viewState.asyncPublicRoomsRequest is Fail) {
            errorWithRetryItem {
                id("error")
                text(errorFormatter.toHumanReadable(viewState.asyncPublicRoomsRequest.error))
                listener { callback?.loadMore() }
            }
        }
    }

    private fun buildPublicRoom(publicRoom: PublicRoom, viewState: PublicRoomsViewState) {
        publicRoomItem {
            id(publicRoom.roomId)
            roomId(publicRoom.roomId)
            avatarUrl(publicRoom.avatarUrl)
            roomName(publicRoom.name)
            roomAlias(publicRoom.canonicalAlias)
            roomTopic(publicRoom.topic)
            nbOfMembers(publicRoom.numJoinedMembers)

            val joinState = when {
                viewState.joinedRoomsIds.contains(publicRoom.roomId)       -> JoinState.JOINED
                viewState.joiningRoomsIds.contains(publicRoom.roomId)      -> JoinState.JOINING
                viewState.joiningErrorRoomsIds.contains(publicRoom.roomId) -> JoinState.JOINING_ERROR
                else                                                       -> JoinState.NOT_JOINED
            }

            joinState(joinState)

            joinListener {
                callback?.onPublicRoomJoin(publicRoom)
            }
            globalListener {
                callback?.onPublicRoomClicked(publicRoom, joinState)
            }
        }
    }

    interface Callback {
        fun onPublicRoomClicked(publicRoom: PublicRoom, joinState: JoinState)
        fun onPublicRoomJoin(publicRoom: PublicRoom)
        fun loadMore()
    }

}
