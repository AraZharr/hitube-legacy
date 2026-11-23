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

package free.rm.skytube.gui.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.GridView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

import free.rm.skytube.R;
import free.rm.skytube.businessobjects.VideoCategory;
import free.rm.skytube.databinding.VideosGridviewBinding;
import free.rm.skytube.gui.businessobjects.MainActivityListener;
import free.rm.skytube.gui.businessobjects.adapters.FilterChipsHeaderAdapter;
import free.rm.skytube.gui.businessobjects.adapters.ShelfVideoGridAdapter;
import free.rm.skytube.gui.businessobjects.adapters.VideoGridAdapter;
import free.rm.skytube.gui.businessobjects.fragments.BaseVideosGridFragment;

/**
 * A fragment that will hold a {@link GridView} full of YouTube videos.
 */
public abstract class VideosGridFragment extends BaseVideosGridFragment {
    protected VideosGridviewBinding gridviewBinding;
    /** Wraps {@link #videoGridAdapter} as horizontal shelves; only used when {@link #useShelfLayout()} is true. */
    private ShelfVideoGridAdapter shelfVideoGridAdapter;
    /** Wraps {@link #videoGridAdapter} with the filter chip row on top; only used when {@link #showFilterChips()} is true. */
    private FilterChipsHeaderAdapter filterChipsHeaderAdapter;

    public VideosGridFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // inflate the layout for this fragment, and initialize
        initVideos(container.getContext(), videoGridAdapter, VideosGridviewBinding.inflate(inflater, container, false));

        return gridviewBinding.getRoot();
    }

    protected void initVideos(@NonNull Context context, VideoGridAdapter videoGridAdapterParam, @NonNull VideosGridviewBinding gridviewBindingParam) {
        initBase(context, videoGridAdapterParam);
        this.gridviewBinding = gridviewBindingParam;
        // setup the video grid view
        videoGridAdapter.setSwipeRefreshLayout(gridviewBindingParam.swipeRefreshLayout);

        VideoCategory category = getVideoCategory();
        if (category != null) {
            videoGridAdapter.setVideoCategory(category, getSearchString());
        }
        videoGridAdapter.setListener((MainActivityListener)getActivity());

        gridviewBinding.gridView.setHasFixedSize(true);
        if (useShelfLayout()) {
            // Legacy shelf layout: vertical list of horizontal shelves (+ a filter
            // chip row on top). No feed currently uses it; kept for reference.
            shelfVideoGridAdapter = new ShelfVideoGridAdapter(requireContext(), videoGridAdapter, getShelfTitles());
            gridviewBinding.gridView.setLayoutManager(new LinearLayoutManager(getActivity()));
            gridviewBinding.gridView.setAdapter(shelfVideoGridAdapter);
        } else if (showFilterChips()) {
            // Home feed: filter chip row on top, then the videos as full-width
            // cards in a single-column list (YouTube-style).
            filterChipsHeaderAdapter = new FilterChipsHeaderAdapter(videoGridAdapter);
            gridviewBinding.gridView.setLayoutManager(new GridLayoutManager(getActivity(), getGridSpanCount()));
            gridviewBinding.gridView.setAdapter(filterChipsHeaderAdapter);
        } else {
            gridviewBinding.gridView.setLayoutManager(new GridLayoutManager(getActivity(), getGridSpanCount()));
            gridviewBinding.gridView.setAdapter(videoGridAdapter);
        }
        // Entrance animation for the video grid: cards fade + rise in once,
        // staggered. Cheap (180ms, alpha + translate only) so old devices cope.
        gridviewBinding.gridView.setLayoutAnimation(
                AnimationUtils.loadLayoutAnimation(context, R.anim.hitube_grid_layout_animation));
        gridviewBinding.swipeRefreshLayout.setOnRefreshListener(this);
    }

    /**
     * @return true if this feed should be shown as horizontal shelves instead of a
     * flat vertical grid. Currently unused (no feed returns true); the home feeds
     * now use the flat single-column list with the filter chip row instead.
     * Kept for reference.
     */
    protected boolean useShelfLayout() {
        return false;
    }

    /**
     * @return true if this feed shows the filter chip row (All / Featured /
     * Subscriptions) above the video list. Only the home feeds (Most Popular,
     * Featured) do.
     */
    protected boolean showFilterChips() {
        return false;
    }

    /**
     * @return Number of columns of the flat video grid. Defaults to the
     * {@code video_grid_num_columns} resource (1 on phones, more on wide
     * screens); the home feeds force 1 for the YouTube-style single column.
     */
    protected int getGridSpanCount() {
        return getResources().getInteger(R.integer.video_grid_num_columns);
    }

    /**
     * @return Section titles for the shelves, in order. The last title repeats for
     * any further shelves. Only used when {@link #useShelfLayout()} is true.
     */
    protected List<String> getShelfTitles() {
        return new ArrayList<>();
    }

    @Override
    public void onDestroyView() {
        if (shelfVideoGridAdapter != null) {
            shelfVideoGridAdapter.release();
            shelfVideoGridAdapter = null;
        }
        if (filterChipsHeaderAdapter != null) {
            filterChipsHeaderAdapter.release();
            filterChipsHeaderAdapter = null;
        }
        gridviewBinding.gridView.setAdapter(null);
        gridviewBinding = null;
        super.onDestroyView();
        Glide.get(requireContext()).clearMemory();
    }

    void scrollToTop() {
        gridviewBinding.gridView.smoothScrollToPosition(0);
    }

    /**
     * @return Returns the category of videos being displayed by this fragment.
     */
    protected abstract VideoCategory getVideoCategory();

    /**
     * @return Returns the search string used when setting the video category.  (Can be used to
     * set the channel ID in case of VideoCategory.CHANNEL_VIDEOS).
     */
    protected String getSearchString() {
        return null;
    }

    /**
     * @return The fragment/tab name/title.
     */
    public abstract String getFragmentName();

    public abstract int getPriority();

    public String getBundleKey() {
        return null;
    }
}
