package com.liaoww.media.view.photo;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.liaoww.media.R;

public class PhotoActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo);
        getSupportFragmentManager().beginTransaction().replace(R.id.container, PhotoAlbumFragment.of()).commit();
    }
}
