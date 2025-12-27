import Foundation
import Capacitor

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(ExoPlayerSignagePlugin)
public class ExoPlayerSignagePlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "ExoPlayerSignagePlugin"
    public let jsName = "ExoPlayerSignage"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "echo", returnType: CAPPluginReturnPromise)
    ]
    private let implementation = ExoPlayerSignage()

    @objc func echo(_ call: CAPPluginCall) {
        let value = call.getString("value") ?? ""
        call.resolve([
            "value": implementation.echo(value)
        ])
    }
}
