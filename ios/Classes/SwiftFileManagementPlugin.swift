import Flutter
import UIKit
import Photos

public class SwiftFileManagementPlugin: NSObject, FlutterPlugin {
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "file_management", binaryMessenger:registrar.messenger())
        let instance = SwiftFileManagementPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
    }

    var result: FlutterResult?
    
    var assetCollection: PHAssetCollection!
    var albumFound : Bool = false
    var assetCollectionPlaceholder: PHObjectPlaceholder!
    var photosAsset: PHFetchResult<PHAsset>!
    var videoAsset: PHFetchResult<PHAsset>!
    let dateFormatter = DateFormatter()
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        self.result = result
        self.dateFormatter.timeZone = TimeZone.current
        self.dateFormatter.locale = Locale.current
        self.dateFormatter.dateFormat = "yyyy-MM-dd_HHmmss"
        if (call.method == "getPlatformVersion") {
            result("iOS " + UIDevice.current.systemVersion)
        } else if (call.method == "saveFileInAppDirInGallery") {
            guard let arguments = call.arguments as? [String: Any],
                  let fileExtension = arguments["extension"] as? String,
                  let path = arguments["filePath"] as? String else {return}
            if (isImageFile(fileExtension: fileExtension)) {
                createAlbum(fileOperation: FileOperation.saveImage, path: path)
            } else {
                if (UIVideoAtPathIsCompatibleWithSavedPhotosAlbum(path)) {
                    createAlbum(fileOperation: FileOperation.saveVideo, path: path)
                } else {
                    convertAndSaveVideo(path)
                    //TODO convert video
//                    result(FlutterMethodNotImplemented)
                }
            }
        } else if (call.method == "saveByteArrayToFileInAppDirInGallery") {
            result(FlutterMethodNotImplemented)
        } else if (call.method == "getAllFileAppDirInGallery") {
            createAlbum(fileOperation: FileOperation.getAllFileAppDirInGallery, path: nil)
        } else if (call.method == "getFileByNameFromAppDirInGallery") {
            result(FlutterMethodNotImplemented)
        } else if (call.method == "shareFiles") {
            guard let arguments = call.arguments as? [String: Any],
                  let fileUris = arguments["fileUris"] as? [String],
                  let mimeTypes = arguments["mimeTypes"] as? [String] else {return}
            shareFiles(uris: fileUris, mimeTypes: mimeTypes)
            result(nil)
        } else if (call.method == "deleteFiles") {
            guard let arguments = call.arguments as? [String: Any],
                  let fileUris = arguments["fileUris"] as? [String] else {return}
            deleteFiles(uris: fileUris)
        } else {
            result(FlutterMethodNotImplemented)
        }
    }
    
    func convertAndSaveVideo(_ path: String) {
        let outputPath = NSSearchPathForDirectoriesInDomains(.cachesDirectory, .userDomainMask, true)
        let documentsDirectory = outputPath[0]
        
        let name = String(path[path.lastIndex(of: "/")!..<path.lastIndex(of: ".")!].dropFirst()) + ".mov"
        
        let filePath = URL(fileURLWithPath: documentsDirectory).appendingPathComponent(name).absoluteString
        let outputURL = URL(string: filePath)
        convertVideoToLowQuailty(inputURL: URL(fileURLWithPath: path), outputURL: outputURL, handler: { exportSession in
            if exportSession?.status == .completed {
                // Video conversation completed
                let convertFilePath = outputURL!.absoluteString
                self.createAlbum(fileOperation: FileOperation.saveVideo, path: convertFilePath)
            } else {
                print(exportSession?.error)
            }
        })
    }
    
    func convertVideoToLowQuailty(inputURL: URL?, outputURL: URL?, handler: @escaping (AVAssetExportSession?) -> Void) {
        if let anURL = outputURL {
            try? FileManager.default.removeItem(at: anURL)
        }
        var asset: AVURLAsset? = nil
        if let anURL = inputURL {
            asset = AVURLAsset(url: anURL, options: nil)
        }
        var exportSession: AVAssetExportSession? = nil
        if let anAsset = asset {
            exportSession = AVAssetExportSession(asset: anAsset, presetName: AVAssetExportPresetPassthrough)
        }
        exportSession?.outputURL = NSURL.fileURL(withPath: outputURL!.path)
        exportSession?.outputFileType = .mov
        exportSession?.shouldOptimizeForNetworkUse = true
        exportSession?.exportAsynchronously(completionHandler: {
            handler(exportSession)
        })
    }
    
    func saveVideo(_ path: String) {
        PHPhotoLibrary.shared().performChanges({
            let assetRequest = PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL:URL(string: path)!)
            let assetPlaceholder = assetRequest?.placeholderForCreatedAsset
            self.videoAsset = PHAsset.fetchAssets(in: self.assetCollection, options: nil)
            let albumChangeReques = PHAssetCollectionChangeRequest(for:self.assetCollection, assets: self.videoAsset)
            albumChangeReques?.addAssets([assetPlaceholder] as NSFastEnumeration)
        }, completionHandler: { success, error in
            if (success) {
                self.saveResult()
            } else {
                print(error?.localizedDescription)
                self.saveResult(error: error?.localizedDescription)
            }
        })
