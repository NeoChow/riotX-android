/*
 * Copyright 2016 OpenMarket Ltd
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
package im.vector.matrix.android.internal.crypto.model.event

import com.squareup.moshi.JsonClass
import im.vector.matrix.android.internal.di.MoshiProvider

/**
 * Class representing the OLM payload content
 */
@JsonClass(generateAdapter = true)
data class OlmPayloadContent(
        /**
         * The room id
         */
        var room_id: String? = null,

        /**
         * The sender
         */
        var sender: String? = null,

        /**
         * The recipient
         */
        var recipient: String? = null,

        /**
         * the recipient keys
         */
        var recipient_keys: Map<String, String>? = null,

        /**
         * The keys
         */
        var keys: Map<String, String>? = null
) {
    fun toJsonString(): String {
        return MoshiProvider.providesMoshi().adapter(OlmPayloadContent::class.java).toJson(this)
    }

    companion object {
        fun fromJsonString(str: String): OlmPayloadContent {
            return MoshiProvider.providesMoshi().adapter(OlmPayloadContent::class.java).fromJson(str)!!
        }
    }
}


