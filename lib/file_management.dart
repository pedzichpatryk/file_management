
import 'dart:async';

import 'package:flutter/services.dart';

class FileManagement {
  static const MethodChannel _channel =
      const MethodChannel('file_management');

  static Future<String> get platformVersion async {
    final String version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  static Future saverFileInGallery(String filePath, String name, String extension, {bool isReturnPathOfIOS = false}) async {
    assert(filePath != null);
    final result =
    await _channel.invokeMethod('saveFileInGallery', <String, dynamic>{
      'filePath': filePath,
      'name': name,
      'extension': extension,
      'isReturnPathOfIOS': isReturnPathOfIOS
    });
    return result;  //LinkedHashMap<dynamic, dynamic>
  }

  static Future getAllFile() async {
    final result = await _channel.invokeMethod("getAllFile");
    return result; //List<dynamic>
  }
}
