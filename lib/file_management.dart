
import 'dart:async';

import 'package:flutter/services.dart';

class FileManagement {
  static const MethodChannel _channel =
      const MethodChannel('file_management');

  static Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  static Future saveFileInAppDirInGallery(String filePath, String name, String extension, {bool isReturnPathOfIOS = false}) async {
    assert(filePath != null);
    final result =
    await _channel.invokeMethod('saveFileInAppDirInGallery', <String, dynamic>{
      'filePath': filePath,
      'name': name,
      'extension': extension,
      'isReturnPathOfIOS': isReturnPathOfIOS
    });
    return result;  //LinkedHashMap<dynamic, dynamic>
  }

  static Future getAllFileAppDirInGallery() async {
    final result = await _channel.invokeMethod("getAllFileAppDirInGallery");
    return result; //List<dynamic>
  }
}
