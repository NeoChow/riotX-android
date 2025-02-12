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

package im.vector.riotredesign.features.roomdirectory.createroom

import com.airbnb.epoxy.TypedEpoxyController
import com.airbnb.mvrx.Fail
import com.airbnb.mvrx.Loading
import com.airbnb.mvrx.Success
import com.airbnb.mvrx.Uninitialized
import im.vector.riotredesign.R
import im.vector.riotredesign.core.epoxy.errorWithRetryItem
import im.vector.riotredesign.core.epoxy.loadingItem
import im.vector.riotredesign.core.error.ErrorFormatter
import im.vector.riotredesign.core.resources.StringProvider
import im.vector.riotredesign.features.form.formEditTextItem
import im.vector.riotredesign.features.form.formSwitchItem

class CreateRoomController(private val stringProvider: StringProvider,
                           private val errorFormatter: ErrorFormatter
) : TypedEpoxyController<CreateRoomViewState>() {

    var listener: Listener? = null

    var index = 0

    override fun buildModels(viewState: CreateRoomViewState) {
        val asyncCreateRoom = viewState.asyncCreateRoomRequest

        when (asyncCreateRoom) {
            is Success       -> {
                // Nothing to display, the screen will be closed
            }
            is Loading       -> {
                // display the form
                buildForm(viewState, false)
                loadingItem {
                    id("loading")
                }
            }
            is Uninitialized -> {
                // display the form
                buildForm(viewState, true)
            }
            is Fail          -> {
                // display the form
                buildForm(viewState, true)
                errorWithRetryItem {
                    id("error")
                    text(errorFormatter.toHumanReadable(asyncCreateRoom.error))
                    listener { listener?.retry() }
                }
            }
        }
    }

    private fun buildForm(viewState: CreateRoomViewState, enableFormElement: Boolean) {
        formEditTextItem {
            id("name")
            enabled(enableFormElement)
            value(viewState.roomName)
            hint(stringProvider.getString(R.string.create_room_name_hint))

            onTextChange { text ->
                listener?.onNameChange(text)
            }
        }
        formSwitchItem {
            id("public")
            enabled(enableFormElement)
            title(stringProvider.getString(R.string.create_room_public_title))
            summary(stringProvider.getString(R.string.create_room_public_description))
            switchChecked(viewState.isPublic)

            listener { value ->
                listener?.setIsPublic(value)
            }
        }
        formSwitchItem {
            id("directory")
            enabled(enableFormElement)
            title(stringProvider.getString(R.string.create_room_directory_title))
            summary(stringProvider.getString(R.string.create_room_directory_description))
            switchChecked(viewState.isInRoomDirectory)

            listener { value ->
                listener?.setIsInRoomDirectory(value)
            }
        }
    }

    interface Listener {
        fun onNameChange(newName: String)
        fun setIsPublic(isPublic: Boolean)
        fun setIsInRoomDirectory(isInRoomDirectory: Boolean)
        fun retry()
    }

}
