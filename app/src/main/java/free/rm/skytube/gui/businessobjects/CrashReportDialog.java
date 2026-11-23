/*
 * HiTube Legacy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation (version 3 of the License).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package free.rm.skytube.gui.businessobjects;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import free.rm.skytube.R;
import free.rm.skytube.app.CrashHandler;
import free.rm.skytube.databinding.DialogCrashReportBinding;

/**
 * Dialog shown on app startup when the previous run ended in a crash (force
 * close). It shows the saved crash log, lets the user copy it, and points
 * them to the GitHub issue page so the crash can be reported with a log
 * attached — instead of a crash report with no log at all.
 */
public class CrashReportDialog {

	/** URL of the page where users file a new issue (with the crash log pasted in). */
	private static final String NEW_ISSUE_URL = "https://github.com/AraZharr/hitube-legacy/issues/new";

	private final Activity activity;

	public CrashReportDialog(@NonNull Activity activity) {
		this.activity = activity;
	}

	/**
	 * Show the crash report dialog, but only if a crash log from a previous
	 * run is waiting. Call once from the main activity's onCreate().
	 */
	public static void showIfNeeded(@NonNull Activity activity) {
		if (CrashHandler.hasCrashLog(activity)) {
			new CrashReportDialog(activity).show();
		}
	}

	public void show() {
		final String crashLog = CrashHandler.readCrashLog(activity);
		if (crashLog.isEmpty()) {
			return;
		}
		final DialogCrashReportBinding binding =
				DialogCrashReportBinding.inflate(LayoutInflater.from(activity));
		binding.crashReportLog.setText(crashLog);

		new AlertDialog.Builder(activity)
				.setTitle(R.string.crash_report_title)
				.setView(binding.getRoot())
				.setPositiveButton(R.string.crash_report_copy, (dialog, which) -> {
					copyLogToClipboard(crashLog);
					CrashHandler.deleteCrashLog(activity);
				})
				.setNeutralButton(R.string.crash_report_github, (dialog, which) -> {
					copyLogToClipboard(crashLog);
					openNewIssuePage();
					CrashHandler.deleteCrashLog(activity);
				})
				.setNegativeButton(R.string.crash_report_close, (dialog, which) ->
						CrashHandler.deleteCrashLog(activity))
				.show();
	}

	private void copyLogToClipboard(String crashLog) {
		ClipboardManager clipboard = ContextCompat.getSystemService(activity, ClipboardManager.class);
		if (clipboard != null) {
			clipboard.setPrimaryClip(ClipData.newPlainText("HiTube crash log", crashLog));
			Toast.makeText(activity, R.string.crash_report_copied, Toast.LENGTH_SHORT).show();
		}
	}

	private void openNewIssuePage() {
		Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(NEW_ISSUE_URL));
		activity.startActivity(intent);
	}
}
