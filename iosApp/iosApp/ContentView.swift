import Shared
import SwiftUI
import UIKit

/// The shared Compose UI, drawn edge to edge. Compose keeps its pages clear of the system bars
/// and the keyboard itself, as on Android.
struct ContentView: View {
    var body: some View {
        ComposeView().ignoresSafeArea()
    }
}

private struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
