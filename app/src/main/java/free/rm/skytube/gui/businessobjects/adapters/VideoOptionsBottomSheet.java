/*
 * SkyTube
 * Copyright (C) 2015  Ramon Mifsud
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

package free.rm.skytube.gui.businessobjects.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import free.rm.skytube.R;
import free.rm.skytube.businessobjects.YouTube.POJOs.YouTubeVideo;
import free.rm.skytube.gui.businessobjects.YouTubePlayer;

/**
 * Video actions as a bottom sheet (rounded top corners, small gap at the screen
 * edges). The sheet is a view over a hidden {@link PopupMenu}: the menu keeps
 * owning the options, their visibility and their click handling, and the sheet
 * renders the currently visible items as rows. Call {@link #refreshFromMenu()}
 * whenever the menu changes (e.g. after the async bookmark / subscription /
 * download checks finish) so the sheet stays in sync.
 *
 * <p>The whole sheet content scrolls, so on a short screen every action stays
 * reachable. Primary actions (Play / Save / Share / Download) are a row of
 * labelled icons on top; the remaining menu options follow below.</p>
 */
public class VideoOptionsBottomSheet {

	private final Context context;
	private final YouTubeVideo video;
	private final Menu menu;
	private final PopupMenu.OnMenuItemClickListener menuItemClickListener;
	private final BottomSheetDialog dialog;
	/** The "up next" queue for autoplay (may be null when the video has no list context). */
	private final java.util.List<String> upNextVideoIds;
	/** Index of the video inside the up-next queue (-1 when there is no queue). */
	private final int upNextVideoIndex;

	private final LinearLayout optionsContainer;
	private final ImageView saveIcon;
	private final TextView saveLabel;
	private final ImageView downloadIcon;
	private final TextView downloadLabel;
	private final LinearLayout downloadAction;

	public VideoOptionsBottomSheet(@NonNull Context context, @NonNull YouTubeVideo video,
								   @NonNull Menu menu,
								   @NonNull PopupMenu.OnMenuItemClickListener menuItemClickListener) {
		this(context, video, menu, menuItemClickListener, null, -1);
	}

	public VideoOptionsBottomSheet(@NonNull Context context, @NonNull YouTubeVideo video,
								   @NonNull Menu menu,
								   @NonNull PopupMenu.OnMenuItemClickListener menuItemClickListener,
								   java.util.List<String> upNextVideoIds, int upNextVideoIndex) {
		this.context = context;
		this.video = video;
		this.menu = menu;
		this.menuItemClickListener = menuItemClickListener;
		this.upNextVideoIds = upNextVideoIds;
		this.upNextVideoIndex = upNextVideoIndex;

		dialog = new BottomSheetDialog(context, R.style.HitubeBottomSheetDialog);
		View root = LayoutInflater.from(context).inflate(R.layout.video_options_bottom_sheet, null);
		dialog.setContentView(root);
		// Small gap at the screen edges (the sheet floats instead of spanning edge to edge).
		dialog.setOnShowListener(d -> {
			View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
			if (sheet != null) {
				ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) sheet.getLayoutParams();
				int margin = (int) (8 * context.getResources().getDisplayMetrics().density);
				lp.leftMargin = margin;
				lp.rightMargin = margin;
				sheet.setLayoutParams(lp);
			}
		});

		// Header: thumbnail + title + channel.
		ImageView thumbnail = root.findViewById(R.id.bottom_sheet_thumbnail_image_view);
		TextView title = root.findViewById(R.id.bottom_sheet_title_text_view);
		TextView channel = root.findViewById(R.id.bottom_sheet_channel_text_view);
		Glide.with(context)
				.load(video.getThumbnailUrl())
				.apply(new RequestOptions().placeholder(R.drawable.thumbnail_default))
				.into(thumbnail);
		title.setText(video.getTitle());
		channel.setText(video.getChannelName());

		optionsContainer = root.findViewById(R.id.bottom_sheet_options_container);
		saveIcon = root.findViewById(R.id.bottom_sheet_action_save_icon);
		saveLabel = root.findViewById(R.id.bottom_sheet_action_save_label);
		downloadIcon = root.findViewById(R.id.bottom_sheet_action_download_icon);
		downloadLabel = root.findViewById(R.id.bottom_sheet_action_download_label);
		downloadAction = root.findViewById(R.id.bottom_sheet_action_download);

