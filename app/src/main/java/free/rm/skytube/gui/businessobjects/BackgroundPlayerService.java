/*
 * SkyTube
 * Copyright (C) 2026  SkyTube contributors
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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.media.app.NotificationCompat.MediaStyle;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import com.google.android.exoplayer2.SimpleExoPlayer;

import java.util.ArrayList;
import java.util.List;

import free.rm.skytube.R;
import free.rm.skytube.businessobjects.Logger;
import free.rm.skytube.businessobjects.YouTube.POJOs.YouTubeVideo;
import free.rm.skytube.gui.activities.YouTubePlayerActivity;

/**
 * Foreground service that keeps the shared player ({@link MiniPlayerManager}) alive
 * while the app is in the background, so audio keeps playing after the user leaves
 * the app. Shows a media notification with play/pause and stop actions, and opens
 * the full player when tapped.
 *
 * <p>Started by {@link free.rm.skytube.app.SkyTubeApp} when the app leaves the
 * foreground while a video is playing, and stopped when the app returns to the
 * foreground (the in-app mini player takes over again), when the user stops
 * playback from the notification, or when the player is closed.</p>
 *
 * <p>Only the default (ExoPlayer) player is covered: the legacy player does not
 * use the shared player, so it keeps its old behaviour (pauses in the background).</p>
 */
public class BackgroundPlayerService extends Service {

	private static final String TAG = BackgroundPlayerService.class.getSimpleName();

	public static final String ACTION_START = "free.rm.skytube.action.BACKGROUND_START";
	public static final String ACTION_TOGGLE = "free.rm.skytube.action.BACKGROUND_TOGGLE";
	public static final String ACTION_STOP = "free.rm.skytube.action.BACKGROUND_STOP";
	public static final String ACTION_OPEN_PLAYER = "free.rm.skytube.action.BACKGROUND_OPEN_PLAYER";

	private static final String CHANNEL_ID = "background_playback";
	private static final int NOTIFICATION_ID = 1001;
	private static final int REQUEST_TOGGLE = 1;
	private static final int REQUEST_STOP = 2;
	private static final int REQUEST_OPEN = 3;

	private MediaSessionCompat mediaSession;
	private MiniPlayerManager.MiniPlayerListener miniPlayerListener;

	/** Starts the service (no-op if there is no active player). */
	public static void start(Context context) {
		Intent intent = new Intent(context, BackgroundPlayerService.class);
		intent.setAction(ACTION_START);
		ContextCompat.startForegroundService(context, intent);
	}

	/** Stops the service (no-op if it is not running). */
	public static void stop(Context context) {
		context.stopService(new Intent(context, BackgroundPlayerService.class));
	}

	@Override
	public void onCreate() {
		super.onCreate();
		createNotificationChannel();
		mediaSession = new MediaSessionCompat(this, getString(R.string.app_name));
		mediaSession.setCallback(new MediaSessionCompat.Callback() {
			@Override
			public void onPlay() {
				MiniPlayerManager.getInstance().play();
			}

			@Override
			public void onPause() {
				MiniPlayerManager.getInstance().pause();
			}

			@Override
			public void onStop() {
				MiniPlayerManager.getInstance().closePlayer();
				stopSelf();
			}
		});
		mediaSession.setActive(true);
		miniPlayerListener = new MiniPlayerManager.MiniPlayerListener() {
			@Override
			public void onCurrentVideoChanged(@NonNull YouTubeVideo video) {
				updateMetadata(video);
				refreshNotification();
			}

			@Override
			public void onPlayStateChanged(boolean isPlaying) {
				updatePlaybackState(isPlaying);
				refreshNotification();
			}

			@Override
			public void onMiniPlayerClosed() {
				stopSelf();
			}
		};
		MiniPlayerManager.getInstance().addListener(miniPlayerListener);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent == null || intent.getAction() == null) {
			// Restarted by the system with no intent: there is no player to serve.
			stopSelf();
			return START_NOT_STICKY;
		}
		MiniPlayerManager manager = MiniPlayerManager.getInstance();
		switch (intent.getAction()) {
			case ACTION_START:
				if (!manager.hasPlayer() || manager.getCurrentVideo() == null) {
					stopSelf();
					break;
				}
				updateMetadata(manager.getCurrentVideo());
				updatePlaybackState(manager.isPlaying());
				startForeground(NOTIFICATION_ID, buildNotification());
				break;
			case ACTION_TOGGLE:
				if (manager.isPlaying()) {
					manager.pause();
				} else {
					manager.play();
				}
				break;
			case ACTION_STOP:
				manager.closePlayer();
				stopSelf();
				break;
			case ACTION_OPEN_PLAYER:
				openPlayer();
				break;
			default:
				Logger.i(this, "BackgroundPlayerService: unknown action " + intent.getAction());
				break;
		}
		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {
		if (miniPlayerListener != null) {
			MiniPlayerManager.getInstance().removeListener(miniPlayerListener);
			miniPlayerListener = null;
		}
		if (mediaSession != null) {
			mediaSession.setActive(false);
			mediaSession.release();
			mediaSession = null;
		}
		super.onDestroy();
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		// Started service only; nothing binds to it.
		return null;
	}

