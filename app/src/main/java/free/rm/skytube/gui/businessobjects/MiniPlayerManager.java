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

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;

import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import free.rm.skytube.R;
import free.rm.skytube.app.Settings;
import free.rm.skytube.app.SkyTubeApp;
import free.rm.skytube.app.StreamSelectionPolicy;
import free.rm.skytube.businessobjects.Logger;
import free.rm.skytube.businessobjects.YouTube.POJOs.YouTubeVideo;
import free.rm.skytube.businessobjects.YouTube.YouTubeTasks;
import free.rm.skytube.businessobjects.YouTube.newpipe.VideoId;
import free.rm.skytube.businessobjects.YouTube.VideoStream.VideoResolution;
import free.rm.skytube.businessobjects.interfaces.GetDesiredStreamListener;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * Owns the single shared {@link SimpleExoPlayer} used by the full-screen player
 * ({@link free.rm.skytube.gui.fragments.YouTubePlayerV2Fragment}) and by the in-app
 * mini player bar in {@link free.rm.skytube.gui.fragments.MainFragment}.
 *
 * <p>Why a singleton and not a foreground Service: the V2 fragment's playback pipeline
 * (stream selection, data source, sponsorblock, resume position, quality switching,
 * gestures) is deeply coupled to the fragment. Moving it into a Service would require
 * a major refactor of that pipeline, which cannot be safely verified without a
 * compile here. The singleton keeps playback alive while the app is in the foreground
 * (Android does not kill a foreground app), which covers the minimise -&gt; browse -&gt;
 * expand sequence. This is an in-app mini player, NOT background playback: if the app is
 * swiped away or killed, playback stops.</p>
 *
 * <p>The manager also owns autoplay: when the shared player reports
 * {@link Player#STATE_ENDED}, it advances to the next video in the queue (if autoplay
 * is enabled and a next video exists). The full player fragment does not handle
 * autoplay itself.</p>
 */
public class MiniPlayerManager {

	private static final String TAG = MiniPlayerManager.class.getSimpleName();

	private static MiniPlayerManager instance;

	/** The shared player. Null until the first video is played. */
	private SimpleExoPlayer player;

	/** The video currently loaded in the shared player. */
	private YouTubeVideo currentVideo;

	/** The "up next" queue: video ids of the list the current video was opened from. */
	private final List<String> upNextVideoIds = new ArrayList<>();

	/** Index of the current video inside {@link #upNextVideoIds} (-1 when there is no queue). */
	private int upNextIndex = -1;

	/** True while the player is minimised into the mini player bar. */
	private boolean minimized = false;

	/** Stream info / resolution of the current video (needed by the Quality submenu). */
	private StreamInfo currentStreamInfo;
	private VideoResolution currentResolution;

	/** The PlayerView the shared player is currently attached to (full player or mini bar). */
	private PlayerView attachedPlayerView;

	/** True while an autoplay advance is in progress (avoids double advances). */
	private boolean advancing = false;

	/** When the last autoplay-advanced video first started playing (elapsedRealtime ms, 0 = never). */
	private long lastAdvancePlayStartedMs = 0;

	/** True when the current video was reached via an autoplay advance (not opened by the user). */
	private boolean lastVideoWasAutoplayAdvanced = false;

	/** A video that ends sooner than this after starting to play is treated as a broken/empty stream. */
	private static final long MIN_AUTOPLAY_VIDEO_PLAY_MS = 2000;

	private final CompositeDisposable disposables = new CompositeDisposable();
	private final List<MiniPlayerListener> listeners = new CopyOnWriteArrayList<>();

	/** Manager-level listener: play/pause state and video-ended (autoplay) handling. */
	private final Player.EventListener managerListener = new Player.EventListener() {
		@Override
		public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
			notifyPlayStateChanged(isPlaying());
			// Remember when an autoplay-advanced video actually starts playing, so a
			// video that ends almost immediately can be recognised as a broken/empty
			// stream (see maybeAdvanceToNext()).
			if (playbackState == Player.STATE_READY && playWhenReady
					&& lastVideoWasAutoplayAdvanced && lastAdvancePlayStartedMs == 0) {
				lastAdvancePlayStartedMs = SystemClock.elapsedRealtime();
			}
			if (playbackState == Player.STATE_ENDED) {
				maybeAdvanceToNext();
			}
		}

