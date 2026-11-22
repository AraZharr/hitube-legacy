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
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.util.Arrays;
import java.util.List;

import free.rm.skytube.R;
import free.rm.skytube.businessobjects.VideoCategory;

/**
 * Presents a {@link VideoGridAdapter} as a vertical list of horizontal shelves,
 * with a filter chip row on top. The outer RecyclerView is the same grid view
 * used by the flat list, so swipe-to-refresh, scroll-to-top and the fragment
 * lifecycle keep working unchanged.
 *
 * <p>Layout: position 0 is the filter chip row; each following position is one
 * shelf of {@link #SHELF_SIZE} videos. A shelf card is about 48% of the screen
 * width (capped by {@code hitube_shelf_card_width_max}), so about two cards are
 * visible on a narrow screen and the rest are reached by swiping the shelf.
 * The chip row is itself a horizontally scrolling RecyclerView, so no chip is
 * ever cut off on a narrow screen.</p>
 */
public class ShelfVideoGridAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

	private static final int TYPE_CHIPS = 0;
	private static final int TYPE_SHELF = 1;

	/** Videos per shelf. */
	private static final int SHELF_SIZE = 8;

	/** Shelf card width as a fraction of the screen width. */
	private static final float SHELF_CARD_WIDTH_FRACTION = 0.48f;

	private final Context context;
	private final VideoGridAdapter videoGridAdapter;
	private final List<String> shelfTitles;
	private final int shelfCardWidthPx;
	private final RecyclerView.AdapterDataObserver dataObserver;

	private VideoCategory currentCategory;

	public ShelfVideoGridAdapter(@NonNull Context context, @NonNull VideoGridAdapter videoGridAdapter,
								 @NonNull List<String> shelfTitles) {
		this.context = context;
		this.videoGridAdapter = videoGridAdapter;
		this.shelfTitles = shelfTitles;
		this.shelfCardWidthPx = computeShelfCardWidthPx(context);
		this.currentCategory = videoGridAdapter.getCurrentVideoCategory();

		this.dataObserver = new RecyclerView.AdapterDataObserver() {
			@Override
			public void onChanged() {
				notifyDataSetChanged();
			}

			@Override
			public void onItemRangeInserted(int positionStart, int itemCount) {
				notifyDataSetChanged();
			}

			@Override
			public void onItemRangeRemoved(int positionStart, int itemCount) {
				notifyDataSetChanged();
			}

			@Override
			public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
				notifyDataSetChanged();
			}
		};
		videoGridAdapter.registerAdapterDataObserver(dataObserver);
	}

	/** Unregisters the data observer. Call from the fragment's onDestroyView. */
	public void release() {
		videoGridAdapter.unregisterAdapterDataObserver(dataObserver);
	}

	/**
	 * Shelf card width in pixels: ~48% of the screen width, capped by the
	 * {@code hitube_shelf_card_width_max} dimen (smaller on short screens, larger
	 * on tall screens). This keeps about two cards visible on a narrow screen.
	 */
	public static int computeShelfCardWidthPx(@NonNull Context context) {
		Resources res = context.getResources();
		DisplayMetrics metrics = res.getDisplayMetrics();
		int cardWidthPx = (int) (metrics.widthPixels * SHELF_CARD_WIDTH_FRACTION);
		int maxWidthPx = res.getDimensionPixelSize(R.dimen.hitube_shelf_card_width_max);
		return Math.min(cardWidthPx, maxWidthPx);
	}

	@Override
	public int getItemViewType(int position) {
		return position == 0 ? TYPE_CHIPS : TYPE_SHELF;
	}

	@Override
	public int getItemCount() {
		int total = videoGridAdapter.getItemCount();
		int shelves = (total + SHELF_SIZE - 1) / SHELF_SIZE;
		return 1 + shelves; // 1 = filter chip row
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		LayoutInflater inflater = LayoutInflater.from(parent.getContext());
		if (viewType == TYPE_CHIPS) {
			View view = inflater.inflate(R.layout.filter_chips_row, parent, false);
			ChipsViewHolder holder = new ChipsViewHolder(view);
			holder.chipsRecyclerView.setLayoutManager(
					new LinearLayoutManager(parent.getContext(), LinearLayoutManager.HORIZONTAL, false));
			holder.chipAdapter = new FilterChipAdapter(this::onChipSelected);
			holder.chipsRecyclerView.setAdapter(holder.chipAdapter);
			return holder;
		} else {
			View view = inflater.inflate(R.layout.shelf_row, parent, false);
			ShelfViewHolder holder = new ShelfViewHolder(view);
			holder.shelfRecyclerView.setLayoutManager(
					new LinearLayoutManager(parent.getContext(), LinearLayoutManager.HORIZONTAL, false));
			holder.rowAdapter = new ShelfRowAdapter(videoGridAdapter, shelfCardWidthPx);
			holder.shelfRecyclerView.setAdapter(holder.rowAdapter);
			holder.moreButton.setOnClickListener(v -> {
				// Scroll the shelf to its end so the user can reach the last cards.
				int lastPosition = holder.rowAdapter.getItemCount() - 1;
				if (lastPosition >= 0) {
					holder.shelfRecyclerView.smoothScrollToPosition(lastPosition);
				}
			});
			return holder;
		}
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
		if (holder instanceof ChipsViewHolder) {
			((ChipsViewHolder) holder).chipAdapter.setSelectedCategory(currentCategory);
		} else if (holder instanceof ShelfViewHolder) {
			ShelfViewHolder shelfHolder = (ShelfViewHolder) holder;
			int shelfIndex = position - 1;
			shelfHolder.titleTextView.setText(getShelfTitle(shelfIndex));
			shelfHolder.rowAdapter.setShelfStartPosition(shelfIndex * SHELF_SIZE);
		}
	}

	private String getShelfTitle(int shelfIndex) {
		List<String> titles = getCurrentShelfTitles();
		if (titles.isEmpty()) {
			return "";
		}
		return titles.get(Math.min(shelfIndex, titles.size() - 1));
	}

	private List<String> getCurrentShelfTitles() {
		if (currentCategory == VideoCategory.SUBSCRIPTIONS_FEED_VIDEOS) {
			return Arrays.asList(
					context.getString(R.string.subscriptions),
					context.getString(R.string.shelf_more_to_explore));
		}
		return shelfTitles;
	}

	private void onChipSelected(VideoCategory category) {
		if (category == null || category == currentCategory) {
			return;
		}
		currentCategory = category;
		videoGridAdapter.setVideoCategory(category);
		videoGridAdapter.refresh(true);
		notifyDataSetChanged();
	}

	/** View holder for the filter chip row (position 0). */
	private static class ChipsViewHolder extends RecyclerView.ViewHolder {
		final RecyclerView chipsRecyclerView;
		FilterChipAdapter chipAdapter;

		ChipsViewHolder(@NonNull View itemView) {
			super(itemView);
			chipsRecyclerView = itemView.findViewById(R.id.filter_chips_recycler_view);
		}
	}

	/** View holder for one shelf row. */
	private static class ShelfViewHolder extends RecyclerView.ViewHolder {
		final TextView titleTextView;
		final ImageButton moreButton;
		final RecyclerView shelfRecyclerView;
		ShelfRowAdapter rowAdapter;

		ShelfViewHolder(@NonNull View itemView) {
			super(itemView);
			titleTextView = itemView.findViewById(R.id.shelf_title_text_view);
			moreButton = itemView.findViewById(R.id.shelf_more_button);
			shelfRecyclerView = itemView.findViewById(R.id.shelf_recycler_view);
		}
	}

	/** Callback for filter chip taps. */
	interface OnChipSelectedListener {
		void onChipSelected(VideoCategory category);
	}

	/**
	 * The horizontally scrolling filter chip row. Chips are: All (Most Popular),
	 * Featured and Subscriptions. Tapping a chip switches the feed's video category.
	 */
	private static class FilterChipAdapter extends RecyclerView.Adapter<FilterChipAdapter.ChipViewHolder> {

		private static class ChipItem {
			final VideoCategory category;
			final int labelResId;

			ChipItem(VideoCategory category, int labelResId) {
				this.category = category;
				this.labelResId = labelResId;
			}
		}

		private final List<ChipItem> items = Arrays.asList(
				new ChipItem(VideoCategory.MOST_POPULAR, R.string.filter_chip_all),
				new ChipItem(VideoCategory.FEATURED, R.string.featured),
				new ChipItem(VideoCategory.SUBSCRIPTIONS_FEED_VIDEOS, R.string.subscriptions));

		private final OnChipSelectedListener listener;
		private VideoCategory selectedCategory;

		FilterChipAdapter(OnChipSelectedListener listener) {
			this.listener = listener;
		}

		void setSelectedCategory(VideoCategory category) {
			if (category != selectedCategory) {
				selectedCategory = category;
				notifyDataSetChanged();
			}
		}

		@NonNull
		@Override
		public ChipViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			View view = LayoutInflater.from(parent.getContext())
					.inflate(R.layout.filter_chip_item, parent, false);
			return new ChipViewHolder(view);
		}

		@Override
		public void onBindViewHolder(@NonNull ChipViewHolder holder, int position) {
			ChipItem item = items.get(position);
			holder.chip.setText(item.labelResId);
			holder.chip.setChecked(item.category == selectedCategory);
			holder.chip.setOnClickListener(v -> {
				if (listener != null) {
					listener.onChipSelected(item.category);
				}
			});
		}

		@Override
		public int getItemCount() {
			return items.size();
		}

		private static class ChipViewHolder extends RecyclerView.ViewHolder {
			final Chip chip;

			ChipViewHolder(@NonNull View itemView) {
				super(itemView);
				chip = itemView.findViewById(R.id.filter_chip);
			}
		}
	}

	/**
	 * One horizontal shelf row. Delegates view creation / binding to the wrapped
	 * {@link VideoGridAdapter} so click handling, badges and the load-more trigger
	 * all keep working, but constrains each card to the shelf card width and
	 * hides the channel avatar (too cramped at shelf width).
	 */
	private static class ShelfRowAdapter extends RecyclerView.Adapter<GridViewHolder> {

		private final VideoGridAdapter videoGridAdapter;
		private final int shelfCardWidthPx;
		private int shelfStartPosition = 0;

		ShelfRowAdapter(VideoGridAdapter videoGridAdapter, int shelfCardWidthPx) {
			this.videoGridAdapter = videoGridAdapter;
			this.shelfCardWidthPx = shelfCardWidthPx;
		}

		void setShelfStartPosition(int shelfStartPosition) {
			this.shelfStartPosition = shelfStartPosition;
			notifyDataSetChanged();
		}

		@NonNull
		@Override
		public GridViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			GridViewHolder holder = videoGridAdapter.onCreateViewHolder(parent, viewType);
			// Shelf cards are narrower than full-width cards: ~48% of the screen width.
			ViewGroup.LayoutParams lp = holder.itemView.getLayoutParams();
			if (lp == null) {
				lp = new ViewGroup.LayoutParams(shelfCardWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
			} else {
				lp.width = shelfCardWidthPx;
			}
			holder.itemView.setLayoutParams(lp);
			holder.setCompactMode(true);
			return holder;
		}

		@Override
		public void onBindViewHolder(@NonNull GridViewHolder holder, int position) {
			videoGridAdapter.onBindViewHolder(holder, shelfStartPosition + position);
		}

		@Override
		public void onViewRecycled(@NonNull GridViewHolder holder) {
			videoGridAdapter.onViewRecycled(holder);
		}

		@Override
		public int getItemCount() {
			int total = videoGridAdapter.getItemCount();
			int remaining = total - shelfStartPosition;
			return Math.max(0, Math.min(SHELF_SIZE, remaining));
		}
	}
}
