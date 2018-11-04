package solutions.gm5.flbtclassicspp;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.PermissionChecker;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * FlbtClassicSppPlugin
 */
public class FlbtClassicSppPlugin implements MethodCallHandler, PluginRegistry.RequestPermissionsResultListener, EventChannel.StreamHandler {
    private final UUID sppUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private final String dataStream = "gm5.solutions.flbt_classic_spp_plugin/dataStream";

    private static Registrar registrar;
    private static MethodChannel channel;

    private BluetoothAdapter bluetoothAdapter;
    private HashMap<String, BtEntry> bluetoothDevices = new HashMap<>();

    private class BtEntry {
        BluetoothDevice device;
        BluetoothSocket btSocket;
        InputStream reader;
        BufferedOutputStream writer;
        Thread thread;
        String identifier;

        BtEntry(BluetoothDevice d, BluetoothSocket bs, BufferedOutputStream writer, InputStream reader, String identifier, Thread thread) {
            device = d;
            btSocket = bs;
            this.reader = reader;
            this.writer = writer;
            this.thread = thread;
            this.identifier = identifier;
        }
    }

    /**
     * Plugin registration.
     */
    public static void registerWith(Registrar reg) {
        channel = new MethodChannel(reg.messenger(), "flbt_classic_spp");
        channel.setMethodCallHandler(new FlbtClassicSppPlugin());
        registrar = reg;
    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        switch (call.method) {
            case "getPlatformVersion":
                result.success("Android " + android.os.Build.VERSION.RELEASE);
                break;
            case "init":
                init(result);
                break;
            case "connect":
                connectBt(result, (String) call.argument("name"), (String) call.argument("uuid"));
                break;
            case "write":
                String identifier = (String) call.argument("identifier");
                if (identifier == null) {
                    result.error("NullException", "Identifier is null.", null);
                    break;
                }
                if (!bluetoothDevices.containsKey(identifier)) {
                    result.error("DeviceNotExisting", "Device with identifier " + identifier + " does not exist!", null);
                    break;
                }
                BtEntry btEntry = bluetoothDevices.get(identifier);
                byte[] data = call.argument("payload");
                try {
                    btEntry.writer.write(data);
                    btEntry.writer.flush();
                } catch (IOException e) {
                    result.error("WriteException", "Could not write data: " + e.toString(), null);
                    break;
                }
                result.success(null);
                break;
            default:
                result.notImplemented();
                break;
        }
    }

    private void connectBt(final Result result, final String name, final String uuid) {
        if (bluetoothAdapter == null) {
            result.error("NoBluetoothAdapter", "Did you call init?", null);
            return;
        }
        BluetoothDevice device = null;
        if (name != null) {
            device = findByName(name);
        } else if (uuid != null) {
            device = findByUuid(uuid);
        }

        if (device == null) {
            result.error("BluetoothDeviceNotFound", "Bt device " + (name == null ? "" : name) + (uuid == null ? "" : uuid) + " with passed params was not found", null);
            return;
        }


        final BluetoothDevice finalDevice = device;
        new Thread(new Runnable() {
            @Override
            public void run() {
                BluetoothSocket btSocket;
                try {
                    btSocket = finalDevice.createRfcommSocketToServiceRecord(sppUuid);
                } catch (IOException ex) {
//                    result.error("CreateBtSocketException", "Failed to create RfComm socket: " + ex.toString(), null);
                    return;
                }
                for (int i = 0; ; i++) {
                    try {
                        btSocket.connect();
                    } catch (IOException ex) {
                        if (i < 5) {
                            Log.d("FlbtClassicSppPlugin", "Failed to connect. Retrying.");
                            continue;
                        }
//                        result.error("ConnectException", "Failed to connect: " + ex.toString(), null);
                        return;
                    }
                    break;
                }

                BufferedOutputStream writer;
                InputStream reader;
                try {
                    writer = new BufferedOutputStream(btSocket.getOutputStream());
                    reader = btSocket.getInputStream();
                } catch (IOException ex) {
//                    result.error("ConnectException", "Failed to get input / output stream: " + ex.toString(), null);
                    return;
                }
                String identifier = name == null ? uuid : name;
                BtEntry btEntry = new BtEntry(finalDevice, btSocket, writer, reader, identifier, startDeviceReadThread(identifier, reader));
                bluetoothDevices.put(identifier, btEntry);
                HashMap<String, Object> args = new HashMap<>();
                args.put("identifier", identifier);
                args.put("uuid", finalDevice.getAddress());
                channel.invokeMethod("connected", args);
            }
        }).start();
        result.success(null);
    }

    private BluetoothDevice findByName(String name) {
        if (bluetoothAdapter == null) return null;
        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice dev : bondedDevices) {
            if (dev.getName().equals(name)) {
                return dev;
            }
        }
        return null;
    }

    private BluetoothDevice findByUuid(String uuid) {
        if (bluetoothAdapter == null) return null;
        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice dev : bondedDevices) {
            if (dev.getAddress().equals(uuid)) {
                return dev;
            }
        }
        return null;
    }

    private void init(Result result) {
        requestPermissions();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            result.error("BluetoothInitException", "Bluetooth adapter not available!", null);
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            result.error("BluetoothInitException", "Bluetooth is disabled. Check configuration.", null);
            return;
        }

        new EventChannel(registrar.messenger(), dataStream).setStreamHandler(this);

        channel.invokeMethod("initComplete", null);
    }

    private void requestPermissions() {
        registrar.addRequestPermissionsResultListener(this);
        ArrayList<String> permissions = new ArrayList<>();
        int perm = PermissionChecker.checkSelfPermission(registrar.activity(), Manifest.permission.ACCESS_COARSE_LOCATION);
        if (perm != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        perm = PermissionChecker.checkSelfPermission(registrar.activity(), Manifest.permission.BLUETOOTH);
        if (perm != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BLUETOOTH);
        }
        perm = PermissionChecker.checkSelfPermission(registrar.activity(), Manifest.permission.ACCESS_FINE_LOCATION);
        if (perm != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (permissions.size() > 0) {
            ActivityCompat.requestPermissions(registrar.activity(), permissions.toArray(new String[0]), 1);
        }
    }

    @Override
    public boolean onRequestPermissionsResult(int code, String[] permissions, int[] grantResults) {
        for (int i = 0; i < permissions.length; i++) {
            int grantResult = grantResults[i];
            if (grantResult != PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        init(new Result() {
            @Override
            public void success(Object o) {

            }

            @Override
            public void error(String s, String s1, Object o) {

            }

            @Override
            public void notImplemented() {

            }
        });
        return true;
    }

    private Thread startDeviceReadThread(final String identifier, final InputStream reader) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                ArrayList<Object> data = new ArrayList<>();
                data.add(identifier);
                data.add(null);
                while(true) {
                    try {
                        data.set(1, reader.read());
                        if (eventSink != null) {
                            eventSink.success(data);
                        }
                    } catch (IOException e) {
                        channel.invokeMethod("disconnected", identifier);
                    }
                }
            }
        });
        t.start();
        return t;
    }

    private EventChannel.EventSink eventSink;

    @Override
    public void onListen(Object o, EventChannel.EventSink eventSink) {
        this.eventSink = eventSink;
    }

    @Override
    public void onCancel(Object o) {
        eventSink = null;
    }
}

