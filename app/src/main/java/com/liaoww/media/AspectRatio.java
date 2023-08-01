package com.liaoww.media;

import android.util.Size;

import androidx.annotation.Nullable;

public interface AspectRatio {
    AspectRatioSize AR_4_3 = new AspectRatioSize(new Size(4, 3));
    AspectRatioSize AR_16_9 = new AspectRatioSize(new Size(16, 9));
    AspectRatioSize AR_1_1 = new AspectRatioSize(new Size(1, 1));


    class AspectRatioSize {
        public Size size;
        public String name;

        public AspectRatioSize(Size size) {
            this.size = size;
            this.name = size.getWidth() + ":" + size.getHeight();
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if (obj == null) {
                return false;
            }
            if (this == obj) {
                return true;
            }
            if (obj instanceof AspectRatioSize) {
                AspectRatioSize other = (AspectRatioSize) obj;
                return other.size.equals(size);
            }
            return false;
        }
    }
}
