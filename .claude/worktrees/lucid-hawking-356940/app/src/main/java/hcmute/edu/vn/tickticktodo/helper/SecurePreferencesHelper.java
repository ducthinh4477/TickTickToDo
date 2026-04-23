package hcmute.edu.vn.doinbot.helper;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

public class SecurePreferencesHelper {

    public static final String DEFAULT_MODEL = "";

    private static final String TAG = "SecurePreferencesHelper";
    private static final String SECURE_PREFS_NAME = "secure_ai_settings";
    private static final String FALLBACK_PREFS_NAME = "ai_settings_fallback";
    private static final String KEY_API_KEY = "ai_api_key";
    private static final String KEY_API_KEY_HISTORY = "ai_api_key_history";
    private static final String KEY_AI_MODEL = "ai_model";
    private static final String KEY_AI_MODELS = "ai_model_options";
    private static final int MAX_KEY_HISTORY = 10;

    private static volatile SecurePreferencesHelper instance;

    private final SharedPreferences preferences;

    private SecurePreferencesHelper(Context context) {
        Context appContext = context.getApplicationContext();
        this.preferences = createPreferences(appContext);
    }

    public synchronized void saveApiKeyForModel(String modelName, String apiKey) {
        String modelPreferenceKey = buildModelApiKeyPreferenceKey(modelName);
        String normalizedApiKey = normalizeKeyForStorage(apiKey);

        if (!TextUtils.isEmpty(modelPreferenceKey)) {
            preferences.edit().putString(modelPreferenceKey, normalizedApiKey).apply();
        }

        saveApiKey(normalizedApiKey);
    }

    public synchronized String getApiKeyForModel(String modelName) {
        String keyForModel = buildModelApiKeyPreferenceKey(modelName);
        if (TextUtils.isEmpty(keyForModel)) {
            return "";
        }

        String modelKey = preferences.getString(keyForModel, "");
        if (!TextUtils.isEmpty(modelKey)) {
            return modelKey.trim();
        }

        // Backward-compat for older builds that used raw model text in preference key.
        String legacyKeyForModel = KEY_API_KEY + "_" + normalizeModel(modelName);
        String legacyModelKey = preferences.getString(legacyKeyForModel, "");
        if (!TextUtils.isEmpty(legacyModelKey)) {
            String normalizedLegacyModelKey = legacyModelKey.trim();
            preferences.edit()
                    .putString(keyForModel, normalizedLegacyModelKey)
                    .remove(legacyKeyForModel)
                    .apply();
            return normalizedLegacyModelKey;
        }

        return modelKey == null ? "" : modelKey.trim();
    }

