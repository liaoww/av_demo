package com.liaoww.media.view.photo;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.List;

public class PhotoViewModel extends ViewModel {
    //当前是否是选择中
    private final MutableLiveData<Boolean> mSelect = new MutableLiveData<>();

    private MutableLiveData<List<String>> mSelectPaths = new MutableLiveData<>();

    public MutableLiveData<Boolean> getSelect() {
        return mSelect;
    }

    public void setSelect(boolean select) {
        mSelect.postValue(select);
    }

    public MutableLiveData<List<String>> getSelectPaths() {
        return mSelectPaths;
    }

    public void setSelectPaths(List<String> selectPaths) {
        mSelectPaths.postValue(selectPaths);
    }
}
