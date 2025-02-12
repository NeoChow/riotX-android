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

package im.vector.riotredesign.features.home.room.list

import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.airbnb.epoxy.EpoxyAttribute
import com.airbnb.epoxy.EpoxyModelClass
import im.vector.riotredesign.R
import im.vector.riotredesign.core.epoxy.VectorEpoxyHolder
import im.vector.riotredesign.core.epoxy.VectorEpoxyModel
import im.vector.riotredesign.features.themes.ThemeUtils

@EpoxyModelClass(layout = R.layout.item_room_category)
abstract class RoomCategoryItem : VectorEpoxyModel<RoomCategoryItem.Holder>() {

    @EpoxyAttribute lateinit var title: CharSequence
    @EpoxyAttribute var expanded: Boolean = false
    @EpoxyAttribute var unreadCount: Int = 0
    @EpoxyAttribute var showHighlighted: Boolean = false
    @EpoxyAttribute var listener: (() -> Unit)? = null

    override fun bind(holder: Holder) {
        val tintColor = ThemeUtils.getColor(holder.rootView.context, R.attr.riotx_text_secondary)
        val expandedArrowDrawableRes = if (expanded) R.drawable.ic_expand_more_white else R.drawable.ic_expand_less_white
        val expandedArrowDrawable = ContextCompat.getDrawable(holder.rootView.context, expandedArrowDrawableRes)?.also {
            DrawableCompat.setTint(it, tintColor)
        }
        holder.unreadCounterBadgeView.render(UnreadCounterBadgeView.State(unreadCount, showHighlighted))
        holder.titleView.setCompoundDrawablesWithIntrinsicBounds(expandedArrowDrawable, null, null, null)
        holder.titleView.text = title
        holder.rootView.setOnClickListener { listener?.invoke() }
    }


    class Holder : VectorEpoxyHolder() {
        val unreadCounterBadgeView by bind<UnreadCounterBadgeView>(R.id.roomCategoryUnreadCounterBadgeView)
        val titleView by bind<TextView>(R.id.roomCategoryTitleView)
        val rootView by bind<ViewGroup>(R.id.roomCategoryRootView)
    }

}

