package com.liaoww.media.view.photo;

import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.liaoww.media.FileUtil;
import com.liaoww.media.R;
import com.liaoww.media.ThreadPoolUtil;
import com.liaoww.media.view.LoadingFragment;
import com.liaoww.media.view.MApplication;
import com.liaoww.media.view.PicFragment;
import com.liaoww.media.view.main.MainViewModel;

import java.io.File;
import java.util.List;
import java.util.concurrent.Future;

public class PhotoAlbumFragment extends Fragment {
    //View
    private RecyclerView mRecyclerView;

    private PhotoAlbumAdapter mAdapter;

    private View mTitle;

    private Button mDeleteButton;

    private MainViewModel mainViewModel;

    private PhotoViewModel mPhotoViewModel;

    private Future mFuture;


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

    @Override
    public void onResume() {
        super.onResume();
        mTitle.post(() -> showOrHideTitle(false, 0));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mFuture != null) {
            mFuture.cancel(true);
        }
    }

    private void initViewModel() {
        mainViewModel = MApplication.getApp(getContext()).getViewModelProvider().get(MainViewModel.class);
        mainViewModel.getLastPhoto().observe(getActivity(), path -> {
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
        mDeleteButton = view.findViewById(R.id.delete_button);
        mDeleteButton.setOnClickListener(v -> {
            List<String> paths = mPhotoViewModel.getSelectPaths().getValue();
            if (paths != null && paths.size() > 0) {
                LoadingFragment.of().show(getActivity().getSupportFragmentManager(), "album");
                mFuture = ThreadPoolUtil.getThreadPool().submit(() -> {
                    FileUtil.deleteFiles(paths);
                    view.post(() -> {
                        LoadingFragment.of().dismissAllowingStateLoss();
                        List<File> files = FileUtil.loadAllPhoto(view.getContext().getApplicationContext());
                        mAdapter.updateData(files);
                        mainViewModel.setLastPhoto(files.size() > 0 ? mAdapter.getData().get(0).getAbsolutePath() : "");
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
        int height = mTitle.getHeight();
        if (height == 0) {
            return;
        }
        ObjectAnimator objectAnimation = ObjectAnimator.ofFloat(mTitle, "translationY", select ? -height : 0, select ? 0 : -height);
        objectAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
        objectAnimation.setDuration(duration);
        objectAnimation.start();
    }

}
