/*
 * SkyTube
 * Copyright (C) 2018  Ramon Mifsud
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

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Checks for HiTube updates by asking the GitHub API for the latest
 * release. HiTube is not on F-Droid and has no other update channel, so
 * this is the only source: one small HTTPS GET, no account needed.
 *
 * Any failure (no network, GitHub rate limit, bad JSON) is recorded in
 * {@link #failure} and reported as "no update" — the check fails
 * silently and never crashes the app.
 */
public class UpdatesChecker {

	/** GitHub API endpoint for HiTube's releases. */
	private static final String HITUBE_RELEASES_API_URL =
			"https://api.github.com/repos/AraZharr/hitube-legacy/releases";

	/** GitHub API endpoint for HiTube's latest release. */
	private static final String HITUBE_LATEST_RELEASE_URL =
			HITUBE_RELEASES_API_URL + "/latest";

	private final static String TAG = UpdatesChecker.class.getSimpleName();

	private String latestApkVersion;
	private String latestReleaseUrl;
	private String releaseNotes;
	private final String currentVersionNumber;
	private boolean updatesAvailable;
	private Throwable failure;

	UpdatesChecker(String currentVersionNumber) {
		this.currentVersionNumber = currentVersionNumber;
	}

	/**
	 * Check for app updates. If an update is available, {@link #latestApkVersion},
	 * {@link #latestReleaseUrl} and {@link #releaseNotes} will be set.
	 */
	public void checkForUpdates() {
		updatesAvailable = false;

		try {
			JSONObject json = fetchLatestRelease();

			latestApkVersion = stripLeadingV(json.optString("tag_name", ""));
			releaseNotes = json.optString("body", "");
			latestReleaseUrl = json.optString("html_url", "");

			Log.d(TAG, "CURRENT_VER: " + currentVersionNumber);
			Log.d(TAG, "REMOTE_VER: " + latestApkVersion);

			updatesAvailable = isNewerVersion(latestApkVersion, currentVersionNumber)
					&& latestReleaseUrl != null && !latestReleaseUrl.isEmpty();

			if (updatesAvailable) {
				Log.d(TAG, "Update available.  RELEASE_URL: " + latestReleaseUrl);
			} else {
				Log.d(TAG, "Not updating.");
			}
		} catch (Throwable e) {
			Log.e(TAG, "An error has occurred while checking for updates", e);
			failure = e;
		}
	}

	public String getLatestReleaseUrl() {
		return latestReleaseUrl;
	}

	public String getLatestApkVersion() {
		return latestApkVersion;
	}

	public String getReleaseNotes() {
		return releaseNotes;
	}

	public Throwable getFailure() {
		return failure;
	}

	public boolean isUpdateAvailable() {
		return updatesAvailable;
	}

	/**
	 * Fetch the release notes of the release for the given installed
	 * version (e.g. "1.0.12" reads the "v1.0.12" release). The "what's
	 * new" dialog must show the notes of the version actually installed,
	 * not the latest release's notes — after an upgrade the latest
	 * release is often still the previous version, so the dialog showed
	 * the wrong notes. Returns the release body, or null when GitHub has
	 * no release with that tag (HTTP 404) or the request fails; the
	 * caller then shows no dialog at all.
	 */
	public String fetchReleaseNotesForVersion(String version) {
		try {
			JSONObject json = fetchRelease(HITUBE_RELEASES_API_URL
					+ "/tags/v" + stripLeadingV(version));
			return json != null ? json.optString("body", "") : null;
		} catch (Throwable e) {
			Log.d(TAG, "No release notes for version " + version + ": " + e.getMessage());
			return null;
		}
	}

	/**
	 * Fetch the latest release JSON from the GitHub API.
	 *
	 * @return The parsed release object.
	 * @throws IOException If the request fails or GitHub answers with an error.
	 */
	private JSONObject fetchLatestRelease() throws Exception {
		JSONObject json = fetchRelease(HITUBE_LATEST_RELEASE_URL);
		if (json == null) {
			throw new IOException("GitHub API did not return the latest release");
		}
		return json;
	}

	/**
	 * GET a release object from the GitHub API.
	 *
	 * @return The parsed release object, or null when GitHub answers with
	 *         an error (e.g. HTTP 404: no release with that tag).
	 * @throws IOException If the request itself fails.
	 */
	private JSONObject fetchRelease(String url) throws Exception {
		HttpURLConnection connection = null;
		try {
			connection = (HttpURLConnection) new URL(url).openConnection();
			connection.setRequestMethod("GET");
			connection.setRequestProperty("Accept", "application/vnd.github+json");
			// GitHub's API rejects requests without a User-Agent header.
			connection.setRequestProperty("User-Agent", "HiTube");
			connection.setConnectTimeout(10000);
			connection.setReadTimeout(15000);
			connection.connect();

			if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
				return null;
			}

			BufferedReader reader = new BufferedReader(
					new InputStreamReader(connection.getInputStream(), "UTF-8"));
			StringBuilder response = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				response.append(line).append('\n');
			}
			reader.close();

			return new JSONObject(response.toString());
		} finally {
			if (connection != null) {
				connection.disconnect();
			}
		}
	}

	/**
	 * @return The version without a leading "v" ("v1.0.8" becomes "1.0.8").
	 */
	private static String stripLeadingV(String version) {
		return version != null && version.startsWith("v") ? version.substring(1) : version;
	}

	/**
	 * @return True if {@code latest} is a newer version than {@code current}.
	 *         Versions are compared numerically, part by part, so "1.0.10" is
	 *         newer than "1.0.9". If either version cannot be parsed, false
	 *         is returned (fail silently: no update prompt).
	 */
	static boolean isNewerVersion(String latest, String current) {
		try {
			String[] latestParts = stripLeadingV(latest).split("\\.");
			String[] currentParts = stripLeadingV(current).split("\\.");
			int len = Math.max(latestParts.length, currentParts.length);
			for (int i = 0; i < len; i++) {
				int latestPart = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;
				int currentPart = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
				if (latestPart != currentPart) {
					return latestPart > currentPart;
				}
			}
			return false;
		} catch (NumberFormatException e) {
			return false;
		}
	}
}