		LinearLayout playAction = root.findViewById(R.id.bottom_sheet_action_play);
		LinearLayout saveAction = root.findViewById(R.id.bottom_sheet_action_save);
		LinearLayout shareAction = root.findViewById(R.id.bottom_sheet_action_share);

		playAction.setOnClickListener(v -> {
			dialog.dismiss();
			YouTubePlayer.launch(video, context, upNextVideoIds, upNextVideoIndex);
		});
		saveAction.setOnClickListener(v -> {
			MenuItem item = findVisibleMenuItem(R.id.bookmark_video, R.id.unbookmark_video);
			if (item != null) {
				onMenuItemClicked(item);
			}
		});
		shareAction.setOnClickListener(v -> {
			MenuItem item = menu.findItem(R.id.share);
			if (item != null) {
				onMenuItemClicked(item);
			}
		});
		downloadAction.setOnClickListener(v -> {
			MenuItem item = findVisibleMenuItem(R.id.download_video, R.id.delete_download);
			if (item != null) {
				onMenuItemClicked(item);
			}
		});
	}

	public void show() {
		refreshFromMenu();
		dialog.show();
	}

	/** Rebuilds the primary actions and the option rows from the menu's current visible items. */
	public void refreshFromMenu() {
		// Save: bookmarked -> "Saved" (accent icon), otherwise "Save".
		boolean bookmarked = isVisible(R.id.unbookmark_video);
		saveLabel.setText(bookmarked ? R.string.saved : R.string.save);
		int saveTint = ContextCompat.getColor(context,
				bookmarked ? R.color.hitube_primary : R.color.hitube_on_surface);
		saveIcon.setColorFilter(saveTint);

		// Download: downloaded -> "Downloaded", otherwise "Download".
		boolean downloaded = isVisible(R.id.delete_download);
		downloadLabel.setText(downloaded ? R.string.downloaded : R.string.download);
		int downloadTint = ContextCompat.getColor(context,
				downloaded ? R.color.hitube_primary : R.color.hitube_on_surface);
		downloadIcon.setColorFilter(downloadTint);

		// Secondary options: every visible menu item except the ones already
		// shown as primary actions (and the channel submenu itself, whose
		// children are flattened into this list).
		optionsContainer.removeAllViews();
		LayoutInflater inflater = LayoutInflater.from(context);
		for (int i = 0; i < menu.size(); i++) {
			addMenuItemRow(inflater, menu.getItem(i));
		}
	}

	private void addMenuItemRow(LayoutInflater inflater, MenuItem item) {
		if (item == null) {
			return;
		}
		if (item.hasSubMenu()) {
			// Flatten the channel submenu into the option list.
			for (int i = 0; i < item.getSubMenu().size(); i++) {
				addMenuItemRow(inflater, item.getSubMenu().getItem(i));
			}
			return;
		}
		if (!item.isVisible() || isPrimaryMenuItem(item.getItemId())) {
			return;
		}
		View row = inflater.inflate(R.layout.video_option_row, optionsContainer, false);
		ImageView icon = row.findViewById(R.id.bottom_sheet_option_icon);
		TextView title = row.findViewById(R.id.bottom_sheet_option_title);
		if (item.getIcon() != null) {
			icon.setImageDrawable(item.getIcon());
		} else {
			icon.setVisibility(View.GONE);
		}
		title.setText(item.getTitle());
		row.setOnClickListener(v -> onMenuItemClicked(item));
		optionsContainer.addView(row);
	}

	private boolean isPrimaryMenuItem(int itemId) {
		return itemId == R.id.share
				|| itemId == R.id.bookmark_video
				|| itemId == R.id.unbookmark_video
				|| itemId == R.id.download_video
				|| itemId == R.id.delete_download;
	}

	private boolean isVisible(int itemId) {
		MenuItem item = menu.findItem(itemId);
		return item != null && item.isVisible();
	}

	private MenuItem findVisibleMenuItem(int... itemIds) {
		for (int id : itemIds) {
			MenuItem item = menu.findItem(id);
			if (item != null && item.isVisible()) {
				return item;
			}
		}
		return null;
	}

	private void onMenuItemClicked(MenuItem item) {
		dialog.dismiss();
		menuItemClickListener.onMenuItemClick(item);
	}
}
