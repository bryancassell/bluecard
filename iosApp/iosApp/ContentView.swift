import Shared
import SwiftUI
import UIKit

/// The shared Compose UI, drawn edge to edge. Compose keeps its pages clear of the system bars
/// and the keyboard itself, as on Android.
struct ContentView: View {
    /// What the pages remember, which iOS keeps with the scene, so they come back after iOS ends
    /// the app in the background (`SceneSavedState`).
    @SceneStorage("savedState") private var savedState = ""

    var body: some View {
        ComposeView(savedState: $savedState).ignoresSafeArea()
    }
}

private struct ComposeView: UIViewControllerRepresentable {
    @Binding var savedState: String

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(savedState: savedState) { savedState = $0 }
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
