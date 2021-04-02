import Flutter
import UIKit
import Photos

public class SwiftFileManagementPlugin: NSObject, FlutterPlugin {
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "file_management", binaryMessenger:registrar.messenger())
        let instance = SwiftFileManagementPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
    }

    var result: FlutterResult?;
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        self.result = result
        if (call.method == "getPlatformVersion") {
            result("iOS " + UIDevice.current.systemVersion)
        } else if (call.method == "saveFileInAppDirInGallery") {
            guard let arguments = call.arguments as? [String: Any],
                  let path = arguments["filePath"] as? String,
                  let isReturnFilePath = arguments["isReturnPathOfIOS"] as? Bool else { return }
            if (isImageFile(filename: path)) {
                saveImage(path, isReturnImagePath: isReturnFilePath)
            } else {
                if (UIVideoAtPathIsCompatibleWithSavedPhotosAlbum(path)) {
                    saveVideo(path, isReturnImagePath: isReturnFilePath)
                }
            }
        } else if (call.method == "saveByteArrayToFileInAppDirInGallery") {
            result(FlutterMethodNotImplemented)
        } else if (call.method == "getAllFileAppDirInGallery") {
            result(FlutterMethodNotImplemented)
        } else if (call.method == "getFileByNameFromAppDirInGallery") {
            result(FlutterMethodNotImplemented)
        } else {
            result(FlutterMethodNotImplemented)
        }
    }
    
    func saveVideo(_ path: String, isReturnImagePath: Bool) {
        if !isReturnImagePath {
            UISaveVideoAtPathToSavedPhotosAlbum(path, self,#selector(didFinishSavingVideo(videoPath:error:contextInfo:)), nil)
            return
        }
        var videoIds: [String] = []
        
        PHPhotoLibrary.shared().performChanges( {
            let req = PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL:URL(string: path)!)
            if let videoId = req?.placeholderForCreatedAsset?.localIdentifier {
                videoIds.append(videoId)
            }
        }, completionHandler: { [unowned self] (success, error) in
            DispatchQueue.main.async {
                if (success && videoIds.count > 0) {
                    let assetResult = PHAsset.fetchAssets(withLocalIdentifiers:videoIds, options: nil)
                    if (assetResult.count > 0) {
                        let videoAsset = assetResult[0]
                        PHImageManager().requestAVAsset(forVideo: videoAsset, options:nil) { (avurlAsset, audioMix, info) in
                            if let urlStr = (avurlAsset as?AVURLAsset)?.url.absoluteString {
                                self.saveResult(uri: urlStr)
                            }
                        }
                    }
                } else {
                    self.saveResult(error: "Video file is not save.")
                }
            }
        })
    }
    
    func saveImage(_ path: String, isReturnImagePath: Bool) {
        if !isReturnImagePath {
            if let image = UIImage(contentsOfFile: path) {
                            UIImageWriteToSavedPhotosAlbum(image, self, #selector(didFinishSavingImage(image:error:contextInfo:)), nil)
            }
            return
        }
        var imageIds: [String] = []
        PHPhotoLibrary.shared().performChanges( {
            let req = PHAssetChangeRequest.creationRequestForAssetFromImage(atFileURL: URL(string: path)!)
            if let imageId = req?.placeholderForCreatedAsset?.localIdentifier {
                imageIds.append(imageId)
            }
        }, completionHandler: { [unowned self] (success, error) in
            DispatchQueue.main.async {
                if (success && imageIds.count > 0) {
                    let assetResult = PHAsset.fetchAssets(withLocalIdentifiers: imageIds, options: nil)
                    if (assetResult.count > 0) {
                        let imageAsset = assetResult[0]
                        let options = PHContentEditingInputRequestOptions()
                        options.canHandleAdjustmentData = { (adjustmeta)
                                -> Bool in true }
                        imageAsset.requestContentEditingInput(with: options) { [unowned self] (contentEditingInput, info) in
                            if let urlStr = contentEditingInput?.fullSizeImageURL?.absoluteString {
                                self.saveResult(uri: urlStr)
                            }
                        }
                    }
                } else {
                    self.saveResult(error: "Image file is not save.")
                }
            }
        })
    }

    @objc func didFinishSavingImage(image: UIImage, error: NSError?, contextInfo: UnsafeMutableRawPointer?) {
        saveResult(error: error?.description)
    }
    
    @objc func didFinishSavingVideo(videoPath: String, error: NSError?, contextInfo:    UnsafeMutableRawPointer?) {
        saveResult(error: error?.description)
    }
    
    func saveResult(error: String? = nil, uri: String? = nil) {
        var saveResult = FileResult()
        saveResult.errorMessage = error?.description
        saveResult.uri = uri
        result?(saveResult.toDic())
    }
    
    func isImageFile(filename: String) -> Bool {
        return filename.hasSuffix(".jpg")
            || filename.hasSuffix(".png")
            || filename.hasSuffix(".JPEG")
            || filename.hasSuffix(".JPG")
            || filename.hasSuffix(".PNG")
            || filename.hasSuffix(".gif")
            || filename.hasSuffix(".GIF")
    }
}

public struct FileResult: Encodable {
    var uri: String?
    var errorMessage: String?
    func toDic() -> [String:Any]? {
        let encoder = JSONEncoder()
        guard let data = try? encoder.encode(self) else { return nil }
        if (!JSONSerialization.isValidJSONObject(data)) {
            return try? JSONSerialization.jsonObject(with: data, options: .mutableContainers) as? [String:Any]
        }
        return nil
    }
}
