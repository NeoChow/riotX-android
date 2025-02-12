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
package im.vector.matrix.android.internal.session.pushers

import im.vector.matrix.android.api.pushrules.rest.PushRule
import im.vector.matrix.android.api.pushrules.rest.GetPushRulesResponse
import im.vector.matrix.android.internal.network.NetworkConstants
import retrofit2.Call
import retrofit2.http.*


internal interface PushRulesApi {
    /**
     * Get all push rules
     */
    @GET(NetworkConstants.URI_API_PREFIX_PATH_R0 + "pushrules/")
    fun getAllRules(): Call<GetPushRulesResponse>

    /**
     * Update the ruleID enable status
     *
     * @param kind   the notification kind (sender, room...)
     * @param ruleId the ruleId
     * @param enable the new enable status
     */
    @PUT(NetworkConstants.URI_API_PREFIX_PATH_R0 + "pushrules/global/{kind}/{ruleId}/enabled")
    fun updateEnableRuleStatus(@Path("kind") kind: String,
                               @Path("ruleId") ruleId: String,
                               @Body enable: Boolean?)
            : Call<Unit>


    /**
     * Update the ruleID action
     *
     * @param kind    the notification kind (sender, room...)
     * @param ruleId  the ruleId
     * @param actions the actions
     */
    @PUT(NetworkConstants.URI_API_PREFIX_PATH_R0 + "pushrules/global/{kind}/{ruleId}/actions")
    fun updateRuleActions(@Path("kind") kind: String,
                          @Path("ruleId") ruleId: String,
                          @Body actions: Any)
            : Call<Unit>


    /**
     * Delete a rule
     *
     * @param kind   the notification kind (sender, room...)
     * @param ruleId the ruleId
     */
    @DELETE(NetworkConstants.URI_API_PREFIX_PATH_R0 + "pushrules/global/{kind}/{ruleId}")
    fun deleteRule(@Path("kind") kind: String,
                   @Path("ruleId") ruleId: String)
            : Call<Unit>

    /**
     * Add the ruleID enable status
     *
     * @param kind   the notification kind (sender, room...)
     * @param ruleId the ruleId.
     * @param rule   the rule to add.
     */
    @PUT(NetworkConstants.URI_API_PREFIX_PATH_R0 + "pushrules/global/{kind}/{ruleId}")
    fun addRule(@Path("kind") kind: String,
                @Path("ruleId") ruleId: String,
                @Body rule: PushRule)
            : Call<Unit>
}