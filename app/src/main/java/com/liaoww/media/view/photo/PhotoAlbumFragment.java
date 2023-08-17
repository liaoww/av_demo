package com.liaoww.media.view.photo;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liaoww.media.FileUtil;
import com.liaoww.media.R;
import com.liaoww.media.view.PicFragment;

import java.io.File;

public class PhotoAlbumFragment extends Fragment {
    //View
    private RecyclerView mRecyclerView;

    private PhotoAlbumAdapter mAdapter;

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
    }

    private void findViews(View view) {
        mRecyclerView = view.findViewById(R.id.recycler_view);
    }

    private void initRecyclerView(View view) {
        mAdapter = new PhotoAlbumAdapter();
        mAdapter.updateData(FileUtil.loadAllPhoto(view.getContext().getApplicationContext()));
        mRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        mRecyclerView.addItemDecoration(new PhotoItemDecoration());
        mRecyclerView.setAdapter(mAdapter);
        mAdapter.setItemClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                File file = mAdapter.getFileByPosition((Integer) v.getTag());
                if (file != null && file.exists() && getActivity() != null) {
                    PicFragment.of(file.getAbsolutePath()).show(getActivity().getSupportFragmentManager(), "pic");
                }
            }
        });
    }

}
