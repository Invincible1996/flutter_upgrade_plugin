import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';

class FlutterUpgradePlugin {
  static const MethodChannel _channel = const MethodChannel('flutter_upgrade_plugin');

  static Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  static Future upgrade({downloadParams}) async {
    if (Platform.isAndroid) {
      Map<Permission, PermissionStatus> statuses = await [
        Permission.storage,
      ].request();
      print(statuses[Permission.storage]);
      downloadAndInstallApk(downloadParams);
    } else if (Platform.isIOS) {
      goToAppStore();
    }
  }

  static downloadAndInstallApk(downloadParams) async {
    await _channel.invokeMethod('downloadAndInstallApk', downloadParams);
  }

  static goToAppStore() {}
}