	/**
	 * Reopens the full player for the current video, exactly like tapping the
	 * in-app mini player bar does.
	 */
	private void openPlayer() {
		MiniPlayerManager manager = MiniPlayerManager.getInstance();
		YouTubeVideo video = manager.getCurrentVideo();
		if (video == null) {
			stopSelf();
			return;
		}
		manager.setMinimized(false);
		manager.detachPlayerView();
		Intent intent = new Intent(this, YouTubePlayerActivity.class);
		intent.putExtra(YouTubePlayerActivity.YOUTUBE_VIDEO_OBJ, video);
		List<String> videoIds = manager.getUpNextVideoIds();
		int videoIndex = manager.getUpNextIndex();
		if (videoIds != null && !videoIds.isEmpty() && videoIndex >= 0) {
			intent.putStringArrayListExtra(YouTubePlayerActivity.YOUTUBE_VIDEO_IDS,
					new ArrayList<>(videoIds));
			intent.putExtra(YouTubePlayerActivity.YOUTUBE_VIDEO_INDEX, videoIndex);
		}
		// Launched from a Service context, so a new task is required.
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(intent);
		stopSelf();
	}

	private void refreshNotification() {
		NotificationManager notificationManager =
				(NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		if (notificationManager != null) {
			notificationManager.notify(NOTIFICATION_ID, buildNotification());
		}
	}

	private Notification buildNotification() {
		MiniPlayerManager manager = MiniPlayerManager.getInstance();
		YouTubeVideo video = manager.getCurrentVideo();
		boolean isPlaying = manager.isPlaying();
		String title = video != null ? video.getTitle() : getString(R.string.app_name);
		String channel = video != null ? video.getChannelName() : null;

		NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
				.setSmallIcon(R.drawable.ic_notification_icon)
				.setContentTitle(title)
				.setContentText(channel)
				.setContentIntent(openPlayerPendingIntent())
				.setOngoing(true)
				.setShowWhen(false)
				.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
				.addAction(isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
						getString(isPlaying ? R.string.background_playback_pause : R.string.background_playback_play),
						servicePendingIntent(ACTION_TOGGLE, REQUEST_TOGGLE))
				.addAction(android.R.drawable.ic_menu_close_clear_cancel,
						getString(R.string.background_playback_stop),
						servicePendingIntent(ACTION_STOP, REQUEST_STOP))
				.setStyle(new MediaStyle()
						.setMediaSession(mediaSession.getSessionToken())
						.setShowActionsInCompactView(0, 1));
		return builder.build();
	}

	private PendingIntent servicePendingIntent(String action, int requestCode) {
		Intent intent = new Intent(this, BackgroundPlayerService.class);
		intent.setAction(action);
		return PendingIntent.getService(this, requestCode, intent,
				PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
	}

	private PendingIntent openPlayerPendingIntent() {
		Intent intent = new Intent(this, BackgroundPlayerService.class);
		intent.setAction(ACTION_OPEN_PLAYER);
		return PendingIntent.getService(this, REQUEST_OPEN, intent,
				PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
	}

	private void updateMetadata(@Nullable YouTubeVideo video) {
		if (mediaSession == null || video == null) {
			return;
		}
		MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
		builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, video.getTitle());
		String channel = video.getChannelName();
		if (channel != null) {
			builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, channel);
		}
		mediaSession.setMetadata(builder.build());
	}

	private void updatePlaybackState(boolean isPlaying) {
		if (mediaSession == null) {
			return;
		}
		int state = isPlaying ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
		long position = 0;
		SimpleExoPlayer player = MiniPlayerManager.getInstance().getPlayer();
		if (player != null) {
			position = player.getCurrentPosition();
		}
		PlaybackStateCompat.Builder builder = new PlaybackStateCompat.Builder();
		builder.setState(state, position, 1.0f);
		builder.setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE
				| PlaybackStateCompat.ACTION_PLAY
				| PlaybackStateCompat.ACTION_PAUSE
				| PlaybackStateCompat.ACTION_STOP);
		mediaSession.setPlaybackState(builder.build());
	}

	private void createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
					getString(R.string.background_playback_channel_name),
					NotificationManager.IMPORTANCE_LOW);
			channel.setDescription(getString(R.string.background_playback_channel_description));
			NotificationManager notificationManager =
					(NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			if (notificationManager != null) {
				notificationManager.createNotificationChannel(channel);
			}
		}
	}
}
