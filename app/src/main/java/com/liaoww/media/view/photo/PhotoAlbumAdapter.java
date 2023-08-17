package com.liaoww.media.view.photo;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.liaoww.media.R;

import java.io.File;
import java.util.List;

public class PhotoAlbumAdapter extends RecyclerView.Adapter<PhotoAlbumAdapter.ViewHolder> implements View.OnClickListener {
    private List<File> mFileList;
    private View.OnClickListener mListener;

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.v_photo_album_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Glide.with(holder.itemView.getContext()).load(mFileList.get(position)).into(holder.mImageView);
        holder.mImageView.setTag(position);
        holder.mImageView.setOnClickListener(this);
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
        notifyItemRangeChanged(0, mFileList.size());
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

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView mImageView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            int imageSize = itemView.getContext().getResources().getDisplayMetrics().widthPixels / 2;
            mImageView = itemView.findViewById(R.id.image_view);
            mImageView.getLayoutParams().width = imageSize;
            mImageView.getLayoutParams().height = imageSize;
        }
    }
}