    public static SecurePreferencesHelper getInstance(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context must not be null.");
        }
        if (instance == null) {
            synchronized (SecurePreferencesHelper.class) {
                if (instance == null) {
                    instance = new SecurePreferencesHelper(context);
                }
            }
        }
        return instance;
    }

    public synchronized void saveApiKey(String key) {
        String normalized = normalizeKeyForStorage(key);
        preferences.edit().putString(KEY_API_KEY, normalized).apply();
        if (!TextUtils.isEmpty(normalized)) {
            addApiKeyToHistory(normalized);
        }
    }

    /** Adds a key to the saved-key history (max {@value MAX_KEY_HISTORY} entries). */
    public synchronized void addApiKeyToHistory(String key) {
        String normalized = normalizeKeyForStorage(key);
        if (TextUtils.isEmpty(normalized)) return;
        LinkedHashSet<String> history = new LinkedHashSet<>(getApiKeyHistoryInternal());
        // Remove then re-add to bump to end (most-recently-used order)
        history.remove(normalized);
        history.add(normalized);
        // Trim to max size from the front (oldest first)
        while (history.size() > MAX_KEY_HISTORY) {
            history.remove(history.iterator().next());
        }
        preferences.edit().putStringSet(KEY_API_KEY_HISTORY, history).apply();
    }

    /** Returns all saved API keys, most-recently-used last. */
    public synchronized List<String> getSavedApiKeys() {
        return new ArrayList<>(getApiKeyHistoryInternal());
    }

    private Set<String> getApiKeyHistoryInternal() {
        Set<String> raw = preferences.getStringSet(KEY_API_KEY_HISTORY, new LinkedHashSet<>());
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (raw == null) return result;
        for (String item : raw) {
            String normalized = normalizeKeyForStorage(item);
            if (!TextUtils.isEmpty(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    public synchronized String getApiKey() {
        return getStoredApiKey();
    }

    public synchronized String getStoredApiKey() {
        String value = preferences.getString(KEY_API_KEY, "");
        return value == null ? "" : value.trim();
    }

    public synchronized void saveAiModel(String modelName) {
        String normalized = normalizeModel(modelName);
        if (TextUtils.isEmpty(normalized)) {
            preferences.edit().remove(KEY_AI_MODEL).apply();
            saveApiKey("");
            return;
        }

        addAiModel(normalized);
        preferences.edit().putString(KEY_AI_MODEL, normalized).apply();
        saveApiKey(getApiKeyForModel(normalized));
    }

    public synchronized String getAiModel() {
        String selectedModel = normalizeModel(preferences.getString(KEY_AI_MODEL, DEFAULT_MODEL));
        if (!TextUtils.isEmpty(selectedModel)) {
            addAiModel(selectedModel);
            return selectedModel;
        }

        List<String> options = getAiModelOptions();
        if (options.isEmpty()) {
            return DEFAULT_MODEL;
        }
        return options.get(0);
    }

    public synchronized boolean addAiModel(String modelName) {
        String normalized = normalizeModel(modelName);
        if (TextUtils.isEmpty(normalized)) {
            return false;
        }

        LinkedHashSet<String> models = new LinkedHashSet<>(getModelSetInternal());
        boolean added = models.add(normalized);
        preferences.edit().putStringSet(KEY_AI_MODELS, models).apply();
        return added;
    }

    public synchronized List<String> getAiModelOptions() {
        LinkedHashSet<String> models = new LinkedHashSet<>(getModelSetInternal());
        String selectedModel = normalizeModel(preferences.getString(KEY_AI_MODEL, DEFAULT_MODEL));
        if (!TextUtils.isEmpty(selectedModel)) {
            models.add(selectedModel);
        }
        return new ArrayList<>(models);
    }

    private SharedPreferences createPreferences(Context context) {
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    context,
                    SECURE_PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException exception) {
            Log.w(TAG, "Encrypted prefs unavailable, using fallback preferences.", exception);
            return context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    private String normalizeKeyForStorage(String key) {
        if (key == null) {
            return "";
        }
        return key.trim();
    }

    private String normalizeModel(String modelName) {
        if (modelName == null) {
            return DEFAULT_MODEL;
        }
        return modelName.trim();
    }

    private String buildModelApiKeyPreferenceKey(String modelName) {
        String normalizedModelPreferenceKey = normalizeModelPreferenceKey(modelName);
        if (TextUtils.isEmpty(normalizedModelPreferenceKey)) {
            return "";
        }
        return KEY_API_KEY + "_" + normalizedModelPreferenceKey;
    }

    private String normalizeModelPreferenceKey(String modelName) {
        String normalizedModel = normalizeModel(modelName);
        if (TextUtils.isEmpty(normalizedModel)) {
            return "";
        }
        return normalizedModel
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "_");
    }

    private Set<String> getModelSetInternal() {
        Set<String> raw = preferences.getStringSet(KEY_AI_MODELS, new LinkedHashSet<>());
        LinkedHashSet<String> normalizedSet = new LinkedHashSet<>();
        if (raw == null) {
            return normalizedSet;
        }

        for (String item : raw) {
            String normalized = normalizeModel(item);
            if (!TextUtils.isEmpty(normalized)) {
                normalizedSet.add(normalized);
            }
        }
        return normalizedSet;
    }
}
