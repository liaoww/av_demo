package com.liaoww.media.view;

import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStore;
import androidx.lifecycle.ViewModelStoreOwner;

public class MApplication extends Application implements ViewModelStoreOwner {
    private ViewModelStore mStore;
    private ViewModelProvider.Factory mFactory;

    public static MApplication getApp(Context context) {
        return (MApplication) context.getApplicationContext();
    }

    @NonNull
    @Override
    public ViewModelStore getViewModelStore() {
        if (mStore == null) {
            mStore = new ViewModelStore();
        }
        return mStore;
    }

    public ViewModelProvider getViewModelProvider() {
        return new ViewModelProvider(this, getAppFactory());
    }

    private ViewModelProvider.Factory getAppFactory() {
        if (mFactory == null) {
            mFactory = ViewModelProvider.AndroidViewModelFactory.getInstance(this);
        }
        return mFactory;
    }

}
