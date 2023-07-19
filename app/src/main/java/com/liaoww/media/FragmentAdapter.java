package com.liaoww.media;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.liaoww.media.view.IFragment;
import com.liaoww.media.view.RecorderFragment;
import com.liaoww.media.view.TakePicFragment;

import java.util.ArrayList;
import java.util.List;

public class FragmentAdapter extends FragmentStateAdapter {

    public String fetchFragmentNameByPosition(int position) {
        IFragment fragment = fragments.get(position);
        if (fragment instanceof TakePicFragment) {
            return "拍照";
        } else if (fragment instanceof RecorderFragment) {
            return "录像";
        } else return "未知";
    }

    private List<IFragment> fragments;

    public FragmentAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
        init();
    }

    public FragmentAdapter(@NonNull Fragment fragment) {
        super(fragment);
        init();
    }

    public FragmentAdapter(@NonNull FragmentManager fragmentManager, @NonNull Lifecycle lifecycle) {
        super(fragmentManager, lifecycle);
        init();
    }

    public void permissionGranted() {
        if (fragments != null) {
            for (IFragment fragment : fragments) {
                fragment.permissionGranted();
            }
        }
    }

    public IFragment getFragmentByPosition(int position) {
        return fragments.get(position);
    }

    public void changeCamera() {
        if (fragments != null) {
            for (IFragment fragment : fragments) {
                fragment.changeCamera();
            }
        }
    }

    private void init() {
        if (fragments == null) {
            fragments = new ArrayList<>();
        }
        fragments.add(TakePicFragment.of());
        fragments.add(RecorderFragment.of());
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return (Fragment) fragments.get(position);
    }

    @Override
    public int getItemCount() {
        return fragments.size();
    }
}
