package com.liaoww.media.view.main;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class MainViewModel extends ViewModel {
    //最近一张照片或者视频
    private final MutableLiveData<String> mLastPhotoPath = new MutableLiveData<>();

    public MutableLiveData<String> getLastPhoto() {
        return mLastPhotoPath;
    }

    public void setLastPhoto(String path) {
        mLastPhotoPath.postValue(path);
    }
}
