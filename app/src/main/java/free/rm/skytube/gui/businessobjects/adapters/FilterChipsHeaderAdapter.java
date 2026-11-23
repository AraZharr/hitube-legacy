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
 * along with the program.  If not, see <http://www.gnu.org/licenses/>.
 */

package free.rm.skytube.gui.businessobjects.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import free.rm.skytube.R;
import free.rm.skytube.businessobjects.VideoCategory;

/**
 * Wraps a {@link VideoGridAdapter} with the home feed's filter chip row on top
 * (position 0: All / Featured / Subscriptions), followed by the adapter's videos
 * as full-width cards. The chip row is the same one the shelf layout used, so
 * switching feed category from a chip keeps working exactly as before.
 *
 * <p>Layout: position 0 is the filter chip row; every following position
 * delegates to the wrapped adapter with an offset of -1, so click handling,
 * badges and the load-more trigger all keep working. The chip row is itself a
 * horizontally scrolling RecyclerView, so no chip is ever cut off on a narrow
 * screen.</p>
 */
public class FilterChipsHeaderAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

	/** View type of the chip row; video items use the wrapped adapter's types (0). */
	private static final int TYPE_CHIPS = 1000;

	private final VideoGridAdapter videoGridAdapter;
	private final RecyclerView.AdapterDataObserver dataObserver;

	private VideoCategory currentCategory;

	public FilterChipsHeaderAdapter(@NonNull VideoGridAdapter videoGridAdapter) {
		this.videoGridAdapter = videoGridAdapter;
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

	@Override
	public int getItemViewType(int position) {
		return position == 0 ? TYPE_CHIPS : videoGridAdapter.getItemViewType(position - 1);
	}

	@Override
	public int getItemCount() {
		return 1 + videoGridAdapter.getItemCount();
	}

	@NonNull
	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		if (viewType == TYPE_CHIPS) {
			View view = LayoutInflater.from(parent.getContext())
					.inflate(R.layout.filter_chips_row, parent, false);
			ChipsViewHolder holder = new ChipsViewHolder(view);
			holder.chipsRecyclerView.setLayoutManager(
					new LinearLayoutManager(parent.getContext(), LinearLayoutManager.HORIZONTAL, false));
			holder.chipAdapter = new ShelfVideoGridAdapter.FilterChipAdapter(this::onChipSelected);
			holder.chipsRecyclerView.setAdapter(holder.chipAdapter);
			return holder;
		}
		return videoGridAdapter.onCreateViewHolder(parent, viewType);
	}

	@Override
	public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
		if (holder instanceof ChipsViewHolder) {
			((ChipsViewHolder) holder).chipAdapter.setSelectedCategory(currentCategory);
		} else {
			videoGridAdapter.onBindViewHolder((GridViewHolder) holder, position - 1);
		}
	}

	@Override
	public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
		if (!(holder instanceof ChipsViewHolder)) {
			videoGridAdapter.onViewRecycled((GridViewHolder) holder);
		}
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
		ShelfVideoGridAdapter.FilterChipAdapter chipAdapter;

		ChipsViewHolder(@NonNull View itemView) {
			super(itemView);
			chipsRecyclerView = itemView.findViewById(R.id.filter_chips_recycler_view);
		}
	}
}
