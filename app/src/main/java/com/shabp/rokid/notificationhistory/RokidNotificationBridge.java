package com.shabp.rokid.notificationhistory;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

/**
 * Minimal client for Rokid Glass3's documented notification AIDL service.
 * It intentionally implements only the binder calls this app needs, avoiding unrelated SDK code.
 */
final class RokidNotificationBridge {
    private static final String TAG = "RokidNotifBridge";
    private static final String SERVER_PACKAGE = "com.rokid.security.system.server";
    private static final String SERVER_CLASS =
            "com.rokid.security.system.server.SecurityCoreService";
    private static final String POOL_DESCRIPTOR =
            "com.rokid.security.system.server.IServicePool";
    private static final String CLIENT_DESCRIPTOR =
            "com.rokid.security.system.server.IClientCallback";
    private static final String NOTIFICATION_DESCRIPTOR =
            "com.rokid.security.system.server.notification.INotificationService";
    private static final String LISTENER_DESCRIPTOR =
            "com.rokid.security.system.server.notification.listener.NotificationListener";

    private static Context appContext;
    private static boolean binding;
    private static boolean bound;
    private static IBinder notificationService;
    private static volatile String status = "START";

    private static final Binder clientCallback = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(CLIENT_DESCRIPTOR);
                return true;
            }
            if (code == 1) {
                data.enforceInterface(CLIENT_DESCRIPTOR);
                reply.writeNoException();
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    };

    private static final Binder notificationCallback = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(LISTENER_DESCRIPTOR);
                return true;
            }
            if (code == 1) {
                data.enforceInterface(LISTENER_DESCRIPTOR);
                if (data.readInt() != 0) {
                    String pkg = safe(data.readString());
                    String app = safe(data.readString());
                    String title = safe(data.readString());
                    String body = safe(data.readString());
                    long time = data.readLong();
                    save(pkg, app, title, body, time);
                }
                reply.writeNoException();
                return true;
            }
            if (code == 2) {
                data.enforceInterface(LISTENER_DESCRIPTOR);
                data.createByteArray();
                data.createByteArray();
                String message = safe(data.readString());
                save("rokid.face", "Rokid", "Face alert", message,
                        System.currentTimeMillis());
                reply.writeNoException();
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }
    };

    private static final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder pool) {
            binding = false;
            bound = true;
            setStatus("BOUND");
            try {
                try {
                    registerClient(pool);
                } catch (Exception registrationError) {
                    Log.w(TAG, "Client registration was rejected; continuing", registrationError);
                }
                notificationService = getNotificationService(pool);
                if (notificationService != null) {
                    setListener(notificationService, notificationCallback);
                    setStatus("READY");
                } else {
                    setStatus("NO SERVICE");
                }
                Log.i(TAG, notificationService == null ? "Notification service unavailable" :
                        "Rokid notification listener registered");
            } catch (Exception e) {
                setStatus("DENIED");
                Log.e(TAG, "Could not register Rokid notification listener", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            binding = false;
            notificationService = null;
            setStatus("DISCONNECTED");
        }

        @Override
        public void onBindingDied(ComponentName name) {
            onServiceDisconnected(name);
            if (appContext != null) start(appContext);
        }
    };

    static synchronized void start(Context context) {
        appContext = context.getApplicationContext();
        if (binding || bound) return;
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(SERVER_PACKAGE, SERVER_CLASS));
        try {
            binding = true;
            setStatus("BIND");
            if (!appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                binding = false;
                setStatus("NOT FOUND");
            }
        } catch (Exception e) {
            binding = false;
            setStatus("BIND ERROR");
            Log.e(TAG, "Rokid service bind failed", e);
        }
    }

    static synchronized void stop() {
        if (notificationService != null) {
            Parcel data = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(NOTIFICATION_DESCRIPTOR);
                notificationService.transact(2, data, reply, 0);
                reply.readException();
            } catch (Exception ignored) {
            } finally {
                reply.recycle();
                data.recycle();
            }
        }
        if (bound && appContext != null) {
            try { appContext.unbindService(connection); } catch (Exception ignored) { }
        }
        notificationService = null;
        bound = false;
        binding = false;
        setStatus("STOPPED");
    }

    private static void registerClient(IBinder pool) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(POOL_DESCRIPTOR);
            data.writeInt(Process.myPid());
            data.writeInt(Process.myUid());
            data.writeString(appContext.getPackageName());
            data.writeString("NotificationHistory");
            data.writeStrongBinder(clientCallback);
            pool.transact(1, data, reply, 0);
            reply.readException();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private static IBinder getNotificationService(IBinder pool) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(POOL_DESCRIPTOR);
            data.writeInt(Process.myPid());
            data.writeInt(Process.myUid());
            data.writeString(appContext.getPackageName());
            data.writeString("Notification");
            pool.transact(3, data, reply, 0);
            reply.readException();
            return reply.readStrongBinder();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private static void setListener(IBinder service, IBinder listener) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(NOTIFICATION_DESCRIPTOR);
            data.writeStrongBinder(listener);
            service.transact(1, data, reply, 0);
            reply.readException();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    private static void save(String pkg, String app, String title, String body, long time) {
        if (appContext == null) return;
        setStatus("EVENT");
        HistoryStore store = new HistoryStore(appContext);
        store.saveRokid(pkg, app, title, body, time);
        store.close();
        appContext.sendBroadcast(new Intent(NotificationCollectorService.ACTION_HISTORY_CHANGED)
                .setPackage(appContext.getPackageName()));
    }

    private static String safe(String value) { return value == null ? "" : value; }

    static String getStatus() { return status; }

    private static void setStatus(String value) {
        status = value;
        if (appContext != null) {
            appContext.sendBroadcast(new Intent(NotificationCollectorService.ACTION_HISTORY_CHANGED)
                    .setPackage(appContext.getPackageName()));
        }
    }

    private RokidNotificationBridge() { }
}
