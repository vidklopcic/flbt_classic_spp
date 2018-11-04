import 'dart:async';

import 'package:flutter/services.dart';

enum FlbtConnectionState { disconnected, connected }

class FlbtDevice {
  String uuid;
}

class FlbtClassicSpp {
  static const MethodChannel _channel = const MethodChannel('flbt_classic_spp');
  static const String _methodConnect = "connect";

  // instance
  FlbtClassicSpp._();

  FlbtClassicSpp _instance = FlbtClassicSpp._();

  get instance => _instance;

  // state
  Map<String, FlbtDevice> _devices;

  static Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  Future<FlbtDevice> connectByName(String name,
      {Duration timeout = const Duration(seconds: 5)}) {
    return _connect(name, null, timeout);
  }

  Future<FlbtDevice> connectByUuid(String uuid,
      {Duration timeout = const Duration(seconds: 5)}) {
    return _connect(null, uuid, timeout);
  }

  Future<FlbtDevice> _connect(
      String name, String uuid, Duration timeout) async {
    return await _channel.invokeMethod(_methodConnect,
        {'name': name, 'uuid': uuid, 'timeout': timeout.inMilliseconds});
  }
}
