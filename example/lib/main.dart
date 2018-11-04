import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flbt_classic_spp/flbt_classic_spp.dart';

void main() => runApp(new MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => new _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _platformVersion = 'Unknown';
  FlbtClassicSpp flbt = FlbtClassicSpp.instance;

  @override
  void initState() {
    super.initState();
    initPlatformState();
    flbt.init().then((_) {
      flbt.connectByName("ISKRA_ISD").then((device) {
        print(device);
        device.onData = (data) {
          print("data: $data");
        };
        flbt.writeBytes(device, Uint8List.fromList([0x02, 0x01, 0x04, 0x40, 0x84, 0x03]));
      });
    });
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    String platformVersion;
    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      platformVersion = await FlbtClassicSpp.platformVersion;
    } on PlatformException {
      platformVersion = 'Failed to get platform version.';
    }

    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.
    if (!mounted) return;

    setState(() {
      _platformVersion = platformVersion;
    });
  }

  @override
  Widget build(BuildContext context) {
    return new MaterialApp(
      home: new Scaffold(
        appBar: new AppBar(
          title: const Text('Plugin example app'),
        ),
        body: new Column(children: [
          new Text('Running on: $_platformVersion\n'),
          new Text('Initialized:}')
        ]),
      ),
    );
  }

}
