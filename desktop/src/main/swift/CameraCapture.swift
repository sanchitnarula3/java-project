import AVFoundation
import CoreImage
import Foundation

final class FrameWriter: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    private let outputURL: URL
    private let context = CIContext()
    private let colorSpace = CGColorSpaceCreateDeviceRGB()
    private let lock = NSLock()
    private var lastWrite = Date.distantPast

    init(outputURL: URL) {
        self.outputURL = outputURL
    }

    func captureOutput(_ output: AVCaptureOutput,
                       didOutput sampleBuffer: CMSampleBuffer,
                       from connection: AVCaptureConnection) {
        lock.lock()
        defer { lock.unlock() }
        guard Date().timeIntervalSince(lastWrite) >= 0.08,
              let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }

        let image = CIImage(cvPixelBuffer: pixelBuffer)
        guard let jpeg = context.jpegRepresentation(of: image, colorSpace: colorSpace, options: [:]) else { return }
        do {
            try jpeg.write(to: outputURL, options: .atomic)
            lastWrite = Date()
        } catch {
            fputs("Could not write camera frame: \(error)\n", stderr)
        }
    }
}

guard CommandLine.arguments.count == 2 else {
    fputs("Usage: camera-capture <frame-file>\n", stderr)
    exit(2)
}

let outputURL = URL(fileURLWithPath: CommandLine.arguments[1])
let permission = DispatchSemaphore(value: 0)
var cameraAllowed = false
AVCaptureDevice.requestAccess(for: .video) { granted in
    cameraAllowed = granted
    permission.signal()
}
permission.wait()
guard cameraAllowed else {
    fputs("Camera access was denied by macOS. Enable Camera access for OMR Scanner in System Settings.\n", stderr)
    exit(3)
}

let cameras = AVCaptureDevice.DiscoverySession(
    deviceTypes: [.builtInWideAngleCamera, .external, .continuityCamera],
    mediaType: .video,
    position: .unspecified
).devices
fputs("Video devices reported by macOS: \(cameras.map { $0.localizedName }.joined(separator: ", "))\n", stderr)
guard let camera = cameras.first(where: { $0.deviceType == .builtInWideAngleCamera }) ?? cameras.first else {
    fputs("No camera device is currently available to AVFoundation.\n", stderr)
    exit(4)
}
fputs("Using camera: \(camera.localizedName) [\(camera.deviceType.rawValue)]\n", stderr)

let session = AVCaptureSession()
session.beginConfiguration()
session.sessionPreset = .vga640x480
do {
    let input = try AVCaptureDeviceInput(device: camera)
    guard session.canAddInput(input) else { throw NSError(domain: "CameraCapture", code: 1) }
    session.addInput(input)
    let output = AVCaptureVideoDataOutput()
    output.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
    output.alwaysDiscardsLateVideoFrames = true
    let writer = FrameWriter(outputURL: outputURL)
    output.setSampleBufferDelegate(writer, queue: DispatchQueue(label: "omr.camera.frames"))
    guard session.canAddOutput(output) else { throw NSError(domain: "CameraCapture", code: 2) }
    session.addOutput(output)
    session.commitConfiguration()
    session.startRunning()
    guard session.isRunning else {
        fputs("AVFoundation returned from startRunning without starting the capture session.\n", stderr)
        exit(6)
    }
    fputs("Capture session started; waiting for video frames.\n", stderr)
    RunLoop.current.run()
} catch {
    session.commitConfiguration()
    fputs("Could not start AVFoundation camera capture: \(error)\n", stderr)
    exit(5)
}
