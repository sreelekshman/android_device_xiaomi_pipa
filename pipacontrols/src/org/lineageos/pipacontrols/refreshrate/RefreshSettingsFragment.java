/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.pipacontrols.refreshrate;

import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.widget.SettingsBasePreferenceFragment;

import org.lineageos.pipacontrols.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RefreshSettingsFragment extends SettingsBasePreferenceFragment
        implements ApplicationsState.Callbacks {

    private AllPackagesAdapter mAllPackagesAdapter;
    private ApplicationsState mApplicationsState;
    private ApplicationsState.Session mSession;
    private ActivityFilter mActivityFilter;
    private Map<String, ApplicationsState.AppEntry> mEntryMap = new HashMap<>();

    private RefreshUtils mRefreshUtils;
    private RecyclerView mAppsRecyclerView;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mApplicationsState = ApplicationsState.getInstance(requireActivity().getApplication());
        mSession = mApplicationsState.newSession(this);
        mSession.onResume();
        mActivityFilter = new ActivityFilter(requireActivity().getPackageManager());
        mAllPackagesAdapter = new AllPackagesAdapter(requireActivity());
        mRefreshUtils = new RefreshUtils(requireActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        return inflater.inflate(R.layout.refresh_layout, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mAppsRecyclerView = view.findViewById(R.id.refresh_rv_view);
        mAppsRecyclerView.setLayoutManager(new LinearLayoutManager(requireActivity()));
        mAppsRecyclerView.setAdapter(mAllPackagesAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        requireActivity().setTitle(getString(R.string.refresh_title));
        rebuild();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSession.onPause();
        mSession.onDestroy();
    }

    @Override public void onPackageListChanged() { mActivityFilter.updateLauncherInfoList(); rebuild(); }
    @Override public void onAllSizesComputed() {}
    @Override public void onLauncherInfoChanged() {}
    @Override public void onPackageIconChanged() {}
    @Override public void onPackageSizeChanged(String packageName) {}
    @Override public void onRunningStateChanged(boolean running) {}

    @Override
    public void onRebuildComplete(ArrayList<ApplicationsState.AppEntry> entries) {
        if (entries != null) {
            handleAppEntries(entries);
            mAllPackagesAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onLoadEntriesCompleted() { rebuild(); }

    private void handleAppEntries(List<ApplicationsState.AppEntry> entries) {
        final ArrayList<String> sections = new ArrayList<>();
        final ArrayList<Integer> positions = new ArrayList<>();
        final PackageManager pm = requireActivity().getPackageManager();
        String lastSectionIndex = null;
        int offset = 0;

        for (int i = 0; i < entries.size(); i++) {
            final ApplicationInfo info = entries.get(i).info;
            final String label = (String) info.loadLabel(pm);
            final String sectionIndex;

            if (!info.enabled) {
                sectionIndex = "--";
            } else if (TextUtils.isEmpty(label)) {
                sectionIndex = "";
            } else {
                sectionIndex = label.substring(0, 1).toUpperCase();
            }

            if (lastSectionIndex == null || !TextUtils.equals(sectionIndex, lastSectionIndex)) {
                sections.add(sectionIndex);
                positions.add(offset);
                lastSectionIndex = sectionIndex;
            }
            offset++;
        }

        mAllPackagesAdapter.setEntries(entries, sections, positions);
        mEntryMap.clear();
        for (ApplicationsState.AppEntry e : entries) {
            mEntryMap.put(e.info.packageName, e);
        }
    }

    private void rebuild() {
        mSession.rebuild(mActivityFilter, ApplicationsState.ALPHA_COMPARATOR);
    }

    private int getStateDrawable(int state) {
        switch (state) {
            case RefreshUtils.STATE_STANDARD: return R.drawable.ic_refresh_60;
            case RefreshUtils.STATE_HIGH:     return R.drawable.ic_refresh_90;
            case RefreshUtils.STATE_EXTREME:  return R.drawable.ic_refresh_120;
            case RefreshUtils.STATE_ULTRA:    return R.drawable.ic_refresh_144;
            default:                          return R.drawable.ic_refresh_default;
        }
    }

    private class ViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        Spinner mode;
        ImageView icon;
        ImageView stateIcon;

        ViewHolder(View view) {
            super(view);
            title = view.findViewById(R.id.app_name);
            mode = view.findViewById(R.id.app_mode);
            icon = view.findViewById(R.id.app_icon);
            stateIcon = view.findViewById(R.id.state);
            view.setTag(this);
        }
    }

    private class ModeAdapter extends BaseAdapter {
        private final LayoutInflater inflater;
        private final int[] items = {
            R.string.refresh_default,
            R.string.refresh_standard,
            R.string.refresh_high,
            R.string.refresh_extreme,
            R.string.refresh_ultra
        };

        ModeAdapter(Context context) { inflater = LayoutInflater.from(context); }

        @Override public int getCount() { return items.length; }
        @Override public Object getItem(int position) { return items[position]; }
        @Override public long getItemId(int position) { return 0; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView view = convertView != null ? (TextView) convertView
                    : (TextView) inflater.inflate(
                            android.R.layout.simple_spinner_dropdown_item, parent, false);
            view.setText(items[position]);
            view.setTextSize(14f);
            return view;
        }
    }

    private class AllPackagesAdapter extends RecyclerView.Adapter<ViewHolder>
            implements AdapterView.OnItemSelectedListener {

        private List<ApplicationsState.AppEntry> mEntries = new ArrayList<>();
        private String[] mSections;
        private int[] mPositions;

        AllPackagesAdapter(Context context) {
            mActivityFilter = new ActivityFilter(context.getPackageManager());
        }

        @Override public int getItemCount() { return mEntries.size(); }
        @Override public long getItemId(int position) { return mEntries.get(position).id; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.refresh_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Context context = holder.itemView.getContext();
            ApplicationsState.AppEntry entry = mEntries.get(position);
            if (entry == null) return;

            holder.mode.setAdapter(new ModeAdapter(context));
            holder.mode.setOnItemSelectedListener(this);
            holder.title.setText(entry.label);
            holder.title.setOnClickListener(v -> holder.mode.performClick());
            mApplicationsState.ensureIcon(entry);
            holder.icon.setImageDrawable(entry.icon);
            int packageState = mRefreshUtils.getStateForPackage(entry.info.packageName);
            holder.mode.setSelection(packageState, false);
            holder.mode.setTag(entry);
            holder.stateIcon.setImageResource(getStateDrawable(packageState));
        }

        void setEntries(List<ApplicationsState.AppEntry> entries,
                List<String> sections, List<Integer> positions) {
            mEntries = entries;
            mSections = sections.toArray(new String[0]);
            mPositions = new int[positions.size()];
            for (int i = 0; i < positions.size(); i++) mPositions[i] = positions.get(i);
            notifyDataSetChanged();
        }

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            final ApplicationsState.AppEntry entry =
                    (ApplicationsState.AppEntry) parent.getTag();
            int currentState = mRefreshUtils.getStateForPackage(entry.info.packageName);
            if (currentState != position) {
                mRefreshUtils.writePackage(entry.info.packageName, position);
                notifyDataSetChanged();
            }
        }

        @Override public void onNothingSelected(AdapterView<?> parent) {}
    }

    private class ActivityFilter implements ApplicationsState.AppFilter {
        private final PackageManager mPackageManager;
        private final List<String> mLauncherResolveInfoList = new ArrayList<>();

        ActivityFilter(PackageManager packageManager) {
            this.mPackageManager = packageManager;
            updateLauncherInfoList();
        }

        public void updateLauncherInfoList() {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> resolveInfoList = mPackageManager.queryIntentActivities(i, 0);
            synchronized (mLauncherResolveInfoList) {
                mLauncherResolveInfoList.clear();
                for (ResolveInfo ri : resolveInfoList)
                    mLauncherResolveInfoList.add(ri.activityInfo.packageName);
            }
        }

        @Override public void init() {}

        @Override
        public boolean filterApp(ApplicationsState.AppEntry entry) {
            boolean show = !mAllPackagesAdapter.mEntries.contains(entry.info.packageName);
            if (show) {
                synchronized (mLauncherResolveInfoList) {
                    show = mLauncherResolveInfoList.contains(entry.info.packageName);
                }
            }
            return show;
        }
    }
}
