package com.liaoww.media.view.photo;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.liaoww.media.R;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PhotoAlbumAdapter extends RecyclerView.Adapter<PhotoAlbumAdapter.ViewHolder>
        implements View.OnClickListener, View.OnLongClickListener, CompoundButton.OnCheckedChangeListener {
    private List<File> mFileList;
    private View.OnClickListener mListener;

    private final HashMap<Integer, Boolean> mSelectMap = new HashMap<>();

    private final List<String> mSelectPaths = new ArrayList<>();

    private final PhotoViewModel mViewModel;
    private boolean mSelectMode = false;

    public PhotoAlbumAdapter(ViewModelStoreOwner owner) {
        mViewModel = new ViewModelProvider(owner).get(PhotoViewModel.class);
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
            holder.mCheckBox.setVisibility(View.GONE);
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

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView mImageView;
        private final View mCover;
        private final CheckBox mCheckBox;

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
        }
    }
}
