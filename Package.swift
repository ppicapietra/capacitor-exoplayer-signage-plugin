// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "PpicapietraCapacitorExoplayerSignagePlugin",
    platforms: [.iOS(.v14)],
    products: [
        .library(
            name: "PpicapietraCapacitorExoplayerSignagePlugin",
            targets: ["ExoPlayerSignagePlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "7.0.0")
    ],
    targets: [
        .target(
            name: "ExoPlayerSignagePlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/ExoPlayerSignagePlugin"),
        .testTarget(
            name: "ExoPlayerSignagePluginTests",
            dependencies: ["ExoPlayerSignagePlugin"],
            path: "ios/Tests/ExoPlayerSignagePluginTests")
    ]
)