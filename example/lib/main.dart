import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_upgrade_plugin/flutter_upgrade_plugin.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _platformVersion = 'Unknown';

  @override
  void initState() {
    super.initState();
    // initPlatformState();
  }

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    String platformVersion;
    // Platform messages may fail, so we use a try/catch PlatformException.
    try {
      await FlutterUpgradePlugin.upgrade(downloadParams: {
        "downloadUrl": "https://bigshot.oss-cn-shanghai.aliyuncs.com/app/%E5%94%AF%E5%AF%BB%E7%BD%91%E6%A0%A1.apk",
        "description": '唯寻网校',
        "title": "唯寻网校",
        "appId": "com.visioneschool.flutter_upgrade_plugin_example",
      });
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
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Center(
          child: Column(
            children: [
              Text('Running on: $_platformVersion\n'),
              RaisedButton(
                onPressed: () {
                  initPlatformState();
                },
                child: Text('download'),
              )
            ],
          ),
        ),
      ),
    );
  }
}
