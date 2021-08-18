
import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
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
    final result = await _channel.invokeMethod('saveFileInAppDirInGallery', <String, dynamic>{
      'filePath': filePath,
      'name': name,
      'extension': extension,
      'isReturnPathOfIOS': isReturnPathOfIOS
    });
    return result;  //LinkedHashMap<dynamic, dynamic>
  }

  static Future saveByteArrayToFileInAppDirInGallery(Uint8List imageBytes, String name, String extension, {bool isReturnPathOfIOS = false}) async {
    assert(imageBytes != null);
    final result = await _channel.invokeMethod('saveByteArrayToFileInAppDirInGallery', <String, dynamic>{
      'imageBytes': imageBytes,
      'name': name,
      'extension': extension,
      'isReturnPathOfIOS': isReturnPathOfIOS
    });
    return result; //LinkedHashMap<dynamic, dynamic>
  }

  static Future getAllFileAppDirInGallery() async {
    final result = await _channel.invokeMethod("getAllFileAppDirInGallery");
    return result; //LinkedHashMap<dynamic, dynamic>
  }

  static Future getFileByNameFromAppDirInGallery(String name, String extension) async {
    final result = await _channel.invokeMethod('getFileByNameFromAppDirInGallery', <String, dynamic>{
      'name': name,
      'extension': extension,
    });
    return result; //LinkedHashMap<dynamic, dynamic>
  }

  static Future deleteFiles(List<String> fileUris) async {
    assert(fileUris.isNotEmpty);
    assert(fileUris.every((element) => element.isNotEmpty));
    final result = await _channel.invokeMethod('deleteFiles', <String, dynamic>{
      'fileUris': fileUris,
    });
    return result;
  }

  static Future<void> shareFiles(List<String> fileUris, List<String> mimeTypes) async {
    assert(fileUris.isNotEmpty);
    assert(fileUris.every((element) => element.isNotEmpty));
    final result = await _channel.invokeMethod('shareFiles', <String, dynamic>{
      'fileUris': fileUris,
      'mimeTypes': mimeTypes,
    });
    return result;
  }
}
