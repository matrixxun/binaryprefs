package com.ironz.binaryprefs.events;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Handler;
import android.os.Process;
import com.ironz.binaryprefs.Preferences;
import com.ironz.binaryprefs.cache.CacheProvider;
import com.ironz.binaryprefs.encryption.ByteEncryption;
import com.ironz.binaryprefs.serialization.SerializerFactory;
import com.ironz.binaryprefs.task.TaskExecutor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Uses global broadcast receiver mechanism for delivering all key change events.
 * Main propose for using this implementation is IPC mechanism.
 * This bridge optimized even if broadcast comes in local process.
 * Uses UI thread for delivering key changes.
 */
@SuppressWarnings("unused")
public final class BroadcastEventBridgeImpl implements EventBridge {

    private static final String INTENT_PREFIX = "com.ironz.binaryprefs.";
    private static final String ACTION_PREFERENCE_UPDATED = INTENT_PREFIX + "ACTION_PREFERENCE_UPDATED_";
    private static final String ACTION_PREFERENCE_REMOVED = INTENT_PREFIX + "ACTION_PREFERENCE_REMOVED_";

    private static final String PREFERENCE_NAME = "preference_name";
    private static final String PREFERENCE_KEY = "preference_key";
    private static final String PREFERENCE_VALUE = "preference_value";
    private static final String PREFERENCE_PROCESS_ID = "preference_process_id";

    private final List<OnSharedPreferenceChangeListener> listeners = new CopyOnWriteArrayList<>();
    private final Handler handler = new Handler();

    private final Context context;
    private final String prefName;
    private final CacheProvider cacheProvider;
    private final SerializerFactory serializerFactory;
    private final TaskExecutor taskExecutor;
    private final ByteEncryption byteEncryption;

    private final String updateActionName;
    private final String removeActionName;
    private final int processId;

    private Preferences preferences;

    public BroadcastEventBridgeImpl(Context context,
                                    String prefName,
                                    CacheProvider cacheProvider,
                                    SerializerFactory serializerFactory,
                                    TaskExecutor taskExecutor,
                                    ByteEncryption byteEncryption) {
        this.context = context;
        this.prefName = prefName;
        this.cacheProvider = cacheProvider;
        this.serializerFactory = serializerFactory;
        this.taskExecutor = taskExecutor;
        this.byteEncryption = byteEncryption;

        this.updateActionName = ACTION_PREFERENCE_UPDATED + context.getPackageName();
        this.removeActionName = ACTION_PREFERENCE_REMOVED + context.getPackageName();
        this.context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                notifyUpdate(intent);
            }
        }, new IntentFilter(updateActionName));
        this.context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                notifyRemove(intent);
            }
        }, new IntentFilter(removeActionName));
        this.processId = Process.myPid();
    }

    private void notifyUpdate(final Intent intent) {
        if (!prefName.equals(intent.getStringExtra(PREFERENCE_NAME))) {
            return;
        }
        if (processId == intent.getIntExtra(PREFERENCE_PROCESS_ID, 0)) {
            return;
        }

        taskExecutor.submit(new Runnable() {
            @Override
            public void run() {
                notifyUpdateInternal(intent);
            }
        });
    }

    private void notifyUpdateInternal(Intent intent) {
        final String key = intent.getStringExtra(PREFERENCE_KEY);
        byte[] value = intent.getByteArrayExtra(PREFERENCE_VALUE);

        Object o = fetchObject(key, value);
        update(key, o);
    }

    private void notifyRemove(final Intent intent) {
        if (!prefName.equals(intent.getStringExtra(PREFERENCE_NAME))) {
            return;
        }
        if (processId == intent.getIntExtra(PREFERENCE_PROCESS_ID, 0)) {
            return;
        }

        taskExecutor.submit(new Runnable() {
            @Override
            public void run() {
                notifyRemoveInternal(intent);
            }
        });
    }

    private void notifyRemoveInternal(Intent intent) {
        final String key = intent.getStringExtra(PREFERENCE_KEY);
        remove(key);
    }

    private Object fetchObject(String key, byte[] bytes) {
        byte[] decrypt = byteEncryption.decrypt(bytes);
        return serializerFactory.deserialize(key, decrypt);
    }

    public void definePreferences(Preferences preferences) {
        this.preferences = preferences;
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        listeners.add(listener);
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void notifyListenersUpdate(Preferences preferences, String key, Object value) {
        update(key, value);
        sendUpdateIntent(key, value);
    }

    @Override
    public void notifyListenersRemove(Preferences preferences, String key) {
        remove(key);
        sendRemoveIntent(key);
    }

    private void update(String key, Object value) {
        cacheProvider.put(key, value);
        notifyListeners(key);
    }

    private void remove(String key) {
        cacheProvider.remove(key);
        notifyListeners(key);
    }

    private void notifyListeners(final String key) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                for (OnSharedPreferenceChangeListener listener : listeners) {
                    listener.onSharedPreferenceChanged(preferences, key);
                }
            }
        });
    }

    private void sendUpdateIntent(final String key, final Object value) {
        taskExecutor.submit(new Runnable() {
            @Override
            public void run() {
                sendUpdateIntentInternal(value, key);
            }
        });
    }

    private void sendRemoveIntent(final String key) {
        taskExecutor.submit(new Runnable() {
            @Override
            public void run() {
                sendRemoveIntentInternal(key);
            }
        });
    }

    private void sendUpdateIntentInternal(Object value, String key) {
        byte[] bytes = serializerFactory.serialize(value);
        byte[] encrypt = byteEncryption.encrypt(bytes);
        Intent intent = new Intent(updateActionName);
        intent.putExtra(PREFERENCE_PROCESS_ID, processId);
        intent.putExtra(PREFERENCE_NAME, prefName);
        intent.putExtra(PREFERENCE_KEY, key);
        intent.putExtra(PREFERENCE_VALUE, encrypt);
        context.sendBroadcast(intent);
    }

    private void sendRemoveIntentInternal(String key) {
        Intent intent = new Intent(removeActionName);
        intent.putExtra(PREFERENCE_PROCESS_ID, processId);
        intent.putExtra(PREFERENCE_NAME, prefName);
        intent.putExtra(PREFERENCE_KEY, key);
        context.sendBroadcast(intent);
    }
}