//        if !isReturnImagePath {
//            UISaveVideoAtPathToSavedPhotosAlbum(path, self,#selector(didFinishSavingVideo(videoPath:error:contextInfo:)), nil)
//            return
//        }
//        var videoIds: [String] = []
//
//        PHPhotoLibrary.shared().performChanges( {
//            let req = PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL:URL(string: path)!)
//            if let videoId = req?.placeholderForCreatedAsset?.localIdentifier {
//                videoIds.append(videoId)
//            }
//        }, completionHandler: { [unowned self] (success, error) in
//            DispatchQueue.main.async {
//                if (success && videoIds.count > 0) {
//                    let assetResult = PHAsset.fetchAssets(withLocalIdentifiers:videoIds, options: nil)
//                    if (assetResult.count > 0) {
//                        let videoAsset = assetResult[0]
//                        PHImageManager().requestAVAsset(forVideo: videoAsset, options:nil) { (avurlAsset, audioMix, info) in
//                            if let urlStr = (avurlAsset as?AVURLAsset)?.url.absoluteString {
//                                self.saveResult(uri: urlStr)
//                            }
//                        }
//                    }
//                } else {
//                    self.saveResult(error: "Video file is not save.")
//                }
//            }
//        })
    }
    
    func saveImage(_ path: String) {
        PHPhotoLibrary.shared().performChanges({
            let assetRequest = PHAssetChangeRequest.creationRequestForAssetFromImage(atFileURL:URL(string: path)!)
            assetRequest?.creationDate = self.dateFormatter.date(from: String(path[path.lastIndex(of: "/")!..<path.lastIndex(of: ".")!].dropFirst()))
            let assetPlaceholder = assetRequest?.placeholderForCreatedAsset
            self.photosAsset = PHAsset.fetchAssets(in: self.assetCollection, options: nil)
            let albumChangeReques = PHAssetCollectionChangeRequest(for:                         self.assetCollection, assets: self.photosAsset)
            albumChangeReques?.addAssets([assetPlaceholder] as NSFastEnumeration)
        }, completionHandler: { success, error in
            if (success) {
                self.saveResult()
            } else {
                print(error)
                self.saveResult(error: "Image file is not save.")
            }
        })
    }
    
    func getAllFileAppDirInGallery() {
        let assets : PHFetchResult = PHAsset.fetchAssets(in: assetCollection, options: nil)
        var fileId = [String:String?]()
        assets.enumerateObjects{(object: AnyObject!, index: Int, stop:UnsafeMutablePointer<ObjCBool>) in
            if object is PHAsset {
                let asset = object as! PHAsset
                let options = PHContentEditingInputRequestOptions()
                options.canHandleAdjustmentData = { (adjustmeta)-> Bool in true }
                asset.requestContentEditingInput(with: options) { (contentEditingInput, info) in
                    self.dateFormatter.string(from: contentEditingInput?.creationDate ?? Date())
                    if let urlStr = contentEditingInput?.fullSizeImageURL?.absoluteString {
                        fileId.updateValue(urlStr, forKey: self.dateFormatter.string(from: contentEditingInput?.creationDate ?? Date()))
                        if (index+1 == assets.count) {
                            self.result?(fileId)
                        }
                    }
                }
            }
        }
        if (assets.count == 0) {
            self.result?(fileId)
        }
    }
    
    func createAlbum(fileOperation: FileOperation, path: String?) {
        let fetchOptions = PHFetchOptions()
        fetchOptions.predicate = NSPredicate(format: "title = %@", displayName ?? "file_management")
        let collection : PHFetchResult = PHAssetCollection.fetchAssetCollections(with:.album, subtype: .any, options: fetchOptions)
        //Check return value - If found, then get the first album out
        if let _: AnyObject = collection.firstObject {
            self.albumFound = true
            assetCollection = collection.firstObject as! PHAssetCollection
            callFileOperation(fileOperation: fileOperation, path: path)
        } else {
            //If not found - Then create a new album
            PHPhotoLibrary.shared().performChanges({
                let createAlbumRequest : PHAssetCollectionChangeRequest = PHAssetCollectionChangeRequest.creationRequestForAssetCollection(withTitle: self.displayName ?? "file_management")
                self.assetCollectionPlaceholder = createAlbumRequest.placeholderForCreatedAssetCollection
                }, completionHandler: { success, error in
                    self.albumFound = success
                    if (success) {
                        let collectionFetchResult = PHAssetCollection.fetchAssetCollections(withLocalIdentifiers: [self.assetCollectionPlaceholder.localIdentifier], options: nil)
                        print(collectionFetchResult)
                        self.assetCollection = collectionFetchResult.firstObject as! PHAssetCollection
                        self.callFileOperation(fileOperation: fileOperation, path: path)
                    }
            })
        }
    }
    
    func callFileOperation(fileOperation: FileOperation, path: String?) {
        switch fileOperation {
        case FileOperation.saveImage:
            saveImage(path!)
        case FileOperation.saveVideo:
            saveVideo(path!)
        case FileOperation.getAllFileAppDirInGallery:
            getAllFileAppDirInGallery()
        default:
            self.saveResult(error: "File operation is incorrect")
        }
    }

    var displayName: String? {
            return Bundle.main.infoDictionary!["CFBundleName"] as? String
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
    
    func isImageFile(fileExtension: String) -> Bool {
        return fileExtension.hasSuffix(".jpg")
            || fileExtension.hasSuffix(".png")
            || fileExtension.hasSuffix(".JPEG")
            || fileExtension.hasSuffix(".JPG")
            || fileExtension.hasSuffix(".PNG")
            || fileExtension.hasSuffix(".gif")
            || fileExtension.hasSuffix(".GIF")
    }
    
    func shareFiles(uris: [String], mimeTypes: [String]) {
        var fileUrls = NSMutableArray()
        for item in uris {
            var fileurl = NSURL.init(fileURLWithPath: item)
            fileUrls.add(fileurl)
        }
        
        let activityViewController = UIActivityViewController(activityItems: fileUrls as! [Any], applicationActivities: [])
        var rootController = UIApplication.shared.keyWindow?.rootViewController
        activityViewController.popoverPresentationController?.sourceView = rootController?.view
        rootController?.present(activityViewController, animated: true, completion: nil)
    }
    
    func deleteFiles(uris: [String]) {
        var fileLocalIdentifier: [String] = []
        
        let fetchOptions = PHFetchOptions()
        fetchOptions.predicate = NSPredicate(format: "title = %@", displayName ?? "file_management")
        let collection : PHFetchResult = PHAssetCollection.fetchAssetCollections(with:.album, subtype: .any, options: fetchOptions)
        assetCollection = collection.firstObject as! PHAssetCollection
        
        let assets : PHFetchResult = PHAsset.fetchAssets(in: assetCollection, options: nil)
        assets.enumerateObjects{(object: AnyObject!, index: Int, stop:UnsafeMutablePointer<ObjCBool>) in
            if object is PHAsset {
                let asset = object as! PHAsset
                let options = PHContentEditingInputRequestOptions()
                options.canHandleAdjustmentData = { (adjustmeta)-> Bool in true }
                asset.requestContentEditingInput(with: options) { (contentEditingInput, info) in
                    self.dateFormatter.string(from: contentEditingInput?.creationDate ?? Date())
                    if let urlStr = contentEditingInput?.fullSizeImageURL?.absoluteString {
                        if uris.contains(urlStr) {
                            fileLocalIdentifier.append(asset.localIdentifier)
                        }
                        if (index+1 == assets.count) {
                            PHPhotoLibrary.shared().performChanges({
                                var allPhotos = PHAsset.fetchAssets(withLocalIdentifiers: fileLocalIdentifier, options: nil)
                                PHAssetChangeRequest.deleteAssets(allPhotos)
                            }, completionHandler: {success, error in
                                if success {
                                    self.saveResult()
                                } else {
                                    self.saveResult(error: (error as? NSError)?.description, uri: nil)
                                }
                            })
                        }
                    }
                }
            }
        }
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

enum FileOperation {
    case saveImage
    case saveVideo
    case getAllFileAppDirInGallery
    case west
}
