package com.liaoww.media.view.photo;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class PhotoItemDecoration extends RecyclerView.ItemDecoration {
    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.getItemOffsets(outRect, view, parent, state);
        outRect.left = 10;
        outRect.right = 10;
        outRect.top = 30;
        outRect.bottom = 30;
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.onDraw(c, parent, state);
        c.drawColor(Color.parseColor("#33000000"));
    }
}
