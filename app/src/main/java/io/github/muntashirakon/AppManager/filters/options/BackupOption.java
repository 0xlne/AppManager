// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.filters.options;

import static io.github.muntashirakon.AppManager.backup.BackupFlags.BACKUP_APK_FILES;
import static io.github.muntashirakon.AppManager.backup.BackupFlags.BACKUP_CACHE;
import static io.github.muntashirakon.AppManager.backup.BackupFlags.BACKUP_EXTRAS;
import static io.github.muntashirakon.AppManager.backup.BackupFlags.BACKUP_EXT_DATA;
import static io.github.muntashirakon.AppManager.backup.BackupFlags.BACKUP_EXT_OBB_MEDIA;
import static io.github.muntashirakon.AppManager.backup.BackupFlags.BACKUP_INT_DATA;
import static io.github.muntashirakon.AppManager.backup.BackupFlags.BACKUP_RULES;

import android.content.Context;
import android.text.SpannableStringBuilder;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.muntashirakon.AppManager.db.entity.Backup;
import io.github.muntashirakon.AppManager.filters.IFilterableAppInfo;
import io.github.muntashirakon.AppManager.utils.DateUtils;

public class BackupOption extends FilterOption {
    private final Map<String, Integer> mKeysWithType = new LinkedHashMap<String, Integer>() {{
        put(KEY_ALL, TYPE_NONE);
        put("backups", TYPE_NONE);
        put("no_backups", TYPE_NONE);
        put("latest_backup", TYPE_NONE);
        put("outdated_backup", TYPE_NONE);
        put("made_before", TYPE_TIME_MILLIS);
        put("made_after", TYPE_TIME_MILLIS);
        put("with_flags", TYPE_INT_FLAGS);
        put("without_flags", TYPE_INT_FLAGS);
    }};

    private final Map<Integer, CharSequence> mBackupFlags = new LinkedHashMap<Integer, CharSequence>() {{
        put(BACKUP_APK_FILES, "Apk files");
        put(BACKUP_INT_DATA, "Internal data");
        put(BACKUP_EXT_DATA, "External data");
        put(BACKUP_EXT_OBB_MEDIA, "OBB and media");
        put(BACKUP_CACHE, "Cache");
        put(BACKUP_EXTRAS, "Extras");
        put(BACKUP_RULES, "Rules");
    }};

    public BackupOption() {
        super("backup");
    }

    @NonNull
    @Override
    public Map<String, Integer> getKeysWithType() {
        return mKeysWithType;
    }

    @Override
    public Map<Integer, CharSequence> getFlags(@NonNull String key) {
        if (key.equals("with_flags") || key.equals("without_flags")) {
            return mBackupFlags;
        }
        return super.getFlags(key);
    }

    @NonNull
    @Override
    public TestResult test(@NonNull IFilterableAppInfo info, @NonNull TestResult result) {
        List<Backup> backups = result.getMatchedBackups() != null
                ? result.getMatchedBackups()
                : Arrays.asList(info.getBackups());
        switch (key) {
            case KEY_ALL:
                return result.setMatched(true).setMatchedBackups(backups);
            case "backups": {
                if (!backups.isEmpty()) {
                    return result.setMatched(true).setMatchedBackups(backups);
                } else return result.setMatched(false).setMatchedBackups(Collections.emptyList());
            }
            case "no_backups": {
                if (backups.isEmpty()) {
                    return result.setMatched(true).setMatchedBackups(Collections.emptyList());
                } else return result.setMatched(false).setMatchedBackups(backups);
            }
            case "latest_backup": {
                if (!info.isInstalled()) {
                    // If the app isn't install, all backups are latest
                    return result.setMatched(true).setMatchedBackups(backups);
                }
                List<Backup> matchedBackups = new ArrayList<>();
                long versionCode = info.getVersionCode();
                for (Backup backup : backups) {
                    if (backup.versionCode >= versionCode) {
                        matchedBackups.add(backup);
                    }
                }
                return result.setMatched(!matchedBackups.isEmpty())
                        .setMatchedBackups(matchedBackups);
            }
            case "outdated_backup": {
                if (!info.isInstalled()) {
                    // If the app isn't install, no backups are outdated
                    return result.setMatched(false);
                }
                List<Backup> matchedBackups = new ArrayList<>();
                long versionCode = info.getVersionCode();
                for (Backup backup : backups) {
                    if (backup.versionCode < versionCode) {
                        matchedBackups.add(backup);
                    }
                }
                return result.setMatched(!matchedBackups.isEmpty())
                        .setMatchedBackups(matchedBackups);
            }
            case "made_before": {
                List<Backup> matchedBackups = new ArrayList<>();
                for (Backup backup : backups) {
                    if (backup.backupTime <= longValue) {
                        matchedBackups.add(backup);
                    }
                }
                return result.setMatched(!matchedBackups.isEmpty())
                        .setMatchedBackups(matchedBackups);
            }
            case "made_after": {
                List<Backup> matchedBackups = new ArrayList<>();
                for (Backup backup : backups) {
                    if (backup.backupTime >= longValue) {
                        matchedBackups.add(backup);
                    }
                }
                return result.setMatched(!matchedBackups.isEmpty())
                        .setMatchedBackups(matchedBackups);
            }
            case "with_flags": {
                List<Backup> matchedBackups = new ArrayList<>();
                for (Backup backup : backups) {
                    if ((backup.flags & intValue) == intValue) {
                        matchedBackups.add(backup);
                    }
                }
                return result.setMatched(!matchedBackups.isEmpty())
                        .setMatchedBackups(matchedBackups);
            }
            case "without_flags": {
                List<Backup> matchedBackups = new ArrayList<>();
                for (Backup backup : backups) {
                    if ((backup.flags & intValue) != intValue) {
                        matchedBackups.add(backup);
                    }
                }
                return result.setMatched(!matchedBackups.isEmpty())
                        .setMatchedBackups(matchedBackups);
            }
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }


    @NonNull
    @Override
    public CharSequence toLocalizedString(@NonNull Context context) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        switch (key) {
            case KEY_ALL:
                return "Apps with or without backups";
            case "backups":
                return "Only the apps with backups";
            case "no_backups":
                return "only the apps without backups";
            case "latest_backup":
                return "Only the apps having the latest backups";
            case "outdated_backup":
                return "Only the apps having some outdated backups";
            case "made_before":
                return sb.append("Only the apps with backups made before ").append(DateUtils.formatDate(context, longValue));
            case "made_after":
                return sb.append("Only the apps with backups made after ").append(DateUtils.formatDate(context, longValue));
            case "with_flags":
                return sb.append("Only the apps having backups with the flags ").append(flagsToString("with_flags", intValue));
            case "without_flags":
                return sb.append("Only the apps having backups without the flags ").append(flagsToString("without_flags", intValue));
            default:
                throw new UnsupportedOperationException("Invalid key " + key);
        }
    }
}
