/*
 * Copyright 2018 New Vector Ltd
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

package im.vector.riotredesign.features.rageshake

import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.core.view.isVisible
import butterknife.OnCheckedChanged
import butterknife.OnTextChanged
import im.vector.riotredesign.R
import im.vector.riotredesign.core.platform.VectorBaseActivity
import kotlinx.android.synthetic.main.activity_bug_report.*
import timber.log.Timber

/**
 * Form to send a bug report
 */
class BugReportActivity : VectorBaseActivity() {

    override fun getLayoutRes() = R.layout.activity_bug_report

    override fun initUiAndData() {
        configureToolbar(bugReportToolbar)

        if (BugReporter.screenshot != null) {
            bug_report_screenshot_preview.setImageBitmap(BugReporter.screenshot)
        } else {
            bug_report_screenshot_preview.isVisible = false
            bug_report_button_include_screenshot.isChecked = false
            bug_report_button_include_screenshot.isEnabled = false
        }
    }

    override fun getMenuRes() = R.menu.bug_report

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.ic_action_send_bug_report)?.let {
            val isValid = bug_report_edit_text.text.toString().trim().length > 10
                    && !bug_report_mask_view.isVisible

            it.isEnabled = isValid
            it.icon.alpha = if (isValid) 255 else 100
        }

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.ic_action_send_bug_report -> {
                sendBugReport()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }


    /**
     * Send the bug report
     */
    private fun sendBugReport() {
        bug_report_scrollview.alpha = 0.3f
        bug_report_mask_view.isVisible = true

        invalidateOptionsMenu()

        bug_report_progress_text_view.isVisible = true
        bug_report_progress_text_view.text = getString(R.string.send_bug_report_progress, "0")

        bug_report_progress_view.isVisible = true
        bug_report_progress_view.progress = 0

        BugReporter.sendBugReport(this,
                bug_report_button_include_logs.isChecked,
                bug_report_button_include_crash_logs.isChecked,
                bug_report_button_include_screenshot.isChecked,
                bug_report_edit_text.text.toString(),
                object : BugReporter.IMXBugReportListener {
                    override fun onUploadFailed(reason: String?) {
                        try {
                            if (!TextUtils.isEmpty(reason)) {
                                Toast.makeText(this@BugReportActivity,
                                        getString(R.string.send_bug_report_failed, reason), Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "## onUploadFailed() : failed to display the toast " + e.message)
                        }

                        bug_report_mask_view.isVisible = false
                        bug_report_progress_view.isVisible = false
                        bug_report_progress_text_view.isVisible = false
                        bug_report_scrollview.alpha = 1.0f

                        invalidateOptionsMenu()
                    }

                    override fun onUploadCancelled() {
                        onUploadFailed(null)
                    }

                    override fun onProgress(progress: Int) {
                        val myProgress = progress.coerceIn(0, 100)

                        bug_report_progress_view.progress = myProgress
                        bug_report_progress_text_view.text = getString(R.string.send_bug_report_progress, "$myProgress")
                    }

                    override fun onUploadSucceed() {
                        try {
                            Toast.makeText(this@BugReportActivity, R.string.send_bug_report_sent, Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            Timber.e(e, "## onUploadSucceed() : failed to dismiss the toast " + e.message)
                        }

                        try {
                            finish()
                        } catch (e: Exception) {
                            Timber.e(e, "## onUploadSucceed() : failed to dismiss the dialog " + e.message)
                        }
                    }
                })
    }

    /* ==========================================================================================
     * UI Event
     * ========================================================================================== */

    @OnTextChanged(R.id.bug_report_edit_text)
    internal fun textChanged() {
        invalidateOptionsMenu()
    }

    @OnCheckedChanged(R.id.bug_report_button_include_screenshot)
    internal fun onSendScreenshotChanged() {
        bug_report_screenshot_preview.isVisible = bug_report_button_include_screenshot.isChecked && BugReporter.screenshot != null
    }

    override fun onBackPressed() {
        // Ensure there is no crash status remaining, which will be sent later on by mistake
        BugReporter.deleteCrashFile(this)

        super.onBackPressed()
    }
}
