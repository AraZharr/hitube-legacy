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
package free.rm.skytube.app;

import android.content.Context;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import free.rm.skytube.BuildConfig;
import free.rm.skytube.businessobjects.Logger;

/**
 * Saves the stack trace of an uncaught exception (force close) to a file, so
 * that the next time the app is opened it can show the crash log to the user
 * and let them copy it into a bug report. Without this, crash reports arrive
 * with no log at all and the bug cannot be reproduced or fixed.
 *
 * <p>The handler only writes the log and then delegates to the previous
 * default handler, so the process still dies exactly as it would without it.
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {

	private static final String TAG = "CrashHandler";

	private static final String CRASH_LOG_FILE = "crash-log.txt";

	/** Maximum size of the saved log, to avoid filling the internal storage. */
	private static final int MAX_LOG_SIZE = 64 * 1024;

	private final Thread.UncaughtExceptionHandler defaultHandler;

	private CrashHandler(Thread.UncaughtExceptionHandler defaultHandler) {
		this.defaultHandler = defaultHandler;
	}

	/**
	 * Install this handler as the default uncaught exception handler.
	 * Call once, as early as possible (e.g. from {@link SkyTubeApp#onCreate()}).
	 */
	public static void install(Context context) {
		Thread.UncaughtExceptionHandler current = Thread.getDefaultUncaughtExceptionHandler();
		if (current instanceof CrashHandler) {
			return;
		}
		Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(current));
	}

	@Override
	public void uncaughtException(Thread thread, Throwable throwable) {
		try {
			writeCrashLog(throwable);
		} catch (Throwable ignored) {
			// Never let the crash logger itself throw: the process must die normally.
		}
		if (defaultHandler != null) {
			defaultHandler.uncaughtException(thread, throwable);
		}
	}

	/** @return true if a crash log from a previous run is waiting to be shown. */
	public static boolean hasCrashLog(Context context) {
		return getCrashLogFile(context).exists();
	}

	/** @return the saved crash log, or an empty string if there is none. */
	public static String readCrashLog(Context context) {
		File file = getCrashLogFile(context);
		if (!file.exists()) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line).append('\n');
			}
		} catch (IOException e) {
			Logger.e(TAG, "Unable to read crash log: " + e.getMessage(), e);
		}
		return sb.toString();
	}

	/** Delete the saved crash log (e.g. after the user has dismissed the dialog). */
	public static void deleteCrashLog(Context context) {
		File file = getCrashLogFile(context);
		if (file.exists()) {
			//noinspection ResultOfMethodCallIgnored
			file.delete();
		}
	}

	private static File getCrashLogFile(Context context) {
		return new File(context.getFilesDir(), CRASH_LOG_FILE);
	}

	private void writeCrashLog(Throwable throwable) {
		String report = buildReport(throwable);
		if (report.length() > MAX_LOG_SIZE) {
			report = report.substring(0, MAX_LOG_SIZE);
		}
		// Only the latest crash is kept: overwrite any previous log.
		try (FileWriter writer = new FileWriter(getCrashLogFile(SkyTubeApp.getContext()))) {
			writer.write(report);
		} catch (IOException e) {
			Logger.e(TAG, "Unable to write crash log: " + e.getMessage(), e);
		}
	}

	private static String buildReport(Throwable throwable) {
		StringWriter stackTrace = new StringWriter();
		throwable.printStackTrace(new PrintWriter(stackTrace));

		String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
		StringBuilder sb = new StringBuilder();
		sb.append("HiTube Legacy crash report\n");
		sb.append("Time: ").append(time).append('\n');
		sb.append("App: ").append(BuildConfig.VERSION_NAME)
				.append(" (").append(BuildConfig.VERSION_CODE).append(")")
				.append(" flavor=").append(BuildConfig.FLAVOR).append('\n');
		sb.append("Android: ").append(Build.VERSION.RELEASE)
				.append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
		sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
		sb.append("----\n");
		sb.append(stackTrace.toString());
		return sb.toString();
	}
}
