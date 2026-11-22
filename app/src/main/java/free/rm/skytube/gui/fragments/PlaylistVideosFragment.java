package free.rm.skytube.gui.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.Random;

import free.rm.skytube.R;
import free.rm.skytube.businessobjects.VideoCategory;
import free.rm.skytube.businessobjects.YouTube.POJOs.CardData;
import free.rm.skytube.businessobjects.YouTube.POJOs.YouTubePlaylist;
import free.rm.skytube.businessobjects.YouTube.POJOs.YouTubeVideo;
import free.rm.skytube.databinding.FragmentPlaylistVideosBinding;
import free.rm.skytube.gui.businessobjects.YouTubePlayer;

/**
 * A Fragment that displays the videos of a playlist in a {@link VideosGridFragment}
 */
public class PlaylistVideosFragment extends VideosGridFragment {
	private YouTubePlaylist youTubePlaylist;

	public static final String PLAYLIST_OBJ = "PlaylistVideosFragment.PLAYLIST_OBJ";

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // sets the play list
        YouTubePlaylist playlist = (YouTubePlaylist) requireArguments().getSerializable(PLAYLIST_OBJ);

        FragmentPlaylistVideosBinding binding = FragmentPlaylistVideosBinding.inflate(inflater, container, false);

        if (playlist == null) {
            Log.e(getClass().getSimpleName(), "No playlist object found:" + getArguments());
            showMain();
            return binding.getRoot();
        } else {
            initPlaylists(container.getContext(), binding, playlist);
        }

        return binding.getRoot();
    }

    protected void initPlaylists(@NonNull Context context, @NonNull FragmentPlaylistVideosBinding binding, @NonNull YouTubePlaylist playlist) {
        this.youTubePlaylist = playlist;
        initVideos(context, videoGridAdapter, binding.videosGridview);
        binding.playlistTitleTextView.setText(youTubePlaylist.getTitle());

        // setup the toolbar/actionbar
        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        ActionBar actionBar = getSupportActionBar();
        String channelTitle = youTubePlaylist.getChannelTitle();
        if (actionBar != null && channelTitle != null) {
            actionBar.setTitle(channelTitle);
        }

        // set the playlist's thumbnail
        Glide.with(requireContext())
                .load(youTubePlaylist.getThumbnailUrl())
                .apply(new RequestOptions().placeholder(R.drawable.channel_thumbnail_default))
                .into(binding.playlistThumbnailImageView);

        // set the channel's banner
        Glide.with(requireContext())
                .load(youTubePlaylist.getBannerUrl())
                .apply(new RequestOptions().placeholder(R.drawable.banner_default))
                .into(binding.playlistBannerImageView);

        // Play / Shuffle pills: Play starts the first video, Shuffle a random one.
        binding.playlistPlayButton.setOnClickListener(v -> playVideoAt(0));
        binding.playlistShuffleButton.setOnClickListener(v -> {
            int count = videoGridAdapter.getItemCount();
            if (count > 0) {
                playVideoAt(new Random().nextInt(count));
            } else {
                Toast.makeText(context, R.string.loading, Toast.LENGTH_SHORT).show();
            }
        });

        // Force initialization
        videoGridAdapter.initializeList();
    }

    private void playVideoAt(int position) {
        CardData card = videoGridAdapter.getVideoAt(position);
        if (card instanceof YouTubeVideo) {
            YouTubePlayer.launch((YouTubeVideo) card, requireContext());
        } else {
            Toast.makeText(requireContext(), R.string.loading, Toast.LENGTH_SHORT).show();
        }
    }

	@Override
	public void onDestroyView() {
		super.onDestroyView();
	}

	private void showMain() {
		Intent startMain = new Intent(Intent.ACTION_MAIN);
		startMain.addCategory(Intent.CATEGORY_HOME);
		startActivity(startMain);
	}

	@Override
	public String getFragmentName() {
		return null;
	}

	@Override
	protected VideoCategory getVideoCategory() {
		// This can be called, when there is no youtubePlaylist object.
		if (youTubePlaylist == null) {
			return null;
		} else {
			return youTubePlaylist.getChannelTitle() != null ? VideoCategory.PLAYLIST_VIDEOS : VideoCategory.MIXED_PLAYLIST_VIDEOS;
		}
	}

	@Override
	protected String getSearchString() {
		return youTubePlaylist.getId();
	}

	@Override
	public int getPriority() {
		return 0;
	}
}
