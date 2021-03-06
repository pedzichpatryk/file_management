import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:file_management/file_management.dart';

void main() {
  const MethodChannel channel = MethodChannel('file_management');

  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    channel.setMockMethodCallHandler((MethodCall methodCall) async {
      return '42';
    });
  });

  tearDown(() {
    channel.setMockMethodCallHandler(null);
  });

  test('getPlatformVersion', () async {
    expect(await FileManagement.platformVersion, '42');
  });
}
