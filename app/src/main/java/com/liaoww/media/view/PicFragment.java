package com.liaoww.media.view;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.liaoww.media.FileUtil;
import com.liaoww.media.R;
import com.liaoww.media.jni.FFmpeg;
import com.liaoww.media.view.main.MainViewModel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 查看图片fragment
 */
public class PicFragment extends DialogFragment {
    private ImageView mImageView;

    private Button mRotationButton;

    private Button mMirrorButton;

    private Button mSaveButton;

    private String mPath;

    private float mCurrentRotation = 0f;

    private float mMirrorRotation = 0f;

    private MainViewModel mainViewModel = null;

    private ExecutorService threadPool = Executors.newCachedThreadPool(r -> new Thread(r, "FFmpeg Thread"));

    public static PicFragment of(String path) {
        Bundle bundle = new Bundle();
        bundle.putString("path", path);
        PicFragment fragment = new PicFragment();
        fragment.setArguments(bundle);
        fragment.setStyle(DialogFragment.STYLE_NO_TITLE, R.style.PicFragment);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle bundle = getArguments();
        if (bundle != null) {
            mPath = bundle.getString("path");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_pic, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        findViews(view);
        mainViewModel = new ViewModelProvider(getActivity()).get(MainViewModel.class);
        Glide.with(PicFragment.this).load(mPath).into(mImageView);
    }

    @Override
    public void onResume() {
        super.onResume();
        ViewGroup.LayoutParams params = getDialog().getWindow().getAttributes();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        getDialog().getWindow().setAttributes((WindowManager.LayoutParams) params);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (threadPool != null) {
            threadPool.shutdown();
            threadPool = null;
        }
    }

    private void findViews(View view) {
        mImageView = view.findViewById(R.id.image_view);
        //拉高相机距离，否则执行rotationX/rotationY时会超出画面
        mImageView.setCameraDistance(mImageView.getCameraDistance() * 5);

        mRotationButton = view.findViewById(R.id.rotation_button);
        mRotationButton.setOnClickListener(v -> doRotation());

        mMirrorButton = view.findViewById(R.id.mirror_button);
        mMirrorButton.setOnClickListener(v -> doMirror());

        mSaveButton = view.findViewById(R.id.save_button);
        mSaveButton.setOnClickListener(v -> doSave());
    }

    private void doRotation() {
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(mImageView, "rotation", mCurrentRotation % 360, mCurrentRotation % 360 + 90),
                ObjectAnimator.ofFloat(mImageView, "scaleX", 1f, 0.6f, 1f),
                ObjectAnimator.ofFloat(mImageView, "scaleY", 1f, 0.6f, 1f));
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.setDuration(500);
        animatorSet.start();
        mCurrentRotation = (mCurrentRotation + 90) % 360;
    }

    private void doMirror() {
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(mImageView, "rotationY", mMirrorRotation % 360, mMirrorRotation % 360 + 180),
                ObjectAnimator.ofFloat(mImageView, "scaleX", 1f, 0.6f, 1f),
                ObjectAnimator.ofFloat(mImageView, "scaleY", 1f, 0.6f, 1f));
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.setDuration(500);
        animatorSet.start();
        mMirrorRotation = (mMirrorRotation + 180) % 360;
    }

    private void doSave() {
        threadPool.execute(() -> {
            String outputPath = FileUtil.getPictureOutputPath(getContext());
            int result = FFmpeg.rotation(mPath, outputPath, (int) mCurrentRotation, (int) mMirrorRotation);
            if (result >= 0) {
                mainViewModel.setLastPhoto(outputPath);
                View view = getView();
                if (view != null) {
                    view.post(() -> Toast.makeText(getContext(), "保存成功", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }
}
