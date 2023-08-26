package com.liaoww.media.view.photo;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.liaoww.media.FileUtil;
import com.liaoww.media.R;
import com.liaoww.media.view.LoadingFragment;
import com.liaoww.media.view.PicFragment;
import com.liaoww.media.view.main.MainViewModel;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PhotoAlbumFragment extends Fragment {
    //View
    private RecyclerView mRecyclerView;

    private PhotoAlbumAdapter mAdapter;

    private View mTitle;

    private Button mDeleteButton;

    private MainViewModel mainViewModel;

    private PhotoViewModel mPhotoViewModel;

    private ExecutorService mThreadPool = Executors.newCachedThreadPool(r -> new Thread(r, "Album Thread"));


    public static PhotoAlbumFragment of() {
        return new PhotoAlbumFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_photo_album, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        findViews(view);
        initRecyclerView(view);
        initViewModel();
    }

    private void initViewModel() {
        mainViewModel = new ViewModelProvider(getActivity()).get(MainViewModel.class);
        mainViewModel.getLastPhoto().observe(getViewLifecycleOwner(), path -> {
            mAdapter.updateData(FileUtil.loadAllPhoto(getContext().getApplicationContext()));
        });
        mPhotoViewModel = new ViewModelProvider(this).get(PhotoViewModel.class);
        mPhotoViewModel.getSelect().observe(getViewLifecycleOwner(), select -> {
            showOrHideTitle(select, 300);
        });
    }

    private void findViews(View view) {
        mRecyclerView = view.findViewById(R.id.recycler_view);
        mTitle = view.findViewById(R.id.title_layout);
        mTitle.post(() -> showOrHideTitle(false, 0));
        mDeleteButton = view.findViewById(R.id.delete_button);
        mDeleteButton.setOnClickListener(v -> {
            List<String> paths = mPhotoViewModel.getSelectPaths().getValue();
            if (paths != null && paths.size() > 0) {
                LoadingFragment.of().show(getActivity().getSupportFragmentManager(), "album");
                mThreadPool.execute(() -> {
                    FileUtil.deleteFiles(paths);
                    view.post(() -> {
                        LoadingFragment.of().dismissAllowingStateLoss();
                        mAdapter.updateData(FileUtil.loadAllPhoto(view.getContext().getApplicationContext()));
                    });
                });
            }
        });
    }

    private void initRecyclerView(View view) {
        mAdapter = new PhotoAlbumAdapter(this);
        mAdapter.updateData(FileUtil.loadAllPhoto(view.getContext().getApplicationContext()));
        mRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        mRecyclerView.addItemDecoration(new PhotoItemDecoration());
        mRecyclerView.setAdapter(mAdapter);
        //解决闪烁问题
        ((SimpleItemAnimator) mRecyclerView.getItemAnimator()).setSupportsChangeAnimations(false);

        mAdapter.setItemClickListener(v -> {
            File file = mAdapter.getFileByPosition((Integer) v.getTag());
            if (file != null && file.exists() && getActivity() != null) {
                PicFragment.of(file.getAbsolutePath()).show(getActivity().getSupportFragmentManager(), "pic");
            }
        });
    }

    private void showOrHideTitle(boolean select, int duration) {
        ObjectAnimator objectAnimation = ObjectAnimator.ofFloat(mTitle, "translationY", select ? -mTitle.getHeight() : 0, select ? 0 : -mTitle.getHeight());
        objectAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
        objectAnimation.setDuration(duration);
        objectAnimation.start();
    }

}
