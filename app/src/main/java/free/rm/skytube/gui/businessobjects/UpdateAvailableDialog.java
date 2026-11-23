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
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import free.rm.skytube.R;
import free.rm.skytube.app.SkyTubeApp;
import free.rm.skytube.databinding.DialogUpdateAvailableBinding;

/**
 * Dialog shown when a newer HiTube release is available. It shows the new
 * version number and the release notes (bug fixes and new features) from
 * the GitHub release, and offers to open the release page to download it.
 *
 * Tapping "Later" remembers this version as skipped, so the dialog does
 * not nag again for it — a newer version later will still prompt.
 */
public class UpdateAvailableDialog {

	/**
	 * Show the update dialog for the given release.
	 *
	 * @param activity      Activity to show the dialog in.
	 * @param latestVersion The new version number (without a leading "v").
	 * @param releaseNotes  The release notes text (may be empty).
	 * @param releaseUrl    The release page URL opened by the Download button.
	 */
	public static void show(@NonNull Activity activity, @NonNull String latestVersion,
							@NonNull String releaseNotes, @NonNull String releaseUrl) {
		final DialogUpdateAvailableBinding binding =
				DialogUpdateAvailableBinding.inflate(LayoutInflater.from(activity));
		binding.updateAvailableMessage.setText(String.format(
				activity.getString(R.string.update_available_message), latestVersion));
		binding.updateAvailableNotes.setText(
				releaseNotes == null || releaseNotes.isEmpty()
						? activity.getString(R.string.update_notes_empty)
						: releaseNotes);

		new AlertDialog.Builder(activity)
				.setTitle(String.format(activity.getString(R.string.update_available_title), latestVersion))
				.setView(binding.getRoot())
				.setPositiveButton(R.string.update_download, (dialog, which) ->
						openReleasePage(activity, releaseUrl))
				.setNegativeButton(R.string.later, (dialog, which) ->
						SkyTubeApp.getSettings().setSkippedUpdateVersion(latestVersion))
				.show();
	}

	private static void openReleasePage(Activity activity, String releaseUrl) {
		Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl));
		activity.startActivity(intent);
	}
}
