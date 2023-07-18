package com.liaoww.media;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.graphics.Color;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSIONS = 10000000;

    private static final String[] permissions = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE};

    private TabLayout mTabLayout;
    private ViewPager2 mViewPager;

    private FragmentAdapter mAdapter;
    private TabLayoutMediator mTabLayoutMediator;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViews();
        initPager();
        requestPermission(permissions);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            int granted = PackageManager.PERMISSION_GRANTED;
            for (int r : grantResults) {
                granted |= r;
            }
            if (granted == PackageManager.PERMISSION_GRANTED) {
                mAdapter.permissionGranted();
            } else {
                Toast.makeText(MainActivity.this, "权限被拒绝,功能可能不完整", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mTabLayoutMediator.detach();
    }

    private void findViews() {
        mTabLayout = findViewById(R.id.tab_layout);
        mViewPager = findViewById(R.id.view_pager);
        findViewById(R.id.change_camera_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mAdapter.changeCamera();
            }
        });
    }

    private void initPager() {
        mAdapter = new FragmentAdapter(MainActivity.this);
        mViewPager.setAdapter(mAdapter);
        mTabLayoutMediator = new TabLayoutMediator(mTabLayout, mViewPager, new TabLayoutMediator.TabConfigurationStrategy() {
            @Override
            public void onConfigureTab(@NonNull TabLayout.Tab tab, int position) {
                tab.setCustomView(fetchTabViewByPosition(position));
            }
        });
        mTabLayoutMediator.attach();
    }

    private SparseArray<View> tabViews;

    private View fetchTabViewByPosition(int position) {
        if (tabViews == null) {
            tabViews = new SparseArray<>();
        }

        View tabView = tabViews.get(position);
        if (tabView != null) {
            return tabView;
        } else {
            TextView textView = new TextView(getApplicationContext());
            textView.setText(mAdapter.fetchFragmentNameByPosition(position));
            textView.setTextSize(22f);
            textView.setGravity(Gravity.CENTER);
            textView.setTextColor(Color.parseColor("#ffffff"));
            tabViews.put(position,textView);
            return textView;
        }
    }

    /**
     * 申请权限 6.0
     */
    private void requestPermission(String[] permissions) {
        boolean showRationale = false;
        for (String perm : permissions) {
            //第一次打开App时	false
            //上次弹出权限点击了禁止（但没有勾选“下次不在询问”）	true
            //上次选择禁止并勾选：下次不在询问	false
            showRationale |= shouldShowRequestPermissionRationale(perm);
        }
        if (showRationale) {
            //权限被拒绝
            Toast.makeText(MainActivity.this, "权限被拒绝,功能可能不完整", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            ActivityCompat.requestPermissions(MainActivity.this, permissions, REQUEST_PERMISSIONS);
        }
    }
}
