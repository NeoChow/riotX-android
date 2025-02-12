/*
 *
 *  * Copyright 2019 New Vector Ltd
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package im.vector.matrix.android.api.session.user

import androidx.lifecycle.LiveData
import im.vector.matrix.android.api.session.user.model.User

/**
 * This interface defines methods to get users. It's implemented at the session level.
 */
interface UserService {

    /**
     * Get a user from a userId
     * @param userId the userId to look for.
     * @return a user with userId or null
     */
    fun getUser(userId: String): User?

    /**
     * Observe a live user from a userId
     * @param userId the userId to look for.
     * @return a Livedata of user with userId
     */
    fun observeUser(userId: String): LiveData<User?>

}