package com.liaoww.media.view.main;

import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class MainViewModel extends ViewModel {
    private final MutableLiveData<String> mLastPhotoPath = new MediatorLiveData<>();

    public MutableLiveData<String> getLastPhoto() {
        return mLastPhotoPath;
    }

    public void setLastPhoto(String path) {
        mLastPhotoPath.postValue(path);
    }
}
