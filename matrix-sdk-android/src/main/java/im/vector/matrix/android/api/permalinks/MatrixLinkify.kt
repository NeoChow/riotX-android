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

package im.vector.matrix.android.api.permalinks

import android.text.Spannable
import im.vector.matrix.android.api.MatrixPatterns

/**
 *  MatrixLinkify take a piece of text and turns all of the
 *  matrix patterns matches in the text into clickable links.
 */
object MatrixLinkify {

    /**
     * Find the matrix spans i.e matrix id , user id ... to display them as URL.
     *
     * @param spannable the text in which the matrix items has to be clickable.
     */
    fun addLinks(spannable: Spannable?, callback: MatrixPermalinkSpan.Callback?): Boolean {
        // sanity checks
        if (spannable.isNullOrEmpty()) {
            return false
        }
        val text = spannable.toString()
        var hasMatch = false
        for (index in MatrixPatterns.MATRIX_PATTERNS.indices) {
            val pattern = MatrixPatterns.MATRIX_PATTERNS[index]
            val matcher = pattern.matcher(spannable)
            while (matcher.find()) {
                hasMatch = true
                val startPos = matcher.start(0)
                if (startPos == 0 || text[startPos - 1] != '/') {
                    val endPos = matcher.end(0)
                    val url = text.substring(matcher.start(0), matcher.end(0))
                    val span = MatrixPermalinkSpan(url, callback)
                    spannable.setSpan(span, startPos, endPos, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        return hasMatch
    }
    
}