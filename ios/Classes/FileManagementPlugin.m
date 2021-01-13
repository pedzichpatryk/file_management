#import "FileManagementPlugin.h"
#if __has_include(<file_management/file_management-Swift.h>)
#import <file_management/file_management-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "file_management-Swift.h"
#endif

@implementation FileManagementPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftFileManagementPlugin registerWithRegistrar:registrar];
}
@end
