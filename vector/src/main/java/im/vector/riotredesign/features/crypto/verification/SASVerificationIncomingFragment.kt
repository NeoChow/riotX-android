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
package im.vector.riotredesign.features.crypto.verification

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import butterknife.BindView
import butterknife.OnClick
import im.vector.matrix.android.api.session.crypto.sas.IncomingSasVerificationTransaction
import im.vector.riotredesign.R
import im.vector.riotredesign.core.platform.VectorBaseFragment
import im.vector.riotredesign.features.home.AvatarRenderer

class SASVerificationIncomingFragment : VectorBaseFragment() {

    companion object {
        fun newInstance() = SASVerificationIncomingFragment()
    }

    @BindView(R.id.sas_incoming_request_user_display_name)
    lateinit var otherUserDisplayNameTextView: TextView

    @BindView(R.id.sas_incoming_request_user_id)
    lateinit var otherUserIdTextView: TextView

    @BindView(R.id.sas_incoming_request_user_device)
    lateinit var otherDeviceTextView: TextView

    @BindView(R.id.sas_incoming_request_user_avatar)
    lateinit var avatarImageView: ImageView

    override fun getLayoutResId() = R.layout.fragment_sas_verification_incoming_request

    private lateinit var viewModel: SasVerificationViewModel

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel = activity?.run {
            ViewModelProviders.of(this).get(SasVerificationViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        otherUserDisplayNameTextView.text = viewModel.otherUser?.displayName ?: viewModel.otherUserId
        otherUserIdTextView.text = viewModel.otherUserId
        otherDeviceTextView.text = viewModel.otherDeviceId

        viewModel.otherUser?.let {
            AvatarRenderer.render(it, avatarImageView)
        } ?: run {
            // Fallback to what we know
            AvatarRenderer.render(null, viewModel.otherUserId ?: "", viewModel.otherUserId, avatarImageView)
        }

        viewModel.transactionState.observe(this, Observer {
            val uxState = (viewModel.transaction as? IncomingSasVerificationTransaction)?.uxState
            when (uxState) {
                IncomingSasVerificationTransaction.UxState.SHOW_ACCEPT            -> {
                    viewModel.loadingLiveEvent.value = null
                }
                IncomingSasVerificationTransaction.UxState.WAIT_FOR_KEY_AGREEMENT -> {
                    viewModel.loadingLiveEvent.value = R.string.sas_waiting_for_partner
                }
                IncomingSasVerificationTransaction.UxState.SHOW_SAS               -> {
                    viewModel.shortCodeReady()
                }
                IncomingSasVerificationTransaction.UxState.CANCELLED_BY_ME,
                IncomingSasVerificationTransaction.UxState.CANCELLED_BY_OTHER     -> {
                    viewModel.loadingLiveEvent.value = null
                    viewModel.navigateCancel()
                }
                else                                                              -> Unit
            }
        })

    }

    @OnClick(R.id.sas_request_continue_button)
    fun didAccept() {
        viewModel.acceptTransaction()
    }

    @OnClick(R.id.sas_request_cancel_button)
    fun didCancel() {
        viewModel.cancelTransaction()
    }
}