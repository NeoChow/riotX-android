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
package im.vector.riotredesign.features.crypto.keysbackup.settings

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.airbnb.mvrx.activityViewModel
import com.airbnb.mvrx.withState
import im.vector.riotredesign.R
import im.vector.riotredesign.core.platform.VectorBaseFragment
import im.vector.riotredesign.features.crypto.keysbackup.KeysBackupModule
import im.vector.riotredesign.features.crypto.keysbackup.restore.KeysBackupRestoreActivity
import im.vector.riotredesign.features.crypto.keysbackup.setup.KeysBackupSetupActivity
import kotlinx.android.synthetic.main.fragment_keys_backup_settings.*
import org.koin.android.ext.android.inject
import org.koin.android.scope.ext.android.bindScope
import org.koin.android.scope.ext.android.getOrCreateScope

class KeysBackupSettingsFragment : VectorBaseFragment(),
        KeysBackupSettingsRecyclerViewController.Listener {

    companion object {
        fun newInstance() = KeysBackupSettingsFragment()
    }

    override fun getLayoutResId() = R.layout.fragment_keys_backup_settings

    private val keysBackupSettingsRecyclerViewController: KeysBackupSettingsRecyclerViewController by inject()

    private val viewModel: KeysBackupSettingsViewModel by activityViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bindScope(getOrCreateScope(KeysBackupModule.KEYS_BACKUP_SCOPE))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        keysBackupSettingsRecyclerView.setController(keysBackupSettingsRecyclerViewController)

        keysBackupSettingsRecyclerViewController.listener = this
    }

    override fun invalidate() = withState(viewModel) { state ->
        keysBackupSettingsRecyclerViewController.setData(state)
    }

    override fun didSelectSetupMessageRecovery() {
        context?.let {
            startActivity(KeysBackupSetupActivity.intent(it, false))
        }
    }

    override fun didSelectRestoreMessageRecovery() {
        context?.let {
            startActivity(KeysBackupRestoreActivity.intent(it))
        }
    }

    override fun didSelectDeleteSetupMessageRecovery() {
        activity?.let {
            AlertDialog.Builder(it)
                    .setTitle(R.string.keys_backup_settings_delete_confirm_title)
                    .setMessage(R.string.keys_backup_settings_delete_confirm_message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.keys_backup_settings_delete_confirm_title) { _, _ ->
                        viewModel.deleteCurrentBackup()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .setCancelable(true)
                    .show()
        }
    }

    override fun loadTrustData() {
        viewModel.getKeysBackupTrust()
    }

    override fun loadKeysBackupState() {
        viewModel.init()
    }
}