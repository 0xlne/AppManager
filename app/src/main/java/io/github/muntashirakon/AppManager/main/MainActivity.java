/*
 * Copyright (C) 2020 Muntashir Al-Islam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.muntashirakon.AppManager.main;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.usage.UsageStatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.SectionIndexer;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomappbar.BottomAppBar;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.ProgressIndicator;
import com.google.android.material.textview.MaterialTextView;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.appcompat.widget.SearchView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.text.HtmlCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.adb.AdbShell;
import io.github.muntashirakon.AppManager.backup.BackupDialogFragment;
import io.github.muntashirakon.AppManager.backup.MetadataManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsManager;
import io.github.muntashirakon.AppManager.batchops.BatchOpsService;
import io.github.muntashirakon.AppManager.details.AppDetailsActivity;
import io.github.muntashirakon.AppManager.misc.RequestCodes;
import io.github.muntashirakon.AppManager.oneclickops.OneClickOpsActivity;
import io.github.muntashirakon.AppManager.rules.RulesTypeSelectionDialogFragment;
import io.github.muntashirakon.AppManager.runningapps.RunningAppsActivity;
import io.github.muntashirakon.AppManager.servermanager.AppOps;
import io.github.muntashirakon.AppManager.settings.SettingsActivity;
import io.github.muntashirakon.AppManager.types.FullscreenDialog;
import io.github.muntashirakon.AppManager.types.IconLoaderThread;
import io.github.muntashirakon.AppManager.usage.AppUsageActivity;
import io.github.muntashirakon.AppManager.utils.AppPref;
import io.github.muntashirakon.AppManager.utils.Utils;

import static androidx.appcompat.app.ActionBar.LayoutParams;
import static io.github.muntashirakon.AppManager.utils.Utils.requestExternalStoragePermissions;

public class MainActivity extends AppCompatActivity implements
        SearchView.OnQueryTextListener, SwipeRefreshLayout.OnRefreshListener {
    public static final String EXTRA_PACKAGE_LIST = "EXTRA_PACKAGE_LIST";
    public static final String EXTRA_LIST_NAME = "EXTRA_LIST_NAME";

    private static final String PACKAGE_NAME_APK_UPDATER = "com.apkupdater";
    private static final String ACTIVITY_NAME_APK_UPDATER = "com.apkupdater.activity.MainActivity";
    private static final String PACKAGE_NAME_TERMUX = "com.termux";
    private static final String ACTIVITY_NAME_TERMUX = "com.termux.app.TermuxActivity";

    private static final String MIME_TSV = "text/tab-separated-values";

    /**
     * A list of packages separated by \r\n.
     */
    public static String packageList;
    /**
     * The name of this particular package list
     */
    public static String listName;

    private static final int[] sSortMenuItemIdsMap = {R.id.action_sort_by_domain,
            R.id.action_sort_by_app_label, R.id.action_sort_by_package_name,
            R.id.action_sort_by_last_update, R.id.action_sort_by_shared_user_id,
            R.id.action_sort_by_app_size, R.id.action_sort_by_sha, R.id.action_sort_by_disabled_app,
            R.id.action_sort_by_blocked_components};

    @IntDef(value = {
            SORT_BY_DOMAIN,
            SORT_BY_APP_LABEL,
            SORT_BY_PACKAGE_NAME,
            SORT_BY_LAST_UPDATE,
            SORT_BY_SHARED_ID,
            SORT_BY_APP_SIZE_OR_SDK,
            SORT_BY_SHA,
            SORT_BY_DISABLED_APP,
            SORT_BY_BLOCKED_COMPONENTS
    })
    public @interface SortOrder {}
    public static final int SORT_BY_DOMAIN = 0;  // User/system app
    public static final int SORT_BY_APP_LABEL = 1;
    public static final int SORT_BY_PACKAGE_NAME = 2;
    public static final int SORT_BY_LAST_UPDATE = 3;
    public static final int SORT_BY_SHARED_ID = 4;
    public static final int SORT_BY_APP_SIZE_OR_SDK = 5;  // App size/sdk
    public static final int SORT_BY_SHA = 6;  // Signature
    public static final int SORT_BY_DISABLED_APP = 7;
    public static final int SORT_BY_BLOCKED_COMPONENTS = 8;

    @IntDef(flag = true, value = {
            FILTER_NO_FILTER,
            FILTER_USER_APPS,
            FILTER_SYSTEM_APPS,
            FILTER_DISABLED_APPS,
            FILTER_APPS_WITH_RULES,
            FILTER_APPS_WITH_ACTIVITIES
    })
    public @interface Filter {}
    public static final int FILTER_NO_FILTER = 0;
    public static final int FILTER_USER_APPS = 1;
    public static final int FILTER_SYSTEM_APPS = 1 << 1;
    public static final int FILTER_DISABLED_APPS = 1 << 2;
    public static final int FILTER_APPS_WITH_RULES = 1 << 3;
    public static final int FILTER_APPS_WITH_ACTIVITIES = 1 << 4;

    private MainActivity.MainRecyclerAdapter mAdapter;
    private SearchView mSearchView;
    private ProgressIndicator mProgressIndicator;
    private SwipeRefreshLayout mSwipeRefresh;
    private BottomAppBar mBottomAppBar;
    private MaterialTextView mBottomAppBarCounter;
    private LinearLayoutCompat mMainLayout;
    private MainViewModel mModel;
    private CoordinatorLayout.LayoutParams mLayoutParamsSelection;
    private CoordinatorLayout.LayoutParams mLayoutParamsTypical;
    private MenuItem appUsageMenu;
    private MenuItem runningAppsMenu;
    private MenuItem sortByBlockedComponentMenu;
    private @SortOrder int mSortBy;

    private BroadcastReceiver mBatchOpsBroadCastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            showProgressIndicator(false);
        }
    };

    @SuppressLint("RestrictedApi")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode((int) AppPref.get(AppPref.PrefKey.PREF_APP_THEME_INT));
        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.toolbar));
        mModel = ViewModelProvider.AndroidViewModelFactory.getInstance(getApplication()).create(MainViewModel.class);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowCustomEnabled(true);
            actionBar.setTitle(getString(R.string.loading));

            mSearchView = new SearchView(actionBar.getThemedContext());
            mSearchView.setOnQueryTextListener(this);
            mSearchView.setQueryHint(getString(R.string.search));

            ((ImageView) mSearchView.findViewById(androidx.appcompat.R.id.search_button))
                    .setColorFilter(Utils.getThemeColor(this, android.R.attr.colorAccent));
            ((ImageView) mSearchView.findViewById(androidx.appcompat.R.id.search_close_btn))
                    .setColorFilter(Utils.getThemeColor(this, android.R.attr.colorAccent));

            LayoutParams layoutParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            layoutParams.gravity = Gravity.END;
            actionBar.setCustomView(mSearchView, layoutParams);
        }
        packageList = getIntent().getStringExtra(EXTRA_PACKAGE_LIST);
        listName = getIntent().getStringExtra(EXTRA_LIST_NAME);
        if (listName == null) listName = "Onboard.packages";

        mProgressIndicator = findViewById(R.id.progress_linear);
        RecyclerView recyclerView = findViewById(R.id.item_list);
        mSwipeRefresh = findViewById(R.id.swipe_refresh);
        mBottomAppBar = findViewById(R.id.bottom_appbar);
        mBottomAppBarCounter = findViewById(R.id.bottom_appbar_counter);
        mMainLayout = findViewById(R.id.main_layout);

        mSwipeRefresh.setColorSchemeColors(Utils.getThemeColor(this, android.R.attr.colorAccent));
        mSwipeRefresh.setProgressBackgroundColorSchemeColor(Utils.getThemeColor(this, android.R.attr.colorPrimary));
        mSwipeRefresh.setOnRefreshListener(this);

        int margin = Utils.dpToPx(this, 56);
        mLayoutParamsSelection = new CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
                CoordinatorLayout.LayoutParams.MATCH_PARENT);
        mLayoutParamsSelection.setMargins(0, margin, 0, margin);
        mLayoutParamsTypical = new CoordinatorLayout.LayoutParams(
                CoordinatorLayout.LayoutParams.MATCH_PARENT,
                CoordinatorLayout.LayoutParams.MATCH_PARENT);
        mLayoutParamsTypical.setMargins(0, margin, 0, 0);

        mAdapter = new MainActivity.MainRecyclerAdapter(MainActivity.this);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(mAdapter);

        if ((boolean) AppPref.get(AppPref.PrefKey.PREF_SHOW_DISCLAIMER_BOOL)) {
            @SuppressLint("InflateParams")
            View view = getLayoutInflater().inflate(R.layout.dialog_disclaimer, null);
            new MaterialAlertDialogBuilder(this)
                    .setView(view)
                    .setCancelable(false)
                    .setPositiveButton(R.string.disclaimer_agree, (dialog, which) -> {
                        if (((MaterialCheckBox) view.findViewById(R.id.agree_forever)).isChecked()) {
                            AppPref.getInstance().setPref(AppPref.PrefKey.PREF_SHOW_DISCLAIMER_BOOL, false);
                        }
                        checkFirstRun();
                        checkAppUpdate();
                    })
                    .setNegativeButton(R.string.disclaimer_exit, (dialog, which) -> finishAndRemoveTask())
                    .show();
        } else {
            checkFirstRun();
            checkAppUpdate();
        }

        Menu menu = mBottomAppBar.getMenu();
        if (menu instanceof MenuBuilder) {
            ((MenuBuilder) menu).setOptionalIconsVisible(true);
        }
        mBottomAppBar.setNavigationOnClickListener(v -> {
            if (mAdapter != null) mAdapter.clearSelection();
            handleSelection();
        });
        mBottomAppBar.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.action_select_all:
                    mAdapter.selectAll();
                    return true;
                case R.id.action_backup:
                    BackupDialogFragment backupDialogFragment = new BackupDialogFragment();
                    Bundle args = new Bundle();
                    args.putStringArrayList(BackupDialogFragment.ARG_PACKAGES, new ArrayList<>(mModel.getSelectedPackages()));
                    backupDialogFragment.setArguments(args);
                    backupDialogFragment.setOnActionBeginListener(mode -> showProgressIndicator(true));
                    backupDialogFragment.setOnActionCompleteListener((mode, failedPackages) -> showProgressIndicator(false));
                    backupDialogFragment.show(getSupportFragmentManager(), BackupDialogFragment.TAG);
                    mAdapter.clearSelection();
                    handleSelection();
                    return true;
                case R.id.action_backup_apk:
                    if (requestExternalStoragePermissions(this)) {
                        handleBatchOp(BatchOpsManager.OP_BACKUP_APK);
                    }
                    return true;
                case R.id.action_block_trackers:
                    handleBatchOp(BatchOpsManager.OP_BLOCK_TRACKERS);
                    return true;
                case R.id.action_clear_data:
                    handleBatchOp(BatchOpsManager.OP_CLEAR_DATA);
                    return true;
                case R.id.action_disable:
                    handleBatchOp(BatchOpsManager.OP_DISABLE);
                    return true;
                case R.id.action_disable_background:
                    handleBatchOp(BatchOpsManager.OP_DISABLE_BACKGROUND);
                    return true;
                case R.id.action_export_blocking_rules:
                    @SuppressLint("SimpleDateFormat")
                    String fileName = "app_manager_rules_export-" + (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance().getTime())) + ".am.tsv";
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType(MIME_TSV);
                    intent.putExtra(Intent.EXTRA_TITLE, fileName);
                    startActivityForResult(intent, RequestCodes.REQUEST_CODE_BATCH_EXPORT);
                    return true;
                case R.id.action_force_stop:
                    handleBatchOp(BatchOpsManager.OP_FORCE_STOP);
                    return true;
                case R.id.action_uninstall:
                    handleBatchOp(BatchOpsManager.OP_UNINSTALL);
                    return true;
            }
            mAdapter.clearSelection();
            handleSelection();
            return false;
        });
        handleSelection();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == RequestCodes.REQUEST_CODE_BATCH_EXPORT) {
                if (data != null) {
                    RulesTypeSelectionDialogFragment dialogFragment = new RulesTypeSelectionDialogFragment();
                    Bundle args = new Bundle();
                    args.putInt(RulesTypeSelectionDialogFragment.ARG_MODE, RulesTypeSelectionDialogFragment.MODE_EXPORT);
                    args.putParcelable(RulesTypeSelectionDialogFragment.ARG_URI, data.getData());
                    args.putStringArrayList(RulesTypeSelectionDialogFragment.ARG_PKG, new ArrayList<>(mModel.getSelectedPackages()));
                    dialogFragment.setArguments(args);
                    dialogFragment.show(getSupportFragmentManager(), RulesTypeSelectionDialogFragment.TAG);
                    mAdapter.clearSelection();
                    handleSelection();
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RequestCodes.REQUEST_CODE_EXTERNAL_STORAGE_PERMISSIONS) {
            if (grantResults.length == 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                handleBatchOp(BatchOpsManager.OP_BACKUP_APK);
            }
        }
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main_actions, menu);
        appUsageMenu = menu.findItem(R.id.action_app_usage);
        runningAppsMenu = menu.findItem(R.id.action_running_apps);
        sortByBlockedComponentMenu = menu.findItem(R.id.action_sort_by_blocked_components);
        MenuItem apkUpdaterMenu = menu.findItem(R.id.action_apk_updater);
        try {
            if(!getPackageManager().getApplicationInfo(PACKAGE_NAME_APK_UPDATER, 0).enabled)
                throw new PackageManager.NameNotFoundException();
            apkUpdaterMenu.setVisible(true);
        } catch (PackageManager.NameNotFoundException e) {
            apkUpdaterMenu.setVisible(false);
        }
        MenuItem termuxMenu = menu.findItem(R.id.action_termux);
        try {
            if(!getPackageManager().getApplicationInfo(PACKAGE_NAME_TERMUX, 0).enabled)
                throw new PackageManager.NameNotFoundException();
            termuxMenu.setVisible(true);
        } catch (PackageManager.NameNotFoundException e) {
            termuxMenu.setVisible(false);
        }
        if (menu instanceof MenuBuilder) {
            ((MenuBuilder) menu).setOptionalIconsVisible(true);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(sSortMenuItemIdsMap[mSortBy]).setChecked(true);
        if (mModel != null) {
            int flags = mModel.getFilterFlags();
            if ((flags & MainActivity.FILTER_USER_APPS) != 0) {
                menu.findItem(R.id.action_filter_user_apps).setChecked(true);
            }
            if ((flags & MainActivity.FILTER_SYSTEM_APPS) != 0) {
                menu.findItem(R.id.action_filter_system_apps).setChecked(true);
            }
            if ((flags & MainActivity.FILTER_DISABLED_APPS) != 0) {
                menu.findItem(R.id.action_filter_disabled_apps).setChecked(true);
            }
            if ((flags & MainActivity.FILTER_APPS_WITH_RULES) != 0) {
                menu.findItem(R.id.action_filter_apps_with_rules).setChecked(true);
            }
            if ((flags & MainActivity.FILTER_APPS_WITH_ACTIVITIES) != 0) {
                menu.findItem(R.id.action_filter_apps_with_activities).setChecked(true);
            }
        }
        if (AppPref.isRootEnabled() || AppPref.isAdbEnabled()) {
            runningAppsMenu.setVisible(true);
            sortByBlockedComponentMenu.setVisible(true);
        } else {
            runningAppsMenu.setVisible(false);
            sortByBlockedComponentMenu.setVisible(false);
        }
        if ((Boolean) AppPref.get(AppPref.PrefKey.PREF_USAGE_ACCESS_ENABLED_BOOL)) {
            appUsageMenu.setVisible(true);
        } else appUsageMenu.setVisible(false);
        return true;
    }

    @SuppressLint("InflateParams")
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_instructions:
                new FullscreenDialog(this)
                        .setTitle(R.string.instructions)
                        .setView(R.layout.dialog_instructions)
                        .show();
                return true;
            case R.id.action_refresh:
                if (mModel != null) {
                    showProgressIndicator(true);
                    mModel.loadApplicationItems();
                }
                return true;
            case R.id.action_settings:
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
                return true;
            // Sort
            case R.id.action_sort_by_app_label:
                setSortBy(SORT_BY_APP_LABEL);
                item.setChecked(true);
                return true;
            case R.id.action_sort_by_package_name:
                setSortBy(SORT_BY_PACKAGE_NAME);
                item.setChecked(true);
                return true;
            case R.id.action_sort_by_domain:
                setSortBy(SORT_BY_DOMAIN);
                item.setChecked(true);
                return true;
            case R.id.action_sort_by_last_update:
                setSortBy(SORT_BY_LAST_UPDATE);
                item.setChecked(true);
                return true;
            case R.id.action_sort_by_shared_user_id:
                setSortBy(SORT_BY_SHARED_ID);
                item.setChecked(true);
                return true;
            case R.id.action_sort_by_sha:
                setSortBy(SORT_BY_SHA);
                item.setChecked(true);
                return true;
            case R.id.action_sort_by_app_size:
                setSortBy(SORT_BY_APP_SIZE_OR_SDK);
                item.setChecked(true);
                return true;
            case R.id.action_sort_by_disabled_app:
                setSortBy(SORT_BY_DISABLED_APP);
                item.setChecked(true);
                return true;
            case R.id.action_sort_by_blocked_components:
                setSortBy(SORT_BY_BLOCKED_COMPONENTS);
                item.setChecked(true);
                return true;
            // Filter
            case R.id.action_filter_user_apps:
                if (!item.isChecked()) mModel.addFilterFlag(FILTER_USER_APPS);
                else mModel.removeFilterFlag(FILTER_USER_APPS);
                item.setChecked(!item.isChecked());
                return true;
            case R.id.action_filter_system_apps:
                if (!item.isChecked()) mModel.addFilterFlag(FILTER_SYSTEM_APPS);
                else mModel.removeFilterFlag(FILTER_SYSTEM_APPS);
                item.setChecked(!item.isChecked());
                return true;
            case R.id.action_filter_disabled_apps:
                if (!item.isChecked()) mModel.addFilterFlag(FILTER_DISABLED_APPS);
                else mModel.removeFilterFlag(FILTER_DISABLED_APPS);
                item.setChecked(!item.isChecked());
                return true;
            case R.id.action_filter_apps_with_rules:
                if (!item.isChecked()) mModel.addFilterFlag(FILTER_APPS_WITH_RULES);
                else mModel.removeFilterFlag(FILTER_APPS_WITH_RULES);
                item.setChecked(!item.isChecked());
                return true;
            case R.id.action_filter_apps_with_activities:
                if (!item.isChecked()) mModel.addFilterFlag(FILTER_APPS_WITH_ACTIVITIES);
                else mModel.removeFilterFlag(FILTER_APPS_WITH_ACTIVITIES);
                item.setChecked(!item.isChecked());
                return true;
            case R.id.action_app_usage:
                Intent usageIntent = new Intent(this, AppUsageActivity.class);
                startActivity(usageIntent);
                return true;
            case R.id.action_one_click_ops:
                Intent onClickOpsIntent = new Intent(this, OneClickOpsActivity.class);
                startActivity(onClickOpsIntent);
                return true;
            case R.id.action_apk_updater:
                try {
                    if(!getPackageManager().getApplicationInfo(PACKAGE_NAME_APK_UPDATER, 0).enabled)
                        throw new PackageManager.NameNotFoundException();
                    Intent intent = new Intent();
                    intent.setClassName(PACKAGE_NAME_APK_UPDATER, ACTIVITY_NAME_APK_UPDATER);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception ignored) {}
                return true;
            case R.id.action_termux:
                try {
                    if(!getPackageManager().getApplicationInfo(PACKAGE_NAME_TERMUX, 0).enabled)
                        throw new PackageManager.NameNotFoundException();
                    Intent intent = new Intent();
                    intent.setClassName(PACKAGE_NAME_TERMUX, ACTIVITY_NAME_TERMUX);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception ignored) {}
                return true;
            case R.id.action_running_apps:
                Intent runningAppsIntent = new Intent(this, RunningAppsActivity.class);
                startActivity(runningAppsIntent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onRefresh() {
        if (mSortBy == SORT_BY_APP_SIZE_OR_SDK && Build.VERSION.SDK_INT <= Build.VERSION_CODES.O) {
            Toast t = Toast.makeText(this, getString(R.string.refresh) + " & " + getString(R.string.sort) + "/" + getString(R.string.sort_by_app_size)
                    + "\n" + getString(R.string.unsupported), Toast.LENGTH_LONG);
            t.setGravity(Gravity.CENTER , Gravity.CENTER, Gravity.CENTER);
            t.show();
        } else {
            showProgressIndicator(true);
            mModel.loadApplicationItems();
        }
        mSwipeRefresh.setRefreshing(false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Check root
        AppPref.getInstance().setPref(AppPref.PrefKey.PREF_ADB_MODE_ENABLED_BOOL, false);
        if (!Utils.isRootGiven()) {
            AppPref.getInstance().setPref(AppPref.PrefKey.PREF_ROOT_MODE_ENABLED_BOOL, false);
            AppPref.getInstance().setPref(AppPref.PrefKey.PREF_ADB_MODE_ENABLED_BOOL, true);
            // Check for adb
            new Thread(() -> {
                try {
                    AdbShell.CommandResult result = AdbShell.run("id");
                    if (!result.isSuccessful()) {
                        AppPref.getInstance().setPref(AppPref.PrefKey.PREF_ADB_MODE_ENABLED_BOOL, false);
                        throw new IOException("Adb not available");
                    }
                    AppOps.updateConfig(this);
                    runOnUiThread(() -> Toast.makeText(this, "Working on ADB mode", Toast.LENGTH_SHORT).show());
                } catch (Exception ignored) {}
            }).start();
        }

        if (mAdapter != null) {
            // Set observer
            mModel.getApplicationItems().observe(this, applicationItems -> {
                mAdapter.setDefaultList(applicationItems);
                showProgressIndicator(false);
                // Set title and subtitle
                ActionBar actionBar = getSupportActionBar();
                if (actionBar != null) {
                    actionBar.setTitle(listName.substring(0, listName.lastIndexOf(".")));
                    actionBar.setSubtitle(MainActivity.listName.substring(listName
                            .lastIndexOf(".") + 1).toLowerCase(Locale.ROOT));
                }
            });
            // Set filter
            if (mSearchView != null && !TextUtils.isEmpty(mModel.getSearchQuery())) {
                mSearchView.setIconified(false);
                mSearchView.setQuery(mModel.getSearchQuery(), false);
            }
        }
        // Show/hide app usage menu
        if (appUsageMenu != null) {
            if ((Boolean) AppPref.get(AppPref.PrefKey.PREF_USAGE_ACCESS_ENABLED_BOOL))
                appUsageMenu.setVisible(true);
            else appUsageMenu.setVisible(false);
        }
        // Set sort by
        mSortBy = (int) AppPref.get(AppPref.PrefKey.PREF_MAIN_WINDOW_SORT_ORDER_INT);
        if (AppPref.isRootEnabled() || AppPref.isAdbEnabled()) {
            if (runningAppsMenu != null) runningAppsMenu.setVisible(true);
            if (sortByBlockedComponentMenu != null) sortByBlockedComponentMenu.setVisible(true);
        } else {
            if (mSortBy == SORT_BY_BLOCKED_COMPONENTS) mSortBy = SORT_BY_APP_LABEL;
            if (runningAppsMenu != null) runningAppsMenu.setVisible(false);
            if (sortByBlockedComponentMenu != null) sortByBlockedComponentMenu.setVisible(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mBatchOpsBroadCastReceiver, new IntentFilter(BatchOpsService.ACTION_BATCH_OPS));
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mBatchOpsBroadCastReceiver);
    }

    private void checkFirstRun() {
        if (Utils.isAppInstalled()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.instructions)
                    .setView(R.layout.dialog_instructions)
                    .setNegativeButton(android.R.string.ok, null)
                    .show();
            AppPref.getInstance().setPref(AppPref.PrefKey.PREF_LAST_VERSION_CODE_LONG, (long) BuildConfig.VERSION_CODE);
        }
    }

    private void checkAppUpdate() {
        if (Utils.isAppUpdated()) {
            new Thread(() -> {
                final Spanned spannedChangelog = HtmlCompat.fromHtml(Utils.getContentFromAssets(this, "changelog.html"), HtmlCompat.FROM_HTML_MODE_COMPACT);
                runOnUiThread(() -> {
                    View view = getLayoutInflater().inflate(R.layout.dialog_changelog, null);
                    ((MaterialTextView) view.findViewById(R.id.content)).setText(spannedChangelog);
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.changelog)
                            .setView(view)
                            .setNegativeButton(android.R.string.ok, null)
                            .setNeutralButton(R.string.instructions, (dialog, which) ->
                                    new FullscreenDialog(this)
                                            .setTitle(R.string.instructions)
                                            .setView(R.layout.dialog_instructions)
                                            .show())
                            .show();
                });
            }).start();
            AppPref.getInstance().setPref(AppPref.PrefKey.PREF_LAST_VERSION_CODE_LONG, (long) BuildConfig.VERSION_CODE);
        }
    }

    private void handleSelection() {
        if (mModel.getSelectedPackages().size() == 0) {
            mBottomAppBar.setVisibility(View.GONE);
            mMainLayout.setLayoutParams(mLayoutParamsTypical);
            mAdapter.clearSelection();
        } else {
            mBottomAppBar.setVisibility(View.VISIBLE);
            mBottomAppBarCounter.setText(getString(R.string.some_items_selected, mModel.getSelectedPackages().size()));
            mMainLayout.setLayoutParams(mLayoutParamsSelection);
        }
    }

    private void handleBatchOp(@BatchOpsManager.OpType int op) {
        showProgressIndicator(true);
        Intent intent = new Intent(this, BatchOpsService.class);
        intent.putStringArrayListExtra(BatchOpsService.EXTRA_OP_PKG, new ArrayList<>(mModel.getSelectedPackages()));
        intent.putExtra(BatchOpsService.EXTRA_OP, op);
        ContextCompat.startForegroundService(this, intent);
        new Thread(() -> {
            mAdapter.clearSelection();
            runOnUiThread(this::handleSelection);
        }).start();
    }

    private void showProgressIndicator(boolean show) {
        if (show) mProgressIndicator.show();
        else mProgressIndicator.hide();
    }

    /**
     * Sort main list if provided value is valid.
     *
     * @param sortBy Must be one of SORT_*
     */
    private void setSortBy(@SortOrder int sortBy) {
        mSortBy = sortBy;
        mModel.setSortBy(sortBy);
    }

    @Override
    public boolean onQueryTextChange(String searchQuery) {
        mModel.setSearchQuery(searchQuery.toLowerCase());
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String s) {
        return false;
    }

    static class MainRecyclerAdapter extends RecyclerView.Adapter<MainRecyclerAdapter.ViewHolder> implements SectionIndexer {
        static final String sections = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        @SuppressLint("SimpleDateFormat")
        static final DateFormat sSimpleDateFormat = new SimpleDateFormat("dd/MM/yyyy"); // hh:mm:ss");

        private MainActivity mActivity;
        private PackageManager mPackageManager;
        private String mSearchQuery;
        private final List<ApplicationItem> mAdapterList = new ArrayList<>();

        private static int mColorTransparent;
        private static int mColorSemiTransparent;
        private static int mColorHighlight;
        private static int mColorDisabled;
        private static int mColorStopped;
        private static int mColorOrange;
        private static int mColorPrimary;
        private static int mColorSecondary;
        private static int mColorRed;

        MainRecyclerAdapter(@NonNull MainActivity activity) {
            mActivity = activity;
            mPackageManager = activity.getPackageManager();

            mColorTransparent = Color.TRANSPARENT;
            mColorSemiTransparent = ContextCompat.getColor(mActivity, R.color.semi_transparent);
            mColorHighlight = ContextCompat.getColor(mActivity, R.color.highlight);
            mColorDisabled = ContextCompat.getColor(mActivity, R.color.disabled_user);
            mColorStopped = ContextCompat.getColor(mActivity, R.color.stopped);
            mColorOrange = ContextCompat.getColor(mActivity, R.color.orange);
            mColorPrimary = Utils.getThemeColor(mActivity, android.R.attr.textColorPrimary);
            mColorSecondary = Utils.getThemeColor(mActivity, android.R.attr.textColorSecondary);
            mColorRed = ContextCompat.getColor(mActivity, R.color.red);
        }

        void setDefaultList(List<ApplicationItem> list) {
            new Thread(() -> {
                synchronized (mAdapterList) {
                    mAdapterList.clear();
                    mAdapterList.addAll(list);
                    mSearchQuery = mActivity.mModel.getSearchQuery();
                    mActivity.runOnUiThread(this::notifyDataSetChanged);
                }
            }).start();
        }

        void clearSelection() {
            synchronized (mAdapterList) {
                final List<Integer> itemIds = new ArrayList<>();
                int itemId;
                for (ApplicationItem applicationItem : mActivity.mModel.getSelectedApplicationItems()) {
                    itemId = mAdapterList.indexOf(applicationItem);
                    if (itemId == -1) continue;
                    applicationItem.isSelected = false;
                    mAdapterList.set(itemId, applicationItem);
                    itemIds.add(itemId);
                }
                mActivity.runOnUiThread(() -> {for (int id: itemIds) notifyItemChanged(id);});
                mActivity.mModel.clearSelection();
            }
        }

        void selectAll() {
            synchronized (mAdapterList) {
                for (int i = 0; i < mAdapterList.size(); ++i) {
                    mAdapterList.set(i, mActivity.mModel.select(mAdapterList.get(i)));
                    notifyItemChanged(i);
                }
                mActivity.handleSelection();
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_main, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            // Cancel an existing icon loading operation
            if (holder.iconLoader != null) holder.iconLoader.interrupt();
            final ApplicationItem item = mAdapterList.get(position);
            // Add click listeners
            holder.itemView.setOnClickListener(v -> {
                // Click listener: 1) If app not installed, display a toast message saying that it's
                // not installed, 2) If installed, load the App Details page, 3) If selection mode
                // is on, select/deselect the current item instead of 1 & 2.
                if (mActivity.mModel.getSelectedPackages().size() == 0) {
                    if (!item.isInstalled)
                        Toast.makeText(mActivity, R.string.app_not_installed, Toast.LENGTH_SHORT).show();
                    else {
                        Intent appDetailsIntent = new Intent(mActivity, AppDetailsActivity.class);
                        appDetailsIntent.putExtra(AppDetailsActivity.EXTRA_PACKAGE_NAME, item.packageName);
                        mActivity.startActivity(appDetailsIntent);
                    }
                } else toggleSelection(item, position);
            });
            holder.itemView.setOnLongClickListener(v -> {
                // Long click listener: Select/deselect an app. Turn selection mode on if this is
                // the first item in the selection list
                toggleSelection(item, position);
                return true;
            });
            holder.icon.setOnClickListener(v -> toggleSelection(item, position));
            // Alternate background colors: selected > disabled > regular
            if (item.isSelected) holder.mainView.setBackgroundColor(mColorHighlight);
            else if (item.isDisabled) holder.mainView.setBackgroundColor(mColorDisabled);
            else holder.mainView.setBackgroundColor(position % 2 == 0 ? mColorSemiTransparent : mColorTransparent);
            // Add yellow star if the app is in debug mode
            holder.favorite_icon.setVisibility(item.debuggable ? View.VISIBLE : View.INVISIBLE);
            // Set version name
            holder.version.setText(item.versionName);
            // Set date and (if available,) days between first install and last update
            String lastUpdateDate = sSimpleDateFormat.format(new Date(item.lastUpdateTime));
            if (item.firstInstallTime == item.lastUpdateTime)
                holder.date.setText(lastUpdateDate);
            else {
                long days = TimeUnit.DAYS.convert(item.lastUpdateTime - item.firstInstallTime, TimeUnit.MILLISECONDS);
                SpannableString ssDate = new SpannableString(mActivity.getResources()
                        .getQuantityString(R.plurals.main_list_date_days, (int) days, lastUpdateDate, days));
                ssDate.setSpan(new RelativeSizeSpan(.8f), 10, ssDate.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                holder.date.setText(ssDate);
            }
            // Set date color to orange if app can read logs (and accepted)
            if (mPackageManager.checkPermission(Manifest.permission.READ_LOGS, item.packageName)
                    == PackageManager.PERMISSION_GRANTED)
                holder.date.setTextColor(mColorOrange);
            else holder.date.setTextColor(mColorSecondary);
            if (item.isInstalled) {
                // Set kernel user ID
                holder.sharedId.setText(String.valueOf(item.uid));
                // Set kernel user ID text color to orange if the package is shared
                if (item.sharedUserId != null) holder.sharedId.setTextColor(mColorOrange);
                else holder.sharedId.setTextColor(mColorSecondary);
            } else holder.sharedId.setText("");
            if (item.sha != null) {
                // Set issuer
                String issuer;
                try {
                    issuer = "CN=" + (item.sha.getFirst()).split("CN=", 2)[1];
                } catch (ArrayIndexOutOfBoundsException e) {
                    issuer = item.sha.getFirst();
                }
                holder.issuer.setVisibility(View.VISIBLE);
                holder.issuer.setText(issuer);
                // Set signature type
                holder.sha.setVisibility(View.VISIBLE);
                holder.sha.setText(item.sha.getSecond());
            } else {
                holder.issuer.setVisibility(View.GONE);
                holder.sha.setVisibility(View.GONE);
            }
            // Load app icon
            holder.iconLoader = new IconLoaderThread(holder.icon, item);
            holder.iconLoader.start();
            // Set app label
            if (!TextUtils.isEmpty(mSearchQuery) && item.label.toLowerCase(Locale.ROOT).contains(mSearchQuery)) {
                // Highlight searched query
                holder.label.setText(Utils.getHighlightedText(item.label, mSearchQuery, mColorRed));
            } else holder.label.setText(item.label);
            // Set app label color to red if clearing user data not allowed
            if (item.isInstalled && (item.flags & ApplicationInfo.FLAG_ALLOW_CLEAR_USER_DATA) == 0)
                holder.label.setTextColor(Color.RED);
            else holder.label.setTextColor(mColorPrimary);
            // Set package name
            if (!TextUtils.isEmpty(mSearchQuery) && item.packageName.toLowerCase(Locale.ROOT).contains(mSearchQuery)) {
                // Highlight searched query
                holder.packageName.setText(Utils.getHighlightedText(item.packageName, mSearchQuery, mColorRed));
            } else holder.packageName.setText(item.packageName);
            // Set package name color to dark cyan if the app is in stopped/force closed state
            if ((item.flags & ApplicationInfo.FLAG_STOPPED) != 0)
                holder.packageName.setTextColor(mColorStopped);
            else holder.packageName.setTextColor(mColorSecondary);
            // Set version (along with HW accelerated, debug and test only flags)
            CharSequence version = holder.version.getText();
            if (item.isInstalled && (item.flags & ApplicationInfo.FLAG_HARDWARE_ACCELERATED) == 0)
                version = "_" + version;
            if ((item.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) version = "debug" + version;
            if ((item.flags & ApplicationInfo.FLAG_TEST_ONLY) != 0) version = "~" + version;
            holder.version.setText(version);
            // Set version color to dark cyan if the app is inactive
            if (Build.VERSION.SDK_INT >= 23) {
                UsageStatsManager mUsageStats;
                mUsageStats = mActivity.getSystemService(UsageStatsManager.class);
                if (mUsageStats != null && mUsageStats.isAppInactive(item.packageName))
                    holder.version.setTextColor(mColorStopped);
                else holder.version.setTextColor(mColorSecondary);
            }
            // Set app type: system or user app (along with large heap, suspended, multi-arch,
            // has code, vm safe mode)
            String isSystemApp;
            if (item.isInstalled) {
                if ((item.flags & ApplicationInfo.FLAG_SYSTEM) != 0)
                    isSystemApp = mActivity.getString(R.string.system);
                else isSystemApp = mActivity.getString(R.string.user);
                if ((item.flags & ApplicationInfo.FLAG_LARGE_HEAP) != 0) isSystemApp += "#";
                if ((item.flags & ApplicationInfo.FLAG_SUSPENDED) != 0) isSystemApp += "°";
                if ((item.flags & ApplicationInfo.FLAG_MULTIARCH) != 0) isSystemApp += "X";
                if ((item.flags & ApplicationInfo.FLAG_HAS_CODE) == 0) isSystemApp += "0";
                if ((item.flags & ApplicationInfo.FLAG_VM_SAFE_MODE) != 0) isSystemApp += "?";
                holder.isSystemApp.setText(isSystemApp);
                // Set app type text color to magenta if the app is persistent
                if ((item.flags & ApplicationInfo.FLAG_PERSISTENT) != 0)
                    holder.isSystemApp.setTextColor(Color.MAGENTA);
                else holder.isSystemApp.setTextColor(mColorSecondary);
            } else {
                holder.isSystemApp.setText("-");
                holder.isSystemApp.setTextColor(mColorSecondary);
            }
            // Set SDK
            if (item.isInstalled) {
                if (Build.VERSION.SDK_INT >= 26) {
                    holder.size.setText(String.format(Locale.getDefault(), "SDK %d", -item.size));
                } else if (item.size != -1L) {
                    holder.size.setText(Formatter.formatFileSize(mActivity, item.size));
                }
            } else holder.size.setText("-");
            // Set SDK color to orange if the app is using cleartext (e.g. HTTP) traffic
            if ((item.flags & ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) !=0)
                holder.size.setTextColor(mColorOrange);
            else holder.size.setTextColor(mColorSecondary);
            // Check for backup
            if (item.metadataV1 != null) {
                holder.backupIndicator.setVisibility(View.VISIBLE);
                holder.backupInfo.setVisibility(View.VISIBLE);
                holder.backupInfoExt.setVisibility(View.VISIBLE);
                holder.backupIndicator.setText(R.string.backup);
                MetadataManager.MetadataV1 metadataV1 = item.metadataV1;
                long days = TimeUnit.DAYS.convert(System.currentTimeMillis() -
                        metadataV1.backupTime, TimeUnit.MILLISECONDS);
                holder.backupInfo.setText(String.format("%s: %s, %s %s",
                        mActivity.getString(R.string.backup), mActivity.getResources()
                                .getQuantityString(R.plurals.usage_days, (int) days, days),
                        mActivity.getString(R.string.version), metadataV1.versionName));
                StringBuilder extBulder = new StringBuilder();
                if (!TextUtils.isEmpty(metadataV1.sourceDir)) extBulder.append("apk");
                if (metadataV1.dataDirs.length > 0) {
                    if (extBulder.length() > 0) extBulder.append("+");
                    extBulder.append("data");
                }
                if (metadataV1.hasRules) {
                    if (extBulder.length() > 0) extBulder.append("+");
                    extBulder.append("rules");
                }
                holder.backupInfoExt.setText(extBulder.toString());
            } else {
                holder.backupIndicator.setVisibility(View.GONE);
                holder.backupInfo.setVisibility(View.GONE);
                holder.backupInfoExt.setVisibility(View.GONE);
            }
        }

        public void toggleSelection(@NonNull ApplicationItem item, int position) {
            if (mActivity.mModel.getSelectedPackages().contains(item.packageName)) {
                mAdapterList.set(position, mActivity.mModel.deselect(item));
            } else {
                mAdapterList.set(position, mActivity.mModel.select(item));
            }
            notifyItemChanged(position);
            mActivity.handleSelection();
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public int getItemCount() {
            return mAdapterList.size();
        }

        @Override
        public int getPositionForSection(int section) {
            for (int i = 0; i < getItemCount(); i++) {
                String item = mAdapterList.get(i).label;
                if (item.length() > 0) {
                    if (item.charAt(0) == sections.charAt(section))
                        return i;
                }
            }
            return 0;
        }

        @Override
        public int getSectionForPosition(int i) {
            return 0;
        }

        @Override
        public Object[] getSections() {
            String[] sectionsArr = new String[sections.length()];
            for (int i = 0; i < sections.length(); i++)
                sectionsArr[i] = "" + sections.charAt(i);

            return sectionsArr;
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            View mainView;
            ImageView icon;
            ImageView favorite_icon;
            TextView label;
            TextView packageName;
            TextView version;
            TextView isSystemApp;
            TextView date;
            TextView size;
            TextView sharedId;
            TextView issuer;
            TextView sha;
            TextView backupIndicator;
            TextView backupInfo;
            TextView backupInfoExt;
            IconLoaderThread iconLoader;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                mainView = itemView.findViewById(R.id.main_view);
                icon = itemView.findViewById(R.id.icon);
                favorite_icon = itemView.findViewById(R.id.favorite_icon);
                label = itemView.findViewById(R.id.label);
                packageName = itemView.findViewById(R.id.packageName);
                version = itemView.findViewById(R.id.version);
                isSystemApp = itemView.findViewById(R.id.isSystem);
                date = itemView.findViewById(R.id.date);
                size = itemView.findViewById(R.id.size);
                sharedId = itemView.findViewById(R.id.shareid);
                issuer = itemView.findViewById(R.id.issuer);
                sha = itemView.findViewById(R.id.sha);
                backupIndicator = itemView.findViewById(R.id.backup_indicator);
                backupInfo = itemView.findViewById(R.id.backup_info);
                backupInfoExt = itemView.findViewById(R.id.backup_info_ext);
            }
        }
    }
}
