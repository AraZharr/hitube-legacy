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

package free.rm.skytube.gui.fragments;

import java.util.Arrays;
import java.util.List;

import free.rm.skytube.R;
import free.rm.skytube.app.SkyTubeApp;
import free.rm.skytube.businessobjects.VideoCategory;

/**
 * A fragment that holds today's most popular videos.
 */
public class MostPopularVideosFragment extends VideosGridFragment {

	@Override
	protected VideoCategory getVideoCategory() {
		return VideoCategory.MOST_POPULAR;
	}

	@Override
	protected boolean useShelfLayout() {
		return true;
	}

	@Override
	protected List<String> getShelfTitles() {
		return Arrays.asList(
				SkyTubeApp.getStr(R.string.shelf_quick_picks),
				SkyTubeApp.getStr(R.string.shelf_trending_now),
				SkyTubeApp.getStr(R.string.shelf_more_to_explore));
	}

	@Override
	public String getFragmentName() {
		return SkyTubeApp.getFragmentNames().getName(getBundleKey());
	}

	@Override
	public int getPriority() {
		return 1;
	}

	@Override
	public String getBundleKey() {
		return MainFragment.MOST_POPULAR_VIDEOS_FRAGMENT;
	}
}
