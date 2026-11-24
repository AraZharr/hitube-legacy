/*
 * SkyTube
 * Copyright (C) 2017  Ramon Mifsud
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

package free.rm.skytube.gui.businessobjects.updates;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.util.Objects;

import free.rm.skytube.BuildConfig;
import free.rm.skytube.R;
import free.rm.skytube.app.SkyTubeApp;
import free.rm.skytube.app.Utils;
import free.rm.skytube.businessobjects.AsyncTaskParallel;
import free.rm.skytube.businessobjects.Logger;
import free.rm.skytube.gui.businessobjects.UpdateAvailableDialog;

/**
 * A task that will check if any HiTube updates have been published. If there are, then the
 * user will be notified with the new version number and its release notes.
 *
 * Automatic checks (from MainActivity) run at most once a day and only when the device is
 * online; a manual check (from Settings) always runs right away. Any failure fails silently.
 */
public class UpdatesCheckerTask extends AsyncTaskParallel<Void, Void, UpdatesChecker> {

	/** Minimum time between two automatic update checks. */
	private static final long AUTO_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L;

	/** If set to true, it will display the "HiTube is up to date" message.  */
	private final boolean displayUpToDateMessage;
	private final Context context;
	private boolean showReleaseNotes;
	private String currentVersionNumber;
	/** Release notes of the version actually installed (null when GitHub
	 * has no release for it — then no "what's new" dialog is shown). */
	private String installedVersionReleaseNotes;

	/**
	 * Constructor.
	 *
	 * @param context                   Context.
	 * @param displayUpToDateMessage    If set to true, it will display the "SkyTube is up to date"
	 *                                  message; otherwise the message will not be displayed.
	 */
	public UpdatesCheckerTask(Context context, boolean displayUpToDateMessage) {
		this.context = context;
		this.displayUpToDateMessage = displayUpToDateMessage;
	}

	@Override
	protected UpdatesChecker doInBackground(Void... params) {
		currentVersionNumber = getCurrentVerNumber();

		// Automatic checks are throttled to once a day and need a connection;
		// a manual check always runs. Returning null means "nothing to do".
		if (!displayUpToDateMessage && !shouldCheckNow()) {
			return null;
		}

		String displayedReleaseNotesVersion = SkyTubeApp.getSettings().getDisplayedReleaseNoteTag();
		showReleaseNotes = !Objects.equals(currentVersionNumber, displayedReleaseNotesVersion) && !displayUpToDateMessage;
		Logger.d(this, "Current Version Number: %s, last displayed: %s, show release notes: %s", currentVersionNumber, displayedReleaseNotesVersion, showReleaseNotes);
		UpdatesChecker updatesChecker = new UpdatesChecker(currentVersionNumber);
		updatesChecker.checkForUpdates();
		if (showReleaseNotes && !updatesChecker.isUpdateAvailable()) {
			// The "what's new" dialog must show the notes of the version
			// actually installed, not the latest release's notes (those
			// belong to an older version after an upgrade). When there is
			// an update available the update dialog takes precedence, so
			// no second request is made.
			installedVersionReleaseNotes =
					updatesChecker.fetchReleaseNotesForVersion(currentVersionNumber);
		}
		SkyTubeApp.getSettings().setLastUpdateCheckTime(System.currentTimeMillis());
		return updatesChecker;
	}

	/**
	 * @return True if an automatic check should run now: the device is online
	 *         and the last check was more than a day ago (or never ran).
	 */
	private boolean shouldCheckNow() {
		if (!SkyTubeApp.isConnected(context)) {
			return false;
		}
		long lastCheck = SkyTubeApp.getSettings().getLastUpdateCheckTime();
		return System.currentTimeMillis() - lastCheck > AUTO_CHECK_INTERVAL_MS;
	}

	@Override
	protected void onPostExecute(final UpdatesChecker updatesChecker) {
		// Null means the automatic check was throttled: nothing to do.
		if (updatesChecker == null) {
			return;
		}

		// if there is an update available...
		if (updatesChecker.isUpdateAvailable()) {
			// ...unless the user already tapped "Later" for this version
			// (a manual check still shows it, so the user can change their mind).
			String latestVersion = updatesChecker.getLatestApkVersion();
			if (displayUpToDateMessage
					|| !latestVersion.equals(SkyTubeApp.getSettings().getSkippedUpdateVersion())) {
				if (context instanceof Activity) {
					UpdateAvailableDialog.show((Activity) context, latestVersion,
							updatesChecker.getReleaseNotes(), updatesChecker.getLatestReleaseUrl());
				}
			}
		} else if (displayUpToDateMessage) {
			// Manual check: say the app is up to date, but stay silent when
			// the check itself failed (no network, GitHub error, ...).
			if (updatesChecker.getFailure() == null) {
				Toast.makeText(context, R.string.update_up_to_date_toast, Toast.LENGTH_LONG).show();
			}
		} else if (showReleaseNotes && installedVersionReleaseNotes != null) {
			// Show the notes of the version actually installed, titled
			// with that version. When GitHub has no release for the
			// installed version (installedVersionReleaseNotes is null),
			// show nothing at all — and do not mark the notes as
			// displayed, so a later check can show them once the release
			// exists.
			new AlertDialog.Builder(context)
					.setTitle(String.format(context.getString(R.string.release_notes), currentVersionNumber))
					.setMessage(installedVersionReleaseNotes)
					.setNeutralButton(R.string.ok, (dialog,button) ->
							SkyTubeApp.getSettings().setDisplayedReleaseNoteTag(currentVersionNumber))
					.show();
		}
	}

	/**
	 * @return The current app's version number.
	 */
	private String getCurrentVerNumber() {
		String currentAppVersionStr = BuildConfig.VERSION_NAME;

		if (BuildConfig.FLAVOR.equalsIgnoreCase("extra")) {
			String[] ver = BuildConfig.VERSION_NAME.split("\\s+");
			currentAppVersionStr = ver[0];
		} else if (BuildConfig.FLAVOR.equalsIgnoreCase("oss")) {
			// For OSS builds, extract just the version number without any suffix
			String[] ver = BuildConfig.VERSION_NAME.split("\\s+");
			currentAppVersionStr = ver[0];
		}

		return currentAppVersionStr;
	}


}
