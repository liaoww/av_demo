package com.liaoww.media.view.photo;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.liaoww.media.R;
import com.liaoww.media.ThreadPoolUtil;
import com.liaoww.media.jni.FFmpeg;
import com.liaoww.media.jni.MediaInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PhotoAlbumAdapter extends RecyclerView.Adapter<PhotoAlbumAdapter.ViewHolder>
        implements View.OnClickListener, View.OnLongClickListener, CompoundButton.OnCheckedChangeListener {

    private static final int MESSAGE_NOTIFY_ITEM_CHANGE = 1000;
    private final Map<String, MediaInfo> mMediaInfoMap = new HashMap<>();

    private final HashMap<Integer, Boolean> mSelectMap = new HashMap<>();

    private final ConcurrentHashMap<String, Boolean> mRunningTasks = new ConcurrentHashMap<>();

    private final List<String> mSelectPaths = new ArrayList<>();

    private final PhotoViewModel mViewModel;

    private final Handler mHandler;

    private List<File> mFileList;
    private View.OnClickListener mListener;
    private boolean mSelectMode = false;

    public PhotoAlbumAdapter(ViewModelStoreOwner owner) {
        mViewModel = new ViewModelProvider(owner).get(PhotoViewModel.class);
        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case MESSAGE_NOTIFY_ITEM_CHANGE:
                        notifyItemChanged(msg.arg1);
                        break;
                }
            }
        };
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.v_photo_album_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Glide.with(holder.itemView.getContext()).load(mFileList.get(position)).into(holder.mImageView);
        holder.itemView.setTag(position);
        holder.itemView.setOnClickListener(this);
        holder.itemView.setOnLongClickListener(this);

        holder.mCheckBox.setOnCheckedChangeListener(this);
        holder.mCheckBox.setTag(position);

        if (mSelectMode) {
            holder.mCheckBox.setVisibility(View.VISIBLE);
            Boolean select = mSelectMap.get(position);
            if (select == null || !select) {
                //没被选中
                holder.mCover.setVisibility(View.GONE);
                holder.mCheckBox.setChecked(false);
            } else {
                //被选中
                holder.mCover.setVisibility(View.VISIBLE);
                holder.mCheckBox.setChecked(true);
            }
        } else {
            holder.mCover.setVisibility(View.GONE);
            holder.mCheckBox.setChecked(false);
            holder.mCheckBox.setVisibility(View.GONE);
        }

        String path = mFileList.get(position).getAbsolutePath();
        if (isNeedFetchMediaInfo(path)) {
            fetchMediaInfo(path);
        }
        MediaInfo mediaInfo = mMediaInfoMap.get(path);
        if (mediaInfo != null) {
            holder.mTextView.setVisibility(View.VISIBLE);
            holder.mTextView.setText(mediaInfo.getDurationText());
        } else {
            holder.mTextView.setVisibility(View.GONE);
        }

    }

    @Override
    public int getItemCount() {
        if (mFileList == null) {
            return 0;
        } else {
            return mFileList.size();
        }
    }

    public List<File> getData() {
        return mFileList;
    }

    public void updateData(List<File> fileList) {
        mFileList = fileList;
        mSelectMode = false;
        mSelectMap.clear();
        mViewModel.setSelect(false);
        notifyDataSetChanged();
    }

    public File getFileByPosition(int position) {
        if (mFileList == null || mFileList.size() <= position) {
            return null;
        } else {
            return mFileList.get(position);
        }
    }

    public void setItemClickListener(View.OnClickListener listener) {
        mListener = listener;
    }

    @Override
    public void onClick(View v) {
        if (mListener != null) {
            mListener.onClick(v);
        }
    }

    @Override
    public boolean onLongClick(View v) {
        mSelectMode = !mSelectMode;
        mViewModel.setSelect(mSelectMode);
        if (!mSelectMode) {
            //取消选中状态，清除之前选中的记录
            mSelectMap.clear();
            clearPath();
        } else {
            int position = (int) v.getTag();
            //进入选中状态
            mSelectMap.put(position, true);
            doCoverAnimation(true, v.findViewById(R.id.cover));
            addPath(mFileList.get(position).getAbsolutePath());
        }
        notifyItemRangeChanged(0, mFileList.size());
        return true;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        int position = (int) buttonView.getTag();
        View cover = ((View) buttonView.getParent()).findViewById(R.id.cover);
        if (mSelectMap.containsKey(position)) {
            //过滤掉check状态和当前相同的情况
            if ((mSelectMap.get(position) == null && !isChecked) || (mSelectMap.get(position) != null && Boolean.TRUE.equals(mSelectMap.get(position)) == isChecked)) {
                return;
            }
            //之前选中过 反选
            mSelectMap.put(position, Boolean.FALSE.equals(mSelectMap.get(position)));
            doCoverAnimation(Boolean.TRUE.equals(mSelectMap.get(position)), cover);
        } else {
            //过滤掉check状态和当前相同的情况
            if (!isChecked) {
                return;
            }
            //之前未点击过，本次选中
            mSelectMap.put(position, true);
            doCoverAnimation(true, cover);
        }

        if (isChecked) {
            addPath(mFileList.get(position).getAbsolutePath());
        } else {
            removePath(mFileList.get(position).getAbsolutePath());
        }
    }

    private boolean isNeedFetchMediaInfo(String path) {
        return path.endsWith(".mp4") && mMediaInfoMap.get(path) == null && !mRunningTasks.containsKey(path);
    }

    private void fetchMediaInfo(String path) {
        mRunningTasks.put(path, true);
        ThreadPoolUtil.getThreadPool().submit(() -> {
            MediaInfo info = FFmpeg.fetchMediaInfo(path);
            mRunningTasks.remove(path);
            mMediaInfoMap.put(path, info);
            int index = getPositionByPath(path);
            if (index != -1) {
                Message messages = Message.obtain();
                messages.what = MESSAGE_NOTIFY_ITEM_CHANGE;
                messages.arg1 = index;
                mHandler.sendMessage(messages);
            }
        });
    }

    private int getPositionByPath(String path) {
        int position = -1;
        if (mFileList != null) {
            for (int i = 0; i < mFileList.size(); i++) {
                if (mFileList.get(i).getAbsolutePath().equals(path)) {
                    position = i;
                    break;
                }
            }
        }
        return position;
    }

    private void doCoverAnimation(boolean visible, View view) {
        ObjectAnimator objectAnimation = ObjectAnimator.ofFloat(view, "alpha", visible ? 0f : 1f, visible ? 1f : 0f);
        objectAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
        objectAnimation.setDuration(300);
        objectAnimation.addListener(new AnimatorListenerAdapter() {

            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                if (visible) {
                    view.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                if (!visible) {
                    view.setVisibility(View.GONE);
                }
            }
        });
        objectAnimation.start();
    }

    private void addPath(String path) {
        mSelectPaths.add(path);
        mViewModel.setSelectPaths(mSelectPaths);
    }

    private void removePath(String path) {
        for (String selectPath : mSelectPaths) {
            if (path.equals(selectPath)) {
                mSelectPaths.remove(path);
                break;
            }
        }
        mViewModel.setSelectPaths(mSelectPaths);
    }

    private void clearPath() {
        mSelectPaths.clear();
        mViewModel.setSelectPaths(mSelectPaths);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView mImageView;
        private final View mCover;
        private final CheckBox mCheckBox;

        private final TextView mTextView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            int imageSize = itemView.getContext().getResources().getDisplayMetrics().widthPixels / 2;
            mImageView = itemView.findViewById(R.id.image_view);
            mImageView.getLayoutParams().width = imageSize;
            mImageView.getLayoutParams().height = imageSize;

            mCover = itemView.findViewById(R.id.cover);
            mCover.getLayoutParams().width = imageSize;
            mCover.getLayoutParams().height = imageSize;

            mCheckBox = itemView.findViewById(R.id.checkbox);

            mTextView = itemView.findViewById(R.id.text_view);
        }
    }
}