		@Override
		public void onPlayerError(ExoPlaybackException error) {
			// The full player fragment shows its own error dialog when it is in the
			// foreground. Only surface the error here when the player is minimised
			// (the fragment is gone, so nobody else reports it) — otherwise the user
			// would get both a dialog and a toast for the same failure.
			if (minimized) {
				Logger.e(this, "Playback error while minimised: " + error, error);
				showToast(R.string.playback_error_toast);
			}
		}
	};

	private MiniPlayerManager() {
	}

	public static synchronized MiniPlayerManager getInstance() {
		if (instance == null) {
			instance = new MiniPlayerManager();
		}
		return instance;
	}

	///////////////////////////////////////////////////////////////////////////////////////////////
	// Player lifecycle
	///////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * @return The shared player, creating it (with the application context, so no Activity is
	 *         leaked) if it does not exist yet.
	 */
	public synchronized SimpleExoPlayer getOrCreatePlayer() {
		if (player == null) {
			player = createExoPlayer();
			player.addListener(managerListener);
		}
		return player;
	}

	public synchronized boolean hasPlayer() {
		return player != null;
	}

	public synchronized SimpleExoPlayer getPlayer() {
		return player;
	}

	/**
	 * @return True if the shared player exists and is currently playing the given video.
	 */
	public synchronized boolean isPlayingVideo(@NonNull String videoId) {
		return player != null && currentVideo != null
				&& videoId.equals(currentVideo.getId())
				&& player.getPlaybackState() != Player.STATE_IDLE;
	}

	/**
	 * @return True if the shared player exists and is currently playing.
	 */
	public synchronized boolean isPlaying() {
		return player != null && player.getPlayWhenReady()
				&& player.getPlaybackState() == Player.STATE_READY;
	}

	/**
	 * @return True if the shared player exists, is supposed to be playing, and has a
	 *         stream loaded (buffering or ready). Used to decide whether audio should
	 *         keep playing in the background when the app leaves the foreground.
	 */
	public synchronized boolean isActive() {
		return player != null && player.getPlayWhenReady()
				&& player.getPlaybackState() != Player.STATE_IDLE
				&& player.getPlaybackState() != Player.STATE_ENDED;
	}

	public synchronized void play() {
		if (player != null) {
			player.setPlayWhenReady(true);
		}
	}

	public synchronized void pause() {
		if (player != null) {
			player.setPlayWhenReady(false);
		}
	}

	/**
	 * Stops and releases the shared player and clears all state. Called when the user closes
	 * the player for good (mini bar X button, or the full player finishing without minimising).
	 */
	public synchronized void closePlayer() {
		disposables.clear();
		advancing = false;
		if (player != null) {
			player.removeListener(managerListener);
			player.stop();
			player.release();
			player = null;
		}
		currentVideo = null;
		currentStreamInfo = null;
		currentResolution = null;
		lastVideoWasAutoplayAdvanced = false;
		lastAdvancePlayStartedMs = 0;
		upNextVideoIds.clear();
		upNextIndex = -1;
		minimized = false;
		attachedPlayerView = null;
		notifyClosed();
	}

	///////////////////////////////////////////////////////////////////////////////////////////////
	// PlayerView attachment (one surface at a time)
	///////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Attaches the shared player to the given PlayerView, detaching it from any PlayerView
	 * it was attached to before. Pass null to detach without attaching elsewhere.
	 */
	public synchronized void attachToPlayerView(@Nullable PlayerView view) {
		if (attachedPlayerView != null && attachedPlayerView != view) {
			attachedPlayerView.setPlayer(null);
		}
		attachedPlayerView = view;
		if (view != null && player != null) {
			view.setPlayer(player);
		}
	}

	/** Detaches the shared player from whatever PlayerView it is attached to. */
	public synchronized void detachPlayerView() {
		attachToPlayerView(null);
	}

	///////////////////////////////////////////////////////////////////////////////////////////////
	// Current video and up-next queue
	///////////////////////////////////////////////////////////////////////////////////////////////

	public synchronized void setCurrentVideo(@Nullable YouTubeVideo video) {
		currentVideo = video;
		lastVideoWasAutoplayAdvanced = false;
		lastAdvancePlayStartedMs = 0;
	}

	@Nullable
	public synchronized YouTubeVideo getCurrentVideo() {
		return currentVideo;
	}

	public synchronized StreamInfo getCurrentStreamInfo() {
		return currentStreamInfo;
	}

	public synchronized void setCurrentStreamInfo(@Nullable StreamInfo streamInfo) {
		currentStreamInfo = streamInfo;
	}

	@Nullable
	public synchronized VideoResolution getCurrentResolution() {
		return currentResolution;
	}

	public synchronized void setCurrentResolution(@Nullable VideoResolution resolution) {
		currentResolution = resolution;
	}

	/**
	 * Sets the "up next" queue: the video ids of the list the current video was opened from,
	 * and the index of the current video inside that list.
	 */
	public synchronized void setUpNextQueue(@Nullable List<String> videoIds, int currentIndex) {
		upNextVideoIds.clear();
		if (videoIds != null) {
			upNextVideoIds.addAll(videoIds);
		}
		upNextIndex = currentIndex;
		lastVideoWasAutoplayAdvanced = false;
		lastAdvancePlayStartedMs = 0;
	}

	@NonNull
	public synchronized List<String> getUpNextVideoIds() {
		return new ArrayList<>(upNextVideoIds);
	}

	public synchronized int getUpNextIndex() {
		return upNextIndex;
	}

	public synchronized void setUpNextIndex(int index) {
		upNextIndex = index;
	}

	public synchronized boolean hasNextVideo() {
		return getNextVideoId() != null;
	}

	@Nullable
	public synchronized String getNextVideoId() {
		if (upNextIndex >= 0 && upNextIndex + 1 < upNextVideoIds.size()) {
			return upNextVideoIds.get(upNextIndex + 1);
		}
		return null;
	}

	///////////////////////////////////////////////////////////////////////////////////////////////
	// Minimised state
	///////////////////////////////////////////////////////////////////////////////////////////////

	public synchronized boolean isMinimized() {
		return minimized;
	}

	public synchronized void setMinimized(boolean minimized) {
		this.minimized = minimized;
	}

	///////////////////////////////////////////////////////////////////////////////////////////////
	// Autoplay
	///////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Called when the shared player reports STATE_ENDED. If autoplay is enabled and there is
	 * a next video in the queue, loads and plays it. Otherwise the video simply stays ended
	 * (the previous end-of-video behaviour).
	 */
	private synchronized void maybeAdvanceToNext() {
		if (advancing) {
			return;
		}
		// Guard against a chain of instant ends: if the video that just ended was
		// itself reached by autoplay and it never really played (it ended before it
		// started, or within 2 seconds of starting), its stream is broken or empty.
		// Advancing again would just skip through the rest of the queue showing
		// black screens, so stop here and tell the user instead.
		if (lastVideoWasAutoplayAdvanced
				&& (lastAdvancePlayStartedMs == 0
						|| SystemClock.elapsedRealtime() - lastAdvancePlayStartedMs < MIN_AUTOPLAY_VIDEO_PLAY_MS)) {
			Logger.e(this, "Autoplay: last advanced video ended almost immediately, stopping the chain");
			lastVideoWasAutoplayAdvanced = false;
			lastAdvancePlayStartedMs = 0;
			showAutoplayError();
			return;
		}
		Settings settings = SkyTubeApp.getSettings();
		if (settings == null || !settings.isAutoplayEnabled()) {
			return;
		}
		final String nextVideoId = getNextVideoId();
		if (nextVideoId == null) {
			return;
		}
		advancing = true;
		Logger.i(this, "Autoplay: advancing to next video %s", nextVideoId);
		disposables.add(
				YouTubeTasks.getVideoDetails(SkyTubeApp.getContext(), VideoId.create(nextVideoId))
						.subscribe(
									video -> {
									// Got the next video's details — fetch its stream first.
									// The queue and the UI are only updated in playStream(),
									// once a playable stream is confirmed, so a failed advance
									// never leaves the queue/UI pointing at a video that is
									// not actually playing.
									disposables.add(
											YouTubeTasks.getDesiredStream(video, new GetDesiredStreamListener() {
												@Override
												public void onGetDesiredStream(StreamInfo streamInfo, YouTubeVideo video) {
													advancing = false;
													playStream(streamInfo, video);
												}

												@Override
												public void onGetDesiredStreamError(Throwable throwable) {
													advancing = false;
													Logger.e(this, "Autoplay: could not get stream for next video: " + throwable, throwable);
													showAutoplayError();
												}
											}).subscribe());
								},
								throwable -> {
									advancing = false;
									Logger.e(this, "Autoplay: could not get details for next video: " + throwable, throwable);
									showAutoplayError();
								},
								() -> {
									// Empty (error was already logged by getVideoDetails): nothing to play.
									advancing = false;
									showAutoplayError();
								}));
	}

	/**
	 * Plays the given stream on the shared player, using the same stream-selection policy
	 * as the full player fragment. The video starts from the beginning.
	 */
	private synchronized void playStream(@NonNull StreamInfo streamInfo, @NonNull YouTubeVideo video) {
		if (player == null) {
			return;
		}
		StreamSelectionPolicy selectionPolicy = SkyTubeApp.getSettings().getDesiredVideoResolution(false);
		StreamSelectionPolicy.StreamSelection selection = selectionPolicy.select(streamInfo);
		if (selection == null) {
			Logger.e(this, "Autoplay: no stream available for next video: " + selectionPolicy.getErrorMessage(SkyTubeApp.getContext()));
			showAutoplayError();
			return;
		}
		Uri videoUri = selection.getVideoStreamUri();
		if (videoUri == null) {
			// Audio-only selection: there is no video to show. Never hand a null URI
			// to the player (it would crash) — report it and stay on the old video.
			Logger.e(this, "Autoplay: selected stream has no video URI for video " + video.getId());
			showAutoplayError();
			return;
		}
		// Only now, with a playable stream confirmed, move the queue and the UI to
		// the new video. Updating them earlier (when the details arrived) left the
		// queue/UI showing a video the player had never loaded whenever the stream
		// fetch failed — and skipped that video for good.
		currentVideo = video;
		upNextIndex++;
		notifyVideoChanged(video);
		Logger.i(this, "Autoplay: playing %s, audio: %s", videoUri, selection.getAudioStreamUri());
		DatasourceBuilder datasourceBuilder = new DatasourceBuilder(SkyTubeApp.getContext(), player);
		datasourceBuilder.play(videoUri, selection.getAudioStreamUri(), streamInfo);
		// prepare() resets the position to 0; seekTo(0) makes the "start from the
		// beginning" explicit so no stale position can ever carry over.
		player.seekTo(0);
		player.setPlayWhenReady(true);
		currentStreamInfo = streamInfo;
		currentResolution = selection.getResolution();
		// Mark this video as autoplay-advanced so a near-instant end (broken/empty
		// stream) can be recognised in maybeAdvanceToNext() instead of chaining.
		lastVideoWasAutoplayAdvanced = true;
		lastAdvancePlayStartedMs = 0;
	}

	///////////////////////////////////////////////////////////////////////////////////////////////
	// Listeners
	///////////////////////////////////////////////////////////////////////////////////////////////

	public void addListener(@NonNull MiniPlayerListener listener) {
		listeners.add(listener);
	}

	public void removeListener(@NonNull MiniPlayerListener listener) {
		listeners.remove(listener);
	}

	private void notifyVideoChanged(@NonNull YouTubeVideo video) {
		for (MiniPlayerListener listener : listeners) {
			listener.onCurrentVideoChanged(video);
		}
	}

	private void notifyPlayStateChanged(boolean isPlaying) {
		for (MiniPlayerListener listener : listeners) {
			listener.onPlayStateChanged(isPlaying);
		}
	}

	private void notifyClosed() {
		for (MiniPlayerListener listener : listeners) {
			listener.onMiniPlayerClosed();
		}
	}

	/**
	 * Shows a short, visible message when an autoplay advance fails. Without this the
	 * player would sit on a black/ended screen with no explanation.
	 */
	private void showAutoplayError() {
		showToast(R.string.autoplay_next_video_error);
	}

	private void showToast(@StringRes int messageResId) {
		Context context = SkyTubeApp.getContext();
		if (context == null) {
			return;
		}
		// The advance callbacks already run on the main thread, but post explicitly
		// so the toast is safe no matter which thread reports the failure.
		new Handler(Looper.getMainLooper()).post(() ->
				Toast.makeText(context, messageResId, Toast.LENGTH_LONG).show());
	}

	///////////////////////////////////////////////////////////////////////////////////////////////
	// Private helpers
	///////////////////////////////////////////////////////////////////////////////////////////////

	private SimpleExoPlayer createExoPlayer() {
		DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
		TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory();
		DefaultTrackSelector trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);
		DefaultRenderersFactory defaultRenderersFactory = new DefaultRenderersFactory(SkyTubeApp.getContext());
		return ExoPlayerFactory.newSimpleInstance(SkyTubeApp.getContext(), defaultRenderersFactory,
				trackSelector, new DefaultLoadControl(), null, bandwidthMeter);
	}

	/**
	 * Called by the full player fragment when the shared player's current video changes
	 * (autoplay advance) and by the mini player bar when its title / play state must refresh.
	 */
	public interface MiniPlayerListener {
		/** Called when the shared player starts playing a different video. */
		void onCurrentVideoChanged(@NonNull YouTubeVideo video);

		/** Called when the shared player's play/pause state changes. */
		void onPlayStateChanged(boolean isPlaying);

		/** Called when the shared player was closed (mini bar X, or player finished). */
		void onMiniPlayerClosed();
	}
}
