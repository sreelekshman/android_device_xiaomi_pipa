/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.pipacontrols.saturation;

import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.android.settingslib.widget.LayoutPreference;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.pipacontrols.CustomSeekBarPreference;
import org.lineageos.pipacontrols.R;

public class SaturationFragment extends SettingsBasePreferenceFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String KEY_SATURATION         = "saturation";
    private static final String KEY_SATURATION_PREVIEW = "saturation_preview";

    private View mViewArrowPrevious;
    private View mViewArrowNext;
    private ViewPager mViewPager;
    private ImageView[] mDotIndicators;
    private View[] mViewPagerImages;
    private CustomSeekBarPreference mSaturationPreference;
    private IBinder mSurfaceFlinger;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mSurfaceFlinger = ServiceManager.getService("SurfaceFlinger");
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.saturation, rootKey);

        LayoutPreference preview = findPreference(KEY_SATURATION_PREVIEW);
        if (preview != null) addViewPager(preview);

        mSaturationPreference = findPreference(KEY_SATURATION);
        if (mSaturationPreference != null) {
            mSaturationPreference.setOnPreferenceChangeListener(this);
        }

        int saved = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getInt(KEY_SATURATION, 100);
        updateSaturation(saved);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mSaturationPreference) {
            updateSaturation((Integer) newValue);
            return true;
        }
        return false;
    }

    private void updateSaturation(int seekBarValue) {
        if (mSurfaceFlinger == null) return;
        float saturation = (seekBarValue == 100) ? 1.001f : seekBarValue / 100.0f;
        try {
            Parcel data = Parcel.obtain();
            data.writeInterfaceToken("android.ui.ISurfaceComposer");
            data.writeFloat(saturation);
            mSurfaceFlinger.transact(1022, data, null, 0);
            data.recycle();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void addViewPager(LayoutPreference preview) {
        mViewPager = preview.findViewById(R.id.viewpager);

        int[] drawables = {
            R.drawable.image_preview1,
            R.drawable.image_preview2,
            R.drawable.image_preview3
        };

        mViewPagerImages = new View[drawables.length];
        for (int i = 0; i < drawables.length; i++) {
            mViewPagerImages[i] = getLayoutInflater().inflate(R.layout.image_layout, null);
            ImageView iv = mViewPagerImages[i].findViewById(R.id.imageView);
            if (iv != null) iv.setImageResource(drawables[i]);
        }

        mViewPager.setAdapter(new ImagePreviewPagerAdapter(mViewPagerImages));

        mViewArrowPrevious = preview.findViewById(R.id.arrow_previous);
        mViewArrowPrevious.setOnClickListener(v -> {
            int cur = mViewPager.getCurrentItem();
            if (cur > 0) mViewPager.setCurrentItem(cur - 1, true);
        });

        mViewArrowNext = preview.findViewById(R.id.arrow_next);
        mViewArrowNext.setOnClickListener(v ->
                mViewPager.setCurrentItem(mViewPager.getCurrentItem() + 1, true));

        mViewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                if (positionOffset != 0f) {
                    for (View v : mViewPagerImages) if (v != null) v.setVisibility(View.VISIBLE);
                } else {
                    if (mViewPagerImages[position] != null)
                        mViewPagerImages[position].setContentDescription(
                                getString(R.string.image_preview_content_description));
                    updateIndicator(position);
                }
            }
        });

        ViewGroup viewGroup = preview.findViewById(R.id.viewGroup);
        mDotIndicators = new ImageView[mViewPagerImages.length];
        for (int i = 0; i < mDotIndicators.length; i++) {
            mDotIndicators[i] = new ImageView(requireContext());
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(12, 12);
            lp.setMargins(6, 0, 6, 0);
            mDotIndicators[i].setLayoutParams(lp);
            viewGroup.addView(mDotIndicators[i]);
        }
        updateIndicator(0);
    }

    private void updateIndicator(int position) {
        for (int i = 0; i < mViewPagerImages.length; i++) {
            if (mDotIndicators != null && mDotIndicators[i] != null) {
                mDotIndicators[i].setBackgroundResource(position == i
                        ? R.drawable.ic_image_preview_page_indicator_focused
                        : R.drawable.ic_image_preview_page_indicator_unfocused);
            }
            if (mViewPagerImages[i] != null)
                mViewPagerImages[i].setVisibility(position == i ? View.VISIBLE : View.INVISIBLE);
        }
        if (mViewArrowPrevious != null)
            mViewArrowPrevious.setVisibility(position == 0 ? View.INVISIBLE : View.VISIBLE);
        if (mViewArrowNext != null)
            mViewArrowNext.setVisibility(
                    position == mViewPagerImages.length - 1 ? View.INVISIBLE : View.VISIBLE);
    }

    private static class ImagePreviewPagerAdapter extends PagerAdapter {
        private final View[] mPages;

        ImagePreviewPagerAdapter(View[] pages) { mPages = pages; }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            if (mPages[position] != null) container.removeView(mPages[position]);
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            container.addView(mPages[position]);
            return mPages[position];
        }

        @Override public int getCount() { return mPages.length; }

        @Override
        public boolean isViewFromObject(View view, Object object) { return view == object; }
    }
}
