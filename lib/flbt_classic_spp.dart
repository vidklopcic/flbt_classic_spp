import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/services.dart';

enum FlbtConnectionState { disconnected, connected }

typedef void FlbtConnStateChange(FlbtDevice device);
typedef void FlbtOnData(int byte);

class FlbtDevice {
  static const _KEY_UUID = "uuid";
  static const _KEY_IDENTIFIER = "identifier";
  String identifier;
  String uuid;
  FlbtConnectionState _connState = FlbtConnectionState.disconnected;

  FlbtConnectionState get connState => _connState;

  set connState(FlbtConnectionState state) {
    if (onConnStateChange != null) onConnStateChange(this);
    _connState = state;
  }

  FlbtConnStateChange onConnStateChange;
  FlbtOnData onData;

  FlbtDevice.fromMap(Map<String, dynamic> map) {
    uuid = map[_KEY_UUID];
    identifier = map[_KEY_IDENTIFIER];
  }

  Map<String, dynamic> toMap() {
    Map map = {};
    map[_KEY_UUID] = uuid;
    map[_KEY_IDENTIFIER] = identifier;
    return map;
  }
}

class FlbtClassicSpp {
  static const String _methodConnect = "connect";

  static const MethodChannel _channel =
  const MethodChannel('flbt_classic_spp');

  static const dataStream =
  const EventChannel("gm5.solutions.flbt_classic_spp_plugin/dataStream");


  // instance
  FlbtClassicSpp._() {
    _channel.setMethodCallHandler(_flbtMethodHandler);
    dataStream.receiveBroadcastStream().listen(_onBtData);
  }

  static FlbtClassicSpp _instance = FlbtClassicSpp._();

  static FlbtClassicSpp get instance => _instance;

  // state
  Map<String, FlbtDevice> _devices = {};
  Map<String, Completer> _awaitingConnect = {};
  Completer _awaitingInit;


  static Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  Future<bool> init() async {
    await _channel.invokeMethod("init");
    _awaitingInit = new Completer();
    final bool success = await _awaitingInit.future;
    return success;
  }

  Future<bool> writeString(FlbtDevice device, String data) {
    return writeBytes(device, AsciiEncoder().convert(data));
  }
  
  Future<bool> writeBytes(FlbtDevice device, Uint8List data) async {
    final bool result = await _channel.invokeMethod("write", {'identifier': device.identifier, 'payload': data});
    return result;
  }

  Future<FlbtDevice> connectByName(String name,
      {Duration timeout = const Duration(seconds: 5)}) async {
    if (_devices[name]?.connState == FlbtConnectionState.connected ?? false)
      return _devices[name];
    return await _connect(name, null, timeout);
  }

  Future<FlbtDevice> connectByUuid(String uuid,
      {Duration timeout = const Duration(seconds: 5)}) async {
    if (_devices[uuid]?.connState == FlbtConnectionState.connected ?? false)
      return _devices[uuid];
    return await _connect(null, uuid, timeout);
  }

  Future<FlbtDevice> _connect(String name, String uuid,
      Duration timeout) async {
    Completer completer = Completer();
    _awaitingConnect[name ?? uuid] = completer;
    await _channel.invokeMethod(_methodConnect,
        {'name': name, 'uuid': uuid});
    final FlbtDevice flbtDevice = await completer.future.timeout(
        timeout, onTimeout: () {
      _awaitingConnect.remove(name ?? uuid);
    });
    return flbtDevice;
  }

  Future<dynamic> _flbtMethodHandler(MethodCall methodCall) async {
    switch (methodCall.method) {
      case 'connected':
        String identifier = methodCall.arguments['identifier'];
        if (identifier == null) return null;
        FlbtDevice device = FlbtDevice.fromMap(methodCall.arguments);
        device.connState = FlbtConnectionState.connected;
        _devices[identifier] = device;
        Completer completer = _awaitingConnect[identifier];
        if (completer == null) return null;
        completer.complete(device);
        _awaitingConnect.remove(identifier);
        return null;
      case 'initComplete':
        _awaitingInit.complete(true);
        return null;
      case 'disconnected':
        String identifier = methodCall.arguments['identifier'];
        if (identifier == null) return null;
        _devices[identifier]?.connState = FlbtConnectionState.disconnected;
        return null;
      default:
      // todo - throw not implemented
    }
  }

  void _onBtData(List args) {
    FlbtDevice device = _devices[args[0]];
    if (device == null) print("device ${args[0]} not found!");
    if (device.onData == null) return;
    device.onData(args[1]);
  }
}
