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

package im.vector.riotredesign.features.home

import im.vector.matrix.android.api.session.room.model.RoomSummary
import im.vector.riotredesign.core.utils.RxStore
import im.vector.riotredesign.features.home.room.list.RoomListDisplayModeFilter
import im.vector.riotredesign.features.home.room.list.RoomListFragment
import io.reactivex.Observable

class HomeRoomListObservableStore : RxStore<List<RoomSummary>>() {

    fun observeFilteredBy(displayMode: RoomListFragment.DisplayMode): Observable<List<RoomSummary>> {
        return observe()
                .flatMapSingle {
                    Observable.fromIterable(it).filter(RoomListDisplayModeFilter(displayMode)).toList()
                }
    }


